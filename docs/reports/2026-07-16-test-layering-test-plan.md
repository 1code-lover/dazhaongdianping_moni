# Maven 测试分层优化测试方案

## 1. 测试目标

验证默认 Maven 测试不再依赖本机 Redis、MySQL 或 Kafka，并确认 integration/manual 测试仍能通过 Profile 显式选择；同时保证评价服务业务规则在纯单元测试中得到覆盖。

## 2. 测试范围

- Maven Surefire 标签过滤配置。
- `integration-test` 与 `manual-test` Profile。
- `ReviewServiceTest` 的 Mockito 隔离与 7 个业务场景。
- 默认测试套件的完整编译和执行。
- Java 8 测试代码兼容性。

## 3. 测试用例

| 模块 | 场景 | 预期结果 |
|---|---|---|
| 默认测试 | 未启动 Redis 执行 `mvn clean test` | 默认确定性测试全部通过 |
| 默认测试 | integration/manual 标记类存在 | 两类测试不进入默认测试统计 |
| 评价服务 | 合法评价提交 | 保存评价并更新商户评分 |
| 评价服务 | 评分或内容非法 | 返回明确业务错误且不写数据库 |
| 评价服务 | 查询存在/不存在的评价 | 分别返回成功和不存在错误 |
| 评价服务 | 合法回复/超长回复 | 分别更新成功和拒绝更新 |
| 人工测试 Profile | 指定 `manual-test` 运行位图基准 | 仅执行 manual 标签测试 |
| 集成测试 Profile | 激活 `integration-test` | groups 为 integration，排除 manual |
| Java 8 兼容 | 测试源码编译 | 不依赖 `String.repeat` 等 Java 11 API |

## 4. 执行命令

```bash
mvn -q -Dtest=ReviewServiceTest test
mvn -q clean test
mvn -q -Pmanual-test -Dtest=NormalTest test
mvn help:evaluate -q -DforceStdout -Pintegration-test -Dexpression=test.groups
mvn help:evaluate -q -DforceStdout -Pmanual-test -Dexpression=test.groups
```

## 5. 通过标准

- 默认测试无需启动 Redis、MySQL、Kafka 即可完成。
- 默认测试 0 failures、0 errors。
- 评价服务 7 个场景全部通过且不加载 Spring ApplicationContext。
- manual/integration Profile 配置结果与标签职责一致。
- 真实中间件测试没有被删除，仍可在对应环境中显式运行。
