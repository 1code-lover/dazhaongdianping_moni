# 压测短板修复（Feed N+1 + 连接池调优）Spec

- 日期：2026-10-01
- 状态：待评审
- 来源：`docs/reports/2026-10-01-loadtest-baseline-report.md` P0 问题
- 关联 Plan：`docs/feature-workspace/plan/2026-10-01-feed-nplus1-optimization-plan.md`

## 1. 背景

首轮压测发现两个 P0 短板：

1. **博客 Feed N+1**：`BlogServiceImpl.queryHotBlog` / `queryBlogOfFollow` 对每页每条博客逐条查询用户信息（MySQL）与点赞状态（Redis），单次请求 2N+1 次网络往返。实测 `/blog/hot` 仅 88 RPS、avg 219.5ms。
2. **连接池过小**：Lettuce `max-active=10`、Hikari 默认 10，500 并发读场景下 p95 已达上百 ms，写链路争抢会更严重。

## 2. 需求

| 编号 | 需求 | 验收标准 |
|------|------|----------|
| R1 | `queryHotBlog` 批量化：用户信息一次 IN 查询，点赞状态一次 Redis Pipeline | `/blog/hot` 相同并发下 RPS ≥ 300，avg ≤ 80ms |
| R2 | `queryBlogOfFollow` 复用同一批量填充逻辑 | Feed 接口单请求数据库/Redis 往返 ≤ 3 次 |
| R3 | Lettuce 连接池 max-active 10 → 50（env 可调） | 配置生效，高并发无连接等待异常 |
| R4 | Hikari 显式配置：max 20 / min-idle 10（env 可调） | 配置生效 |
| R5 | 行为兼容：未登录用户 isLike 字段不输出（保持 non_null 序列化）；单条查询接口不受影响 | 接口响应结构不变 |

## 3. 非目标

- 关注推送 `saveBlog` 的逐粉丝写 Redis（数据量小，暂不动）
- ES 搜索缓存（P1，另行处理）
- Tomcat 线程调优（P1）
