# 附近商户地理距离展示 Plan

- 日期：2026-10-01
- 状态：待实施
- 关联 Spec：`docs/feature-workspace/spec/2026-10-01-geo-distance-spec.md`

## 1. 改动清单

| 文件 | 动作 |
|------|------|
| `src/main/java/com/hmdp/config/ShopGeoPreheatRunner.java` | 新增：启动时按 typeId 分组 GEOADD 全量商户坐标（有坐标才加） |
| `src/utils/location.js` | 新增：getLocation()（安全上下文→浏览器定位；否则默认杭州市中心，结果缓存 localStorage 1h）+ formatDistance() |
| `src/api/shop.js` | `getShopList` 增加 x/y 参数 |
| `src/views/ShopList.vue` | 传入坐标请求；header 展示定位状态；卡片显示距离标签 |

## 2. 关键设计

- 默认坐标：武林广场 `120.1653, 30.2765`（演示数据均为杭州商户）
- location.js 优先 `localStorage`（1 小时内不重复弹定位授权）
- `navigator.geolocation` 仅在 `window.isSecureContext` 时尝试，失败/拒绝/超时均静默降级默认坐标，并在页面提示"已默认定位到杭州市中心"
- distance 单位 km（后端 Redis GEO Metrics.KILOMETERS），<1km 显示"xxx米"

## 3. 验证

1. 重启后端 → Redis `shop:geo:1` zcard=美食商户数
2. `curl '/shop/of/type?typeId=1&x=120.165&y=30.276'` → 返回含 distance 且按距离升序
3. 前端构建部署 → 列表页出现距离标签与定位提示
