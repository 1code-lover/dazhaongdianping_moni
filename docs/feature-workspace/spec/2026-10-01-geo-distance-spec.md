# 附近商户地理距离展示 Spec

- 日期：2026-10-01
- 状态：待评审
- 关联 Plan：`docs/feature-workspace/plan/2026-10-01-geo-distance-plan.md`

## 1. 背景

后端 `queryShopByType` 已支持 x/y 坐标经 Redis GEO 距离排序（5km 内，返回 `distance` 字段），但：
1. **Geo 数据从未加载**：`shop:geo:{typeId}` key 不存在，距离查询永远走降级分页
2. **前端未接入定位**：不传 x/y，不展示距离

## 2. 需求

| 编号 | 需求 | 验收标准 |
|------|------|----------|
| R1 | 启动时预加载商户坐标到 Redis GEO（仿 SeckillStockPreheatRunner） | `shop:geo:{typeId}` 存在，zcard=该分类商户数 |
| R2 | 前端获取用户位置传入 x/y | 带坐标请求返回按距离排序且含 distance |
| R3 | 商户卡片展示距离 | 列表页显示"距您 x.xkm" |
| R4 | 非 HTTPS 环境降级 | http 访问时定位被拒→默认杭州市中心并提示，功能可用 |

## 3. 非目标

- 不引入地图 SDK（高德/百度 key 申请）
- IP 定位库（精度差，不值得）
- 商户新增/编辑实时同步 GEO（重启加载足够，数据稳定）
