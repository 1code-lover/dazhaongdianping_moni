# 服务器迁移指南（容器化交付）

> 目标：在**任意一台安装了 Docker 的服务器**上，5 分钟内完整重建 hmdp 全套服务。
> 本方案与宿主部署（systemd 版，见 `server-ops-runbook.md）互为备选：新机器推荐容器版，老机器维持现状不动。

## 1. 新服务器要求

- Docker 20.10+ 与 Docker Compose v2（`curl -fsSL https://get.docker.com | sh`）
- 2C4G 以上（推荐 4C8G），磁盘 20G+
- 开放端口：80（前端，可用 `HMDP_HTTP_PORT` 改）+ 22

## 2. 一键部署（5 分钟）

```bash
# 1. 拉代码
git clone https://github.com/1code-lover/dazhaongdianping_moni.git
cd dazhaongdianping_moni

# 2. 配置环境变量（密码全部自动生成或手填）
cp .env.example .env
# 编辑 .env：DB_PASSWORD / DB_ROOT_PASSWORD / REDIS_PASSWORD 必填；
# 邮箱验证码（MAIL_*）与飞书告警（FEISHU_WEBHOOK）按需启用

# 3. ES 需要的内核参数（重启后失效，建议同时写入 /etc/sysctl.d/90-es.conf）
sudo sysctl -w vm.max_map_count=262144

# 4. 构建并启动全套服务（首次构建约 5-10 分钟，之后秒级）
docker compose --env-file .env -f deploy/docker-compose.yml -p hmdp up -d --build

# 5. 验证
curl http://服务器IP/                      # 前端
curl http://服务器IP/api/shop-type/list    # 业务API
```

MySQL 首次启动会**自动执行 `deploy/initdb/00-init.sh`**：按序导入全部库脚本（结构+种子+演示数据）并创建 `hmdp` 账号，无需手工导库。

## 3. 数据迁移（老服务器 → 新服务器）

```bash
# 老服务器：导出（含全部业务数据）
mysqldump -u hmdp -p --single-transaction hmdp | gzip > hmdp_backup.sql.gz

# 新服务器：容器内导入（库已存在，走正常导入路径）
gunzip < hmdp_backup.sql.gz | docker exec -i hmdp-mysql-c mysql -uroot -p"$DB_ROOT_PASSWORD" hmdp

# ES 商户索引重建
curl -X POST http://服务器IP/api/shop/sync
```

> Redis 缓存无需迁移（启动预热器自动加载库存/GEO）；用户登录态会失效，用户重新登录即可。

## 4. 日常运维（容器版）

```bash
docker compose --env-file .env -f deploy/docker-compose.yml -p hmdp ps       # 状态
docker compose ... logs -f backend                                           # 日志
docker compose ... up -d --build backend                                     # 只更新后端
docker compose ... restart backend                                           # 重启
```

- 数据卷：`hmdp_mysql-data` / `hmdp_redis-data` / `hmdp_es-data` / `hmdp_upload-data`
- 备份：挂载卷或 mysqldump（同宿主版脚本 `scripts/backup-db.sh` 思路）
- 更新代码：`git pull && docker compose ... up -d --build`（CI 可接同一命令）

## 5. 与宿主部署的对照

| 维度 | 容器版（本文件） | 宿主版（runbook） |
|------|------------------|-------------------|
| 适用 | 新机器/快速交付/多环境一致 | 已运行的阿里云机器 |
| MySQL/Redis | 容器内自动初始化 | 系统级安装 |
| 应用进程 | 容器（restart=always） | systemd |
| 端口 | 仅暴露前端端口 | 80 + 内网 8081 |
| 性能 | 容器网络多一跳 | 略优（本机回环） |
