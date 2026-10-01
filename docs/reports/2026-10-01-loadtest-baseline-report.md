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
