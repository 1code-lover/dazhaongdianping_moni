# 环境和工具

> 本文档是 [AGENTS.md](AGENTS.md) 环境工具章节的详细版本。

---

## 环境要求

| 工具 | 版本 | 检查命令 |
|------|------|----------|
| Java | 8 | `java -version` |
| Maven | 3.6+ | `mvn -v` |
| MySQL | 5.7+ | `mysql --version` |
| Redis | 6.x | `redis-cli --version` |
| Kafka | 2.x+ | 通过配置文件验证 |

---

## 推荐 IDE

**IntelliJ IDEA**（推荐），插件：

| 插件 | 用途 |
|------|------|
| Alibaba Java Coding Guidelines | 代码规范检查 |
| Save Actions | 自动格式化 |
| SonarLint | 代码质量分析 |
| Lombok Plugin | Lombok 支持 |
| CheckStyle | 代码风格检查 |

### IDE 格式化配置

1. Preferences → Editor → Code Style → Java
2. 设置缩进为 4 个空格
3. 设置行长限制为 120
4. 启用 "Optimize imports"

---

## 调试工具

| 工具 | 用途 |
|------|------|
| Postman / Reqable | API 测试 |
| Redis Desktop Manager | Redis 数据查看 |
| MySQL Workbench | 数据库管理 |
| Kafka Topic UI | Kafka 消息查看 |
