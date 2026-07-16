# Maven 默认测试被外部中间件阻断

## 问题现象

执行 `mvn test` 时共发现 75 个测试，其中 17 个错误。失败集中在批量 Token 生成、综合数据预热、Redisson、Redis 回滚和评价服务测试，错误入口均表现为本机 `127.0.0.1:6379` 连接被拒绝，导致 Spring ApplicationContext 无法创建。

## 排查过程

1. 汇总 Surefire 报告，确认 17 个错误来自 5 个测试类，而不是生产代码编译失败。
2. 检查测试职责，发现 `BatchTokenGeneratorTest`、`HmDianPingApplicationTests` 和 `NormalTest` 实际属于压测或人工数据工具，不应进入默认回归测试。
3. 检查 `RedissonTest` 和 `SeckillRedisRollbackIntegrationTest`，确认它们需要真实 Redis，属于集成测试。
4. 检查 `ReviewServiceTest`，确认它只验证 Service 业务规则，却使用 `@SpringBootTest` 启动了完整容器，间接依赖 Redis、MySQL 和其他基础设施。
5. 验证已有 `@Tag("integration")` 没有 Maven 排除配置，因此标记并未改变默认测试行为。

## 根因

项目将确定性单元测试、真实中间件集成测试和人工压测工具混在同一个 Surefire 默认生命周期中；同时评价服务测试错误使用完整 Spring 上下文，导致任何本地中间件缺失都会让整个质量门禁失败。

## 解决方案

1. 在 `pom.xml` 配置 Surefire，默认排除 `integration` 和 `manual` 标签。
2. 新增 `integration-test` 和 `manual-test` Maven Profile，使两类测试仍可按需显式执行。
3. 为 Redisson、Redis 回滚测试标记 `integration`，为 Token 生成、数据预热、基准测试标记 `manual`。
4. 将 `ReviewServiceTest` 改为 Mockito 单元测试，显式注入 MyBatis-Plus `baseMapper`，并按测试场景提供最小 Mapper 桩。
5. 将测试中的 `String.repeat` 替换为 Java 8 兼容实现，保持与项目运行环境一致。

## 验证结果

- `mvn -q -Dtest=ReviewServiceTest test`：通过，7 个评价服务用例不再启动 Spring 容器。
- `mvn -q clean test`：通过，共 64 个默认测试，0 failures、0 errors、0 skipped。
- `mvn -q -Pmanual-test -Dtest=NormalTest test`：通过，证明人工测试 Profile 可独立执行。
- Maven Profile 属性检查：`integration-test` 仅包含 integration、排除 manual；`manual-test` 仅包含 manual、排除 integration。

## 关键收获

通过按职责分离单元测试、集成测试和人工工具，可以让默认质量门禁快速且稳定，同时保留真实中间件验证和压测能力，而不是简单删除或永久禁用测试。
