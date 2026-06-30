# 功能工作区索引

## 使用说明

本索引用于登记 `docs/feature-workspace/` 下的功能文档，方便后续按主题、时间和状态快速查找。

## 目录入口

- [工作区说明](./README.md)
- [Spec 目录说明](./spec/README.md)
- [Plan 目录说明](./plan/README.md)

## 文档登记规则

- `spec/` 文档建议命名为：`{date}-{feature}-spec.md`
- `plan/` 文档建议命名为：`{date}-{feature}-plan.md`
- `date` 使用 `YYYY-MM-DD`
- `feature` 使用英文短语，多个单词用 `-` 连接

## 当前文档清单

### Spec

| 日期 | 主题 | 文件 | 状态 | 备注 |
|------|------|------|------|------|
| 2026-06-09 | 套餐订单核销功能 | `spec/2026-06-09-combo-order-verify-spec.md` | 已完成 | 已实施，对应 plan 见 docs/superpowers/plans/ |
| 2026-06-10 | 商家入驻功能 | `spec/2026-06-10-shop-apply-spec.md` | 已完成 | 已实施，对应 plan 见 docs/superpowers/plans/ |
| 2026-06-16 | 后端重启优化 | `spec/2026-06-16-backend-restart-spec.md` | 已完成 | 已实施，对应 plan 见 docs/superpowers/plans/ |
| 2026-06-16 | 评价系统 | `spec/2026-06-16-review-spec.md` | 已完成 | 已实施 |
| 2026-06-21 | 前端UI优化 | `spec/2026-06-21-frontend-ui-spec.md` | 已完成 | 已实施，对应 plan 见 docs/superpowers/plans/ |
| 2026-06-30 | 前端优化 | `spec/2026-06-30-frontend-optimization-spec.md` | 待评审 | 基于当前前端代码审查整理 |

### Plan

| 日期 | 主题 | 文件 | 状态 | 备注 |
|------|------|------|------|------|
| 2026-06-30 | 前端优化 | `plan/2026-06-30-frontend-optimization-plan.md` | 待实施 | 基于已评审 spec 编写 |

### 历史 Plan (已迁移到 docs/superpowers/plans/)

以下文档已从工作区迁移到正式目录:

| 日期 | 主题 | 文件 | 备注 |
|------|------|------|------|
| 2026-06-05 | 后端增强 | `docs/superpowers/plans/2026-06-05-backend-enhancement-plan.md` | - |
| 2026-06-05 | 智能客服 | `docs/superpowers/plans/2026-06-05-chatbot-plan.md` | - |
| 2026-06-09 | 套餐订单核销 | `docs/superpowers/plans/2026-06-09-combo-order-verify-plan.md` | - |
| 2026-06-10 | 商家入驻 | `docs/superpowers/plans/2026-06-10-shop-apply-plan.md` | - |
| 2026-06-16 | 后端重启 | `docs/superpowers/plans/2026-06-16-backend-restart-plan.md` | - |
| 2026-06-16 | 评价系统 | `docs/superpowers/plans/2026-06-16-review-plan.md` | - |
| 2026-06-21 | 前端UI | `docs/superpowers/plans/2026-06-21-frontend-ui-plan.md` | - |

## 维护建议

- 新增文档后，同步更新本索引。
- 文档废弃时，在备注中说明替代文档或归档去向。
- 已进入正式交付流程的内容，可在备注中补充对应的 `docs/design/` 或 `docs/superpowers/plans/` 文件路径。
