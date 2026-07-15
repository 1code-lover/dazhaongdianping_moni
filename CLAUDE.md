# 项目说明

## 项目简介

本地生活平台 - 类似大众点评的本地商户服务平台，提供商户展示、优惠券、套餐、订单、评价等功能。

## 技术栈

### 后端
- Spring Boot 2.7.x
- MyBatis-Plus
- Redis
- MySQL 5.7
- Maven

### 前端
- Vue 3
- Vite
- Element Plus

## 开发规范

### 工作流程

**所有新功能、bug修复、优化改造必须遵循 Spec → Plan → 实现 的流程。**

详细规范见: [DEVELOPMENT_WORKFLOW.md](./DEVELOPMENT_WORKFLOW.md)

### 代码规范

1. **注释规范**
   - 所有文件头部必须有用途说明(中文)
   - 所有函数必须有中文文档说明
   - 关键逻辑必须有中文行内注释

2. **Git 提交规范**
   - 使用 `git-commit` skill 辅助生成符合规范的提交信息
   - 格式: `<type>(<scope>): <subject>`
   - 示例: `feat(order): 添加订单核销功能`

3. **文档规范**
   - 需求文档: `docs/feature-workspace/spec/`
   - 方案文档: `docs/feature-workspace/plan/`
   - 测试报告: `docs/reports/`
   - 设计文档: `docs/design/`

## 项目结构

```
dazhaongdianping_moni/
├── hmdp_backend/              # 后端项目
│   ├── src/main/java/com/hmdp/
│   │   ├── controller/        # 控制器层
│   │   ├── service/           # 服务层
│   │   ├── mapper/            # 数据访问层
│   │   ├── entity/            # 实体类
│   │   └── utils/             # 工具类
│   └── src/main/resources/
│       ├── application.yaml   # 配置文件
│       └── db/                # SQL脚本
├── heima_qianduan/            # 前端项目
│   └── nginx-1.18.0/html/hmdp/
│       ├── src/
│       │   ├── views/         # 页面组件
│       │   ├── components/    # 通用组件
│       │   └── api/           # API封装
│       └── vite.config.js
└── docs/                      # 文档目录
    ├── feature-workspace/     # 功能工作区
    │   ├── spec/             # 需求文档
    │   └── plan/             # 方案文档
    ├── design/               # 设计文档
    └── reports/              # 测试报告
```

## 常用 Skills

- `chinese-code-comments` - 自动添加中文注释
- `git-commit` - Git 提交规范助手
- `superpowers:brainstorming` - 需求分析和设计头脑风暴
- `superpowers:writing-plans` - 编写实施方案
- `superpowers:test-driven-development` - TDD 开发
- `superpowers:verification-before-completion` - 完成前验证

## 开发指南

### 启动后端

```bash
cd hmdp_backend
mvn clean install
mvn spring-boot:run
```

### 启动前端

```bash
cd heima_qianduan/nginx-1.18.0/html/hmdp
npm install
npm run dev
```

### 数据库初始化

执行 `hmdp_backend/src/main/resources/db/` 目录下的 SQL 脚本

## 注意事项

1. **高风险操作需确认**: 删除文件、force push、修改共享状态等操作前必须确认
2. **代码提交前**: 确保所有测试通过，文档更新完整
3. **功能开发前**: 必须先编写 Spec 和 Plan 文档并通过 review
4. **问题记录**: 遇到问题及时更新相关文档，方便后续追踪

## 相关文档

- [开发工作流规范](./DEVELOPMENT_WORKFLOW.md)
- [功能工作区说明](./docs/feature-workspace/README.md)
- [功能文档索引](./docs/feature-workspace/INDEX.md)
- [设计文档说明](./docs/design/README.md)
