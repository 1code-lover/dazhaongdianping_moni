# Design 目录说明

## 目录定位

`docs/design/` 用于存放项目级别的设计文档、产品设计和架构设计。

## 目录结构

```
docs/design/
├── architecture/    # 架构设计
├── api/             # API 设计
├── database/        # 数据库设计
├── frontend/        # 前端设计
├── archived/        # 已实现的设计归档
└── README.md        # 设计文档索引
```

## 目录内容

### 产品设计
- `2026-06-04-product-design.md` - 产品整体规划和功能蓝图

### 技术设计
- `2026-06-04-backend-enhancement-design.md` - 后端架构增强设计
- `2026-06-21-frontend-design.md` - 前端架构设计

## 命名规范

`{date}-{topic}-design.md`

示例：`2026-06-04-backend-enhancement-design.md`

## 使用建议

1. **项目启动阶段**: 在此目录编写整体架构和产品设计
2. **功能开发阶段**: 使用 `docs/specs/features/` 管理具体功能需求，使用 `docs/plans/features/` 管理实施方案
3. **文档归档**: 已实现的设计归档到 `docs/design/archived/`

## 参考

- 功能开发流程参见: [AGENTS.md](../../AGENTS.md)
- 文档管理规范参见: [DOCS.md](../../DOCS.md)
