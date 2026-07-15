# Design 目录说明

## 目录定位

`docs/design/` 用于存放项目级别的设计文档、产品设计和架构设计。

## 与 feature-workspace 的区别

| 维度 | design/ | feature-workspace/ |
|------|---------|-------------------|
| 范围 | 项目级、全局性 | 功能级、具体任务 |
| 内容 | 整体架构、产品规划、技术选型 | 单个功能的需求和方案 |
| 时效 | 长期有效，变更少 | 短期工作文档，迭代频繁 |
| 示例 | 系统架构设计、产品蓝图 | 登录功能优化、订单bug修复 |

## 目录内容

### 产品设计
- `product-design.md` - 产品整体规划和功能蓝图

### 技术设计
- `2026-06-04-backend-enhancement-design.md` - 后端架构增强设计
- `frontend-design.md` - 前端架构设计

## 使用建议

1. **项目启动阶段**: 在此目录编写整体架构和产品设计
2. **功能开发阶段**: 使用 `docs/feature-workspace/` 管理具体功能的 spec 和 plan
3. **文档归档**: 重要的技术方案可以从 feature-workspace 同步到这里作为正式设计文档

## 参考

具体功能开发流程参见: [DEVELOPMENT_WORKFLOW.md](../../DEVELOPMENT_WORKFLOW.md)
