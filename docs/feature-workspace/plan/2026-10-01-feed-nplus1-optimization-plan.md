# 压测短板修复（Feed N+1 + 连接池调优）Plan

- 日期：2026-10-01
- 状态：待实施
- 关联 Spec：`docs/feature-workspace/spec/2026-10-01-feed-nplus1-optimization-spec.md`

## 1. 方案设计

### 1.1 Feed N+1 修复（`BlogServiceImpl`）

新增批量填充方法，将 `2N+1` 次往返降为 `3` 次：

```
fillBlogDetails(List<Blog> blogs)
  ├─ MySQL：userIds 去重 → listByIds 一次 IN 查询 → Map<id, UserDTO>
  ├─ Redis：executePipelined 一次发送 N 个 ZSCORE → Map<blogId, isLike>
  └─ 内存循环填充 name / icon / isLike
```

- `queryHotBlog`：forEach 循环替换为 `fillBlogDetails(records)`
- `queryBlogOfFollow`：blog 列表循环替换为 `fillBlogDetails(blogs)`
- `queryBlogById` 保持原逐条逻辑（单条无 N+1 问题），`queryBlogUser` / `isBlogLiked` 保留
- 未登录用户：`isLike` 不设置（jackson non_null 下不输出），与现状一致

### 1.2 连接池调优（`application.yaml`）

| 配置 | 原值 | 新值 | 环境变量 |
|------|------|------|----------|
| `spring.redis.lettuce.pool.max-active` | 10 | 50 | `REDIS_POOL_MAX_ACTIVE` |
| `spring.redis.lettuce.pool.max-idle` | 10 | 50 | `REDIS_POOL_MAX_IDLE` |
| `spring.redis.lettuce.pool.min-idle` | 1 | 5 | `REDIS_POOL_MIN_IDLE` |
| `spring.datasource.hikari.maximum-pool-size` | （默认10） | 20 | `DB_POOL_MAX` |
| `spring.datasource.hikari.minimum-idle` | （默认= max） | 10 | `DB_POOL_MIN_IDLE` |

## 2. 改动清单

| 文件 | 动作 |
|------|------|
| `src/main/java/com/hmdp/service/impl/BlogServiceImpl.java` | 新增 `fillBlogDetails` / `batchCheckLiked`；改造 `queryHotBlog` / `queryBlogOfFollow` |
| `src/main/resources/application.yaml` | 连接池配置 |
| `docs/feature-workspace/INDEX.md` | 登记文档 |

## 3. 验证计划

1. `mvn clean package -Dmaven.test.skip=true` 编译通过
2. 重启后端，同基线脚本复测（20线程×25请求）：
   - `/blog/hot` 目标 RPS ≥ 300、avg ≤ 80ms
   - 其余接口无回退（对比基线表）
3. 功能回归：登录用户看博客 isLike 正常；未登录访问不报错；Feed 流滚动分页正常
4. 在 `docs/reports/2026-10-01-loadtest-baseline-report.md` 追加复测对比数据

## 4. 风险与回滚

| 风险 | 应对 |
|------|------|
| Pipeline 结果顺序 | Spring Data Redis pipeline 结果与命令发送顺序一致，按序映射 blogId |
| 连接池增大占资源 | 4C14G 空闲充足；均可通过环境变量回退原值 |
| 回滚 | git revert；配置默认值的修改即时生效 |
