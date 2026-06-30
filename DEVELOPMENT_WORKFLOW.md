# 开发工作流规范

## 适用场景

本规范适用于所有涉及代码修改的工作，包括但不限于:
- 新功能开发
- bug 修复
- 性能优化
- 重构改造

## 工作流程

### 1. 需求阶段 (Spec)

**目标**: 明确「为什么做」和「做什么」

**输出文档**: `docs/feature-workspace/spec/{date}-{feature}-spec.md`

**必须包含**:
- 问题背景 / 需求背景
- 当前现状分析
- 目标与范围
- 明确的非目标(不做什么)
- 影响范围(页面/接口/数据)
- 验收标准

**检查点**:
- [ ] 背景说明清晰，目标明确
- [ ] 范围边界清楚(做什么、不做什么)
- [ ] 验收标准可量化、可验证

### 2. 方案阶段 (Plan)

**目标**: 明确「怎么做」

**输出文档**: `docs/feature-workspace/plan/{date}-{feature}-plan.md`

**必须包含**:
- 涉及的文件清单
- 数据流或调用链路
- 任务拆解(按步骤)
- 风险点识别
- 测试方案
- 回滚考虑

**检查点**:
- [ ] 技术方案合理，风险可控
- [ ] 任务拆解清晰，可执行
- [ ] 测试覆盖完整

### 3. 实施阶段 (Implementation)

**要求**:
- 严格按照 Plan 中的步骤执行
- 每个步骤完成后标记 `- [x]`
- 遇到问题及时记录并更新 Plan

**代码规范**:
- 文件头注释说明用途(中文)
- 函数必须有中文文档说明
- 提交信息遵循 Git 提交规范(见 `git-commit` skill)

**检查点**:
- [ ] 代码符合现有规范
- [ ] 测试用例通过
- [ ] 文档更新完成

### 4. 验证阶段 (Verification)

**必须验证**:
- 功能是否符合 Spec 中的验收标准
- 是否引入新的 bug
- 性能是否符合预期
- 日志是否完善

**输出**:
- 测试报告(如需要): `docs/reports/{date}-{feature}-test-report.md`

## 文档命名规范

### Spec 文档
格式: `{date}-{feature}-spec.md`

示例:
- `2026-06-30-user-login-fix-spec.md`
- `2026-06-30-order-performance-optimization-spec.md`

### Plan 文档
格式: `{date}-{feature}-plan.md`

示例:
- `2026-06-30-user-login-fix-plan.md`
- `2026-06-30-order-performance-optimization-plan.md`

## 常见问题

### Q: 简单的 bug 修复也需要写 Spec 和 Plan 吗?

A: 视情况而定:
- **需要**: 涉及多个文件、影响多个模块、风险较高的修复
- **可简化**: 单文件、逻辑简单、风险可控的修复(可以简化为一个文档说明背景+方案)

### Q: 紧急线上问题怎么办?

A: 
1. 先止损修复上线
2. 事后补充 Spec 和 Plan 文档，记录问题根因和修复方案
3. 评估是否需要更全面的优化

### Q: Spec 和 Plan 分别由谁来写?

A:
- **Spec**: 需求方(产品/业务) 或 开发负责人
- **Plan**: 开发负责人
- **Review**: 团队成员交叉 review

## 工具支持

### 相关 Skills

- `superpowers:writing-plans`: 帮助编写 Plan 文档
- `superpowers:brainstorming`: 需求分析和设计前的头脑风暴
- `superpowers:test-driven-development`: TDD 开发指导
- `superpowers:verification-before-completion`: 完成前验证清单
- `git-commit`: Git 提交规范助手

### 目录结构

```
docs/
├── feature-workspace/        # 功能工作区
│   ├── spec/                # 需求文档
│   │   ├── README.md
│   │   └── {date}-{feature}-spec.md
│   └── plan/                # 方案文档
│       ├── README.md
│       └── {date}-{feature}-plan.md
├── reports/                 # 测试报告
│   └── {date}-{feature}-test-report.md
└── design/                  # 正式设计文档(已归档的历史文档)
```

## 检查清单

### 开始工作前
- [ ] 已创建 Spec 文档
- [ ] Spec 已通过 review
- [ ] 已创建 Plan 文档
- [ ] Plan 已通过 review

### 代码提交前
- [ ] 代码符合规范
- [ ] 所有测试通过
- [ ] 文档已更新
- [ ] Commit message 符合规范

### 上线前
- [ ] 功能验证通过
- [ ] 性能测试通过(如需要)
- [ ] 回滚方案已准备
- [ ] 监控告警已配置
