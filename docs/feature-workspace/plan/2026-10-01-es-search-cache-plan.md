# ES 搜索缓存与降级 Plan

- 日期：2026-10-01
- 状态：待实施
- 关联 Spec：`docs/feature-workspace/spec/2026-10-01-es-search-cache-spec.md`

## 1. 方案（`ShopSearchServiceImpl` 改造）

```
searchByKeyword:
  key = cache:shop-search:{MD5(keyword|page|size|坐标)}
  1. 查 Redis 缓存 → 命中直接返回（TTL 30s）
  2. 未命中 → 查 ES
       ├─ 成功 → 写缓存（JSON 序列化 ShopDocument 列表）→ 返回
       └─ 异常 → log.warn → DB LIKE 兜底（limit size，无缓存）
  坐标为 null 时以 "0,0" 参与组键，避免排序语义错乱
```

- 失效：`syncShopToEs` / `syncAllShopsToEs` 执行后 SCAN 删除 `cache:shop-search:*`
- 序列化：hutool JSONUtil（项目已有依赖）

## 2. 改动清单

| 文件 | 动作 |
|------|------|
| `src/main/java/com/hmdp/service/impl/ShopSearchServiceImpl.java` | 缓存读写 + ES 异常降级 DB |
| `src/main/java/com/hmdp/utils/RedisConstants.java` | 新增 `CACHE_SHOP_SEARCH_KEY` 前缀与 TTL |

## 3. 验证计划

1. 编译重启后：同关键词连续两次搜索，第二次明显更快（Redis 有 key）
2. `docker stop hmdp-es` → 搜索返回 DB 兜底结果 → `docker start hmdp-es`
3. 新增/修改商户 → `/shop/sync` → 缓存被清，搜索结果即时反映
4. 回归：带距离坐标搜索正常

## 4. 风险与回滚

- 缓存与 DB 数据 30s 内不一致：可接受（有 R3 主动失效兜底）
- 回滚：git revert
