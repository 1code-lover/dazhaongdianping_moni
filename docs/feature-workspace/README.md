# 功能工作区说明

## 目录定位

`docs/feature-workspace/` 用于集中存放后续功能新增、功能修复、体验优化等工作的过程文档。

这个目录的目标是把「准备做什么」和「准备怎么做」先沉淀下来，避免文档分散、命名混乱、后续不好追踪。

## 适用范围

- 新功能添加
- 现有功能修复
- 页面优化和交互优化
- 性能优化
- 可维护性改造

## 目录结构

```text
docs/feature-workspace/
├─ README.md
├─ INDEX.md
├─ spec/
│  └─ README.md
└─ plan/
   └─ README.md
```

## 子目录说明

- `spec/`
  - 存放需求分析、问题背景、目标范围、验收标准等说明文档。
- `plan/`
  - 存放实施方案、任务拆分、改动范围、风险评估、测试考虑等执行文档。

## 工作流程

**所有新功能开发、bug修复、优化改造必须遵循以下流程:**

1. **编写 Spec** (`spec/` 目录)
   - 说明背景、目标、范围、验收标准
   - 必须通过 review 才能进入下一步

2. **编写 Plan** (`plan/` 目录)
   - 技术方案、任务拆解、风险评估
   - 必须通过 review 才能开始实施

3. **实施开发**
   - 严格按照 Plan 执行
   - 完成后验证是否符合 Spec 的验收标准

详细规范参见: [DEVELOPMENT_WORKFLOW.md](../../DEVELOPMENT_WORKFLOW.md)

## 与其他目录的关系

| 目录 | 用途 | 关系 |
|------|------|------|
| `docs/design/` | 项目级设计文档(架构、产品规划) | 全局性、长期有效 |
| `docs/feature-workspace/` | 功能级工作文档(Spec/Plan) | 具体任务、迭代频繁 |
| `docs/superpowers/plans/` | 已实施的方案归档 | feature-workspace 完成后可迁移到此 |
| `docs/reports/` | 测试报告 | 记录测试过程和结果 |

## 文档管理建议

- **进行中的工作**: 放在 feature-workspace
- **已完成的重要方案**: 可迁移到 superpowers/plans 归档
- **项目级设计**: 放在 design 目录
