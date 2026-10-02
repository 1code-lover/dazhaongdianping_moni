# 服务器运维手册（hmdp @ 阿里云 ECS）

> 部署环境：公网 IP `8.156.94.216`，Ubuntu 22.04，4C14G，磁盘 126G（多项目共用）
> 对外入口：**http://8.156.94.216/**（80 端口 → 前端静态 + API 反代 → 8081）

## 1. 服务清单与自启状态

| 组件 | 托管方式 | 开机自启 | 说明 |
|------|----------|----------|------|
| MySQL 8.0 | systemd `mysql` | ✅ enabled | 数据目录 `/var/lib/mysql/data8`（配置 `/etc/mysql/mysql.conf.d/hmdp-datadir.cnf`） |
| Redis 6.2 | systemd `redis-hmdp` | ✅ enabled | 源码安装，配置 `/usr/local/redis/redis-6.2.14/redis.conf` |
| ES/Kafka/ZK | Docker | ✅ `restart=always` | `docker compose up -d zookeeper kafka elasticsearch`（勿全量 up，避免与原生 MySQL/Redis 撞端口） |
| 后端 :8081 | systemd `hmdp-backend` | ✅ enabled | 读 `/home/dazhaongdianping_moni/.env`，新 JDK 8u504，崩溃 5s 自动拉起 |
| 前端 :80 | systemd `hmdp-frontend` nginx | ✅ enabled | nginx 托管 dist + 反代 `/api`、`/imgs`、`/blogs`（2026-10-02 由 vite preview 切换，更稳） |
| 前端 dev :3000 | 手动 | ❌ | `npm run dev -- --host 0.0.0.0`，仅开发调试用 |

> **HTTPS 待办**：浏览器定位 API 仅安全上下文可用，对外 HTTP 访问时定位自动降级为杭州市中心。如需真实 GPS：申请域名（sslip.io/DuckDNS 等免费方案经实测 LE 多视角验证不稳，建议买个 ¥10/年 便宜域名）→ certbot 申请证书 → nginx 加 443。nginx 已就绪，加证书即可。

## 2. 常用操作

```bash
# 服务管理
systemctl status|restart hmdp-backend redis-hmdp
nginx -t && systemctl reload nginx   # 前端
docker ps   # ES/Kafka/ZK

# 改配置（.env 含 DB/Redis/邮件配置）后重启生效
systemctl restart hmdp-backend

# 前端发版：构建 + 重启
cd heima_qianduan/nginx-1.18.0/html/hmdp && npm run build
# nginx 直接读 dist 目录，构建完成即生效（无需重启）

# 健康巡检（每小时整点自动执行，结果在 /var/log/hmdp-health.log）
bash scripts/health-check.sh

# 日志
journalctl -u hmdp-backend -f          # 后端 stdout
tail -f /var/log/hmdp-health.log       # 巡检记录
# app.log 已配 logrotate（每日轮转保留7天）
```

## 3. 监控与告警

- **应用层**：`/actuator/health`、`/actuator/prometheus`（Micrometer，含秒杀/缓存指标）
- **巡检**：`scripts/health-check.sh`（cron 每小时）记录 后端/前端/Redis/MySQL/ES/Kafka/磁盘/负载 到 `/var/log/hmdp-health.log`
- **飞书告警**：任一组件异常或磁盘 ≥95% 时推送飞书群机器人。配置：飞书群 → 设置 → 群机器人 → 添加「自定义机器人」→ 把 webhook 地址写入 `.env` 的 `FEISHU_WEBHOOK=`（参照 `.env.example`），无需重启
- **数据库备份**：`scripts/backup-db.sh`（cron 每日 03:30）mysqldump 压缩备份到 `backups/`，保留 7 天（已 gitignore）
- **进程自愈**：全部 systemd/docker `restart=always`，实测 kill -9 后端 17s 自愈
- **待办**：Prometheus 未被采集（如需图表需装 Prometheus+Grafana）

## 3.5 CI/CD 自动部署

- **流水线**：`.github/workflows/deploy.yml`，push 到 master 自动触发
- **流程**：单元测试（**硬门禁**，62 个纯单测 JDK8 全绿，integration/manual 分层显式排除）→ 后端打包 → 前端构建 → rsync 产物到服务器 → `systemctl restart hmdp-backend` → 经 nginx 80 端口健康验证
- **凭据**：GitHub Secret `DEPLOY_SSH_KEY`（专用 ed25519 私钥，公钥在服务器的 `/root/.ssh/authorized_keys`）
- **注意**：8081 仅内网开放，外部验证一律走 `http://8.156.94.216/api/...`

## 4. 容量与风险

| 项目 | 现状 | 阈值建议 |
|------|------|----------|
| 磁盘 | 91%（多项目共用，/opt 64G 非本项目） | >95% 告警；journal 已限 500M |
| 内存 | 14G 总量，常态使用 ~8G | 关注 ES 512m heap |
| 连接池 | Lettuce 50 / Hikari 20 | 压测验证足够 |
| 已知环境怪象 | MySQL TCP 并发 COUNT 慢（详见压测报告第6节） | 已应用层规避 |

## 5. 压测速查

```bash
python3 scripts/loadtest_seckill.py --tokens-file scripts/tokens.txt --voucher 10 --workers 32 --count 1800
```
压测账号：`load_user_*` / `load2_user_*`（tb_user）；token 文件 `scripts/tokens.txt`（已 gitignore）。
当前券状态：券12（探鱼30元）已精确售罄 1000/1000；券10/11/13/14/15 可演示。
