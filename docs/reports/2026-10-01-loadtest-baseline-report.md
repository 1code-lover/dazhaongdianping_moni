# 压测基线报告（2026-10-01）

- 环境：阿里云 ECS 4C14G；MySQL 8.0 / Redis 6.2（原生）/ ES 7.6.2 + Kafka（Docker）
- 后端：单实例 jar（新 JDK 8u504），连接池 Lettuce max-active=50、Hikari max=20（优化后）
- 工具：`scripts/loadtest_seckill.py`（Python requests，多 token 轮询）
- 压测用户：600 个（`load_user_*`），token 直接铸造于 Redis `login:token:*`

## 0. 修复后复测对比（同日）

| 接口 | 基线 RPS | 复测 RPS | 基线 avg | 复测 avg | 变化 |
|------|----------|----------|----------|----------|------|
| **/blog/hot** | 82 | **231** | 219.5ms | **85ms** | **2.8x** |
| /shop/1 | 559 | 672 | 23.7ms | 22.6ms | 持平偏好 |
| /shop/of/type | 406 | 542 | 43.9ms | 27.8ms | 1.3x |
| /shop/search | 231 | 377 | 81.5ms | 47.6ms | 1.6x |
| /shop-type/list | 742 | 740 | 18.0ms | 18.6ms | 持平 |
| /voucher/list/15 | 654 | 692 | 20.8ms | 19.3ms | 持平 |

> 注：基线数据受排查过程中发现的"孤儿压测进程"污染（见第 3 节），复测在清理干净后进行，绝对值更有参考价值。

## 1. 秒杀链路（Redis Lua → Stream → Kafka → MySQL）

| 场景 | 请求数 | 并发 | 耗时 | RPS | 成功 | 业务拒绝 | HTTP错误 |
|------|--------|------|------|-----|------|----------|----------|
| 抢券（券12/库存1000，600人×3次） | 1800 | 32 | 4.2s | 426 | **600（恰好一人一单）** | 1200（重复） | 0 |
| 抢券（券10/库存500，600人×5次） | 3000 | 64 | 5.9s | 510 | **500（=库存）** | 2500 | 0 |

**一致性校验 ✅**：券12 订单 600 条/600 个不同用户/全部 status=1；DB 库存 400 = Redis 库存 400；无超卖、无重复下单。

结论：秒杀链路 correctness 设计可靠（Lua 原子扣减 + 唯一索引 + 一人一单），入口吞吐 ~500 RPS（4C 机器 + 压测脚本无 keep-alive）。

## 2. P0 修复内容（已实施）

1. **Feed N+1**：`BlogServiceImpl` 新增 `fillBlogDetails`（用户 IN 批量查 + Redis Pipeline 批量查点赞 + 内存填充），`queryHotBlog` / `queryBlogOfFollow` 的 2N+1 次往返降为 3 次。
2. **消除每请求 COUNT**：`queryHotBlog` 的 `Page.setSearchCount(false)`——前端只渲染列表，总数统计无消费者。
3. **连接池调优**：Lettuce max-active 10→50、Hikari 显式 max=20/min-idle=10（均可 env 覆盖）。

## 2.5 写接口压测（20~25并发）

| 写接口 | 样本 | RPS | 结果 |
|--------|------|-----|------|
| 点赞 toggle（同博客1000次） | 1000 | 215 | 全部成功；**一致性 ✅ DB liked=723 与 Redis zcard 精确一致** |
| 关注用户 | 100 | 408 | 全部成功；关注数 +100 正确 |
| 发博客 | 30 | 177 | 全部成功入库（含粉丝 Feed 推送） |
| 套餐下单（库存200抢100） | 100 | 107 | 全部成功；库存精确扣减，取消后 100% 回滚（stock/sales 复原） |

结论：写链路无超卖、无数据漂移，点赞双写（DB+Redis）在并发 toggle 下保持一致。**写接口无需修复项。**

## 3. 排查实录（重要经验）

复测中 blog/hot 改善不及预期，深挖发现三层问题：

1. **测量污染（最大教训）**：排查用的后台并发脚本未设退出条件，成为孤儿进程持续打满 CPU（load 35），基线数据与多轮"干净复测"全部被污染。**压测脚本必须可退出，测完检查 `ps` 残留。**
2. **MyBatis-Plus 每次分页都执行 COUNT**：performance_schema 显示 `SELECT COUNT(*) FROM tb_blog` 平均 102ms/次（500 次执行），而同连接上其他查询仅 1.3ms。裸 JDBC 可复现（20 并发 89ms/次），但走 socket 仅 1ms、单连接 TCP 仅 0.6ms。
3. **本机 MySQL over TCP 并发异常**：表现为并发查询经 TCP（127.0.0.1）比 Unix socket 慢一个数量级（raw 回环 TCP 无此问题，与驱动无关）。根因未定位（疑似云主机网络虚拟化/内核参数），**已通过应用层规避（searchCount=false 消除 COUNT）**；如后续其他 SQL 也受影响，排查方向：升级 connector 8.x、MySQL 开 `skip-name-resolve`、检查云主机网络参数。

## 4. 遗留与建议（下一轮）

| 优先级 | 事项 |
|--------|------|
| P1 | ES 搜索加短 TTL 缓存 + 超时降级 DB |
| P1 | 写接口压测（发博客/点赞/关注/套餐下单）未覆盖 |
| P1 | 定位 TCP 并发慢根因（connector 升级 8.x 试验） |
| P2 | 前端 vite preview 换 nginx |
| P2 | 压测脚本改进：keep-alive、自然退出、结果落盘 |

## 5. 复测方法

```bash
# 秒杀
python3 scripts/loadtest_seckill.py --tokens-file scripts/tokens.txt --voucher 12 --workers 32 --count 1800
# 一致性
mysql -e "SELECT COUNT(*),COUNT(DISTINCT user_id) FROM tb_voucher_order WHERE voucher_id=12"
redis-cli get seckill:stock:12
```

> 压测账号：`tb_user` 中 nick_name 以 `load_user_` 开头的 600 个；`scripts/tokens.txt` 已 gitignore。

## 6. 附录：MySQL TCP 并发慢根因排查实录（2026-10-01 深夜场）

**症状**：应用经 TCP(127.0.0.1) 并发查询 MySQL 时，`SELECT COUNT(*)` 类语句在并发≥5 时从 0.8ms 劣化到 37~100ms；走 Unix socket 无此问题。

**排除法结论**：

| 假设 | 结果 |
|------|------|
| JDBC 驱动版本（5.1.47 太老） | ❌ 换 8.0.33 依旧 99ms |
| MyBatis-Plus / Hikari 层 | ❌ 裸 JDBC 复现 |
| 表统计信息异常 | ❌ ANALYZE 无效；12 行表 EXPLAIN ANALYZE 仅 0.4ms |
| 孤儿进程/CPU 饱和污染 | ❌ 清理后（load 0.36）稳定复现 |
| 回环网络本身 | ❌ 裸 TCP echo 20 并发 0.88ms |
| 事务历史/锁 | ❌ History list length 0，无 MDL/行锁 |

**已确认的规律**：
- 并发梯度（pymysql，TCP）：1线程 0.55ms → 2线程 0.82ms → **5线程 37ms（相变）** → 20线程 97ms
- **表特异性**：`COUNT(*)` 打在单页表（tb_blog/tb_shop_type，16KB）上慢；打在 5 页表（tb_user）上 20 并发仅 2.1ms
- C 客户端（mysqlslap）同场景约 6.5ms，仅为 Java/Python 客户端的 1/15
- tcpdump 证实延迟在服务端响应阶段

**最终判断**：疑似云主机内核/虚拟化与 InnoDB 单页并发读的交互问题（与业务代码无关），升级驱动/ANALYZE/换表结构均无效。**应用层已完成规避**：热路径 COUNT 消除（searchCount=false）、N+1 消除、搜索走 Redis 缓存。遗留表如再次出现类似症状，首选应用层规避，其次考虑 MySQL 升版本或迁移宿主机验证。

## 7. 附录：秒杀链路消费端优化对比（2026-10-02）

**背景**：入口受理 ~650 RPS 后，2000 单落库需 20s（~100 单/s），消费端成为瓶颈。

| 优化项 | 前 | 后 |
|--------|----|----|
| seckill-order 分区数 | 1 | 8 |
| 消费并发 | 1（KafkaConfig 硬编码 setConcurrency(1)，yaml 配置未生效） | 8（读取 app.kafka.consumer.concurrency） |
| 落库吞吐 | ~100 单/s | ~155 单/s |
| 2000 单落库耗时 | 20s | 13s |

**修复代码**：`KafkaConfig` 硬编码并发为 1 且覆盖了 Spring Boot 自动配置，改为 `@Value` 注入；topic 重建 8 分区（1 分区时并发上限为 1）。

**剩余瓶颈**：所有订单更新同一条库存行，InnoDB 行锁使落库串行化（~155 单/s 天花板）。进一步提速需 DB 层库存分桶（分段库存），属架构级改造，演示场景暂不需要。

**工具**：`scripts/loadtest_seckill_keepalive.py`（连接复用版压测，消除客户端建连开销后入口 510 → 650 RPS）。
