# 项目开发规范

## 目录

- [快速参考](#快速参考)
- [开发流程](#开发流程)
- [代码注释规范](#代码注释规范)
- [提交信息格式](#提交信息格式)
- [分支管理规范](#分支管理规范)
- [设计原则](#设计原则)
- [代码风格规范](#代码风格规范)
- [测试规范](#测试规范)
- [代码审查清单](#代码审查清单)
- [环境和工具](#环境和工具)
- [常见问题](#常见问题)

---

## 快速参考

| 项目 | 要点 |
|------|------|
| **流程** | 设计方案 → 审核 → 实施方案 → 审核 → 编码 → 测试方案 → 审核 → 执行测试 → 测试报告 → 审核 → 提交 |
| **提交** | `feat/fix/docs/chore/refactor/perf: 描述` |
| **命名** | 类 `PascalCase`、方法 `camelCase`、常量 `UPPER_SNAKE_CASE`、变量 `camelCase` |
| **函数** | 单一职责、≤30 行、≤4 参数 |
| **测试** | 命名 `test{方法}{场景}{结果}`、覆盖率 ≥80% |
| **注释** | 文件头 + 函数注释（必须），复杂逻辑行内注释 |

---

## 开发流程

每个功能遵循完整流程，确保质量和可追溯性：

```
开发方案 → 审核 → 实施方案 → 审核 → 编写代码 → 测试方案 → 审核 → 执行测试 → 测试报告 → 审核 → 提交推送
```

### 阶段产出

| 阶段 | 内容 | 产出 |
|------|------|------|
| **1. 开发方案** | 需求分析、业务流程、数据模型、接口设计 | `docs/design/{date}-{feature}-requirements.md` |
| **2. 实施方案** | 文件结构、Task拆分、实现细节、单测计划 | `docs/superpowers/plans/{date}-{feature}-plan.md` |
| **3. 编写代码** | 遵循规范、中文注释、编译通过 | — |
| **4. 测试方案** | 单元/接口/异常测试计划 | `docs/reports/{date}-{feature}-test-plan.md` |
| **5. 执行测试** | 运行单测、接口测试、业务验证 | — |
| **6. 测试报告** | 用例结果、覆盖率、缺陷记录、结论 | `docs/reports/{date}-{feature}-test-report.md` |
| **7. 提交推送** | 语义化提交、推送远程 | — |

> 每个阶段需审核通过后方可进入下一步。

---

## 代码注释规范

### 文件头注释（必须）

```java
/**
 * 订单服务实现类
 * 处理订单创建、查询、退款等业务逻辑
 *
 * @author ethan
 * @date 2026-06-10
 */
```

### 函数注释（必须）

```java
/**
 * 创建订单
 * 
 * @param orderDTO 订单信息
 * @return 订单ID
 * @throws BusinessException 库存不足时抛出
 */
public Long createOrder(OrderDTO orderDTO) { ... }
```

### 行内注释（复杂逻辑必须）

```java
// 分布式锁防止超卖
boolean locked = tryLock("order:" + comboId);
```

---

## 提交信息格式

```
feat: 添加xxx功能
fix: 修复xxx问题
test: 添加xxx测试
docs: 更新xxx文档
chore: 其他改动（配置、依赖等）
refactor: 代码重构
perf: 性能优化
```

---

## 分支管理规范

### 分支命名

| 类型 | 格式 | 示例 |
|------|------|------|
| 功能 | `feat/{issue-id}-{name}` | `feat/123-user-login` |
| 修复 | `fix/{issue-id}-{name}` | `fix/456-cache-bug` |
| 文档 | `docs/{description}` | `docs/api-documentation` |
| 重构 | `refactor/{description}` | `refactor/shop-service` |

### 规则

- `master` 分支受保护，不允许直接推送
- 必须通过 PR 合并，至少 1 人审核，CI 检查通过
- PR 流程：创建分支 → 开发+测试 → 提交 PR → 审核 → 合并 → 删除分支

---

## 设计原则

### 最小开发原则（单一职责）

每个函数只负责一个小功能。拆分标准：

| 维度 | 标准 |
|------|------|
| 行数 | 函数体 ≤ 30 行 |
| 职责 | 只做一件事 |
| 命名 | 能用一句话描述 |
| 参数 | ≤ 4 个 |

---

## 代码风格规范

### 命名规范

| 类型 | 规范 | 示例 |
|------|------|------|
| 类名 | PascalCase | `UserService`, `CacheClient` |
| 方法名 | camelCase | `queryUserById`, `saveBlog` |
| 常量 | UPPER_SNAKE_CASE | `LOGIN_USER_KEY`, `MAX_PAGE_SIZE` |
| 变量 | camelCase | `userPhone`, `pageSize`, `shopList` |

### 格式规范

- **缩进**：4 空格
- **行长**：≤ 120 字符
- **空行**：方法间 1 行，逻辑块间 1 行
- **导入顺序**：标准库 (`java.*`) → 第三方 (`com.*`/`org.*`) → 项目内部 (`com.hmdp.*`)
- **集合初始化**：使用菱形运算符 `new ArrayList<>()`

---

## 测试规范

- **覆盖率**：关键业务逻辑 ≥ 80%，Service/Util 层必须
- **命名**：`test{方法名}{场景}{预期结果}()`
- **Mock 策略**：Service 层 Mock DB 和外部依赖；集成测试用 H2；Redis 用 Embedded Redis

详见 [TESTING.md](TESTING.md)

---

## 代码审查清单

- [ ] 代码风格符合规范
- [ ] 添加必要中文注释
- [ ] 编写单元测试，覆盖率 ≥ 80%
- [ ] 无硬编码魔法值
- [ ] 无 TODO / FIXME
- [ ] 无 `System.out.println`，使用 slf4j
- [ ] 数据库查询有性能考虑
- [ ] Redis 操作有异常处理
- [ ] 接口有权限检查

---

## 环境和工具

**环境要求**：Java 8、Maven 3.6+、MySQL 5.7+、Redis 6.x、Kafka 2.x+

**推荐 IDE**：IntelliJ IDEA + Alibaba Java Coding Guidelines、Save Actions、SonarLint、Lombok 插件

**调试工具**：Postman（API）、Redis Desktop Manager、MySQL Workbench

详见 [TOOLS.md](TOOLS.md)

---

## 常见问题

| 问题 | 摘要 |
|------|------|
| 代码风格修复 | IDEA: `Ctrl+Shift+L` 格式化 |
| 测试覆盖率 | `mvn clean test jacoco:report` → `target/site/jacoco/index.html` |
| 本地运行测试 | `mvn clean test` / `mvn "-Dtest=类名" test` / `mvn "-Dtest=类名#方法" test` |
| 提交前检查 | 运行测试 → 格式化 → 检查注释 → 验证 commit 格式 → 清除调试代码 |

详见 [FAQ.md](FAQ.md)
