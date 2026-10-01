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
| 前端 :80 | systemd `hmdp-frontend` | ✅ enabled | vite preview 托管 dist，反代 `/api`、`/imgs`、`/blogs` |
| 前端 dev :3000 | 手动 | ❌ | `npm run dev -- --host 0.0.0.0`，仅开发调试用 |

## 2. 常用操作

```bash
# 服务管理
systemctl status|restart hmdp-backend hmdp-frontend redis-hmdp
docker ps   # ES/Kafka/ZK

# 改配置（.env 含 DB/Redis/邮件配置）后重启生效
systemctl restart hmdp-backend

# 前端发版：构建 + 重启
cd heima_qianduan/nginx-1.18.0/html/hmdp && npm run build
systemctl restart hmdp-frontend

# 健康巡检（每小时整点自动执行，结果在 /var/log/hmdp-health.log）
bash scripts/health-check.sh

# 日志
journalctl -u hmdp-backend -f          # 后端 stdout
tail -f /var/log/hmdp-health.log       # 巡检记录
# app.log 已配 logrotate（每日轮转保留7天）
```

## 3. 监控现状

- **应用层**：`/actuator/health`、`/actuator/prometheus`（Micrometer，含秒杀/缓存指标）
- **巡检**：`scripts/health-check.sh`（cron 每小时）记录 后端/前端/Redis/MySQL/ES/Kafka/磁盘/负载 到 `/var/log/hmdp-health.log`
- **进程自愈**：全部 systemd/docker `restart=always`，实测 kill -9 后端 17s 自愈
- **待办**：无告警通知渠道（可加钉钉/邮件 webhook）；Prometheus 未被采集（如需图表需装 Prometheus+Grafana）

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
