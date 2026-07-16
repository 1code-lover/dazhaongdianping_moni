# Maven 测试分层优化测试报告

## 1. 测试结论

测试分层优化通过。默认 `mvn clean test` 已从原来的 75 个测试、17 个环境错误，调整为 64 个确定性测试全部通过；11 个依赖真实环境或用于人工操作的测试按 integration/manual 标签移出默认生命周期，但仍保留显式执行入口。

## 2. 执行结果

| 检查项 | 结果 | 说明 |
|---|---|---|
| `mvn -q -Dtest=ReviewServiceTest test` | 通过 | 7 个纯 Mockito 单元测试 |
| `mvn -q clean test` | 通过 | 64 tests、0 failures、0 errors、0 skipped |
| `mvn -q -Pmanual-test -Dtest=NormalTest test` | 通过 | manual Profile 可独立选择人工测试 |
| integration Profile 属性 | 通过 | groups=integration，excludedGroups=manual |
| manual Profile 属性 | 通过 | groups=manual，excludedGroups=integration |
| 测试源码 Java 8 兼容 | 通过 | 评价测试不再调用 `String.repeat` |

## 3. 测试分层结果

### 默认单元测试

默认执行 64 个不依赖外部中间件的确定性测试，用于日常开发和提交前质量门禁。

### integration 标签

- `RedissonTest`
- `SeckillRedisRollbackIntegrationTest`

这些测试需要真实 Redis 和完整 Spring 环境，通过 `-Pintegration-test` 显式运行。

### manual 标签

- `BatchTokenGeneratorTest`
- `HmDianPingApplicationTests`
- `NormalTest`

这些类会生成压测 Token、写入数据或执行性能基准，通过 `-Pmanual-test` 显式运行，避免默认测试产生副作用。

## 4. 方案权衡

本轮没有引入 Embedded Redis 或 Testcontainers。原因是失败测试中包含数据库写入、数据预热和压测工具，并非只缺少一个可替代的 Redis；强行模拟 Redis 仍不能让这些测试成为无副作用单元测试。当前方案先建立稳定的默认门禁，同时保留后续在 CI 中增加真实集成环境的空间。

## 5. 剩余风险

1. integration 测试仍需要开发机或 CI 提供 Redis，以及完整应用启动所需的其他基础设施。
2. 当前未建立自动化集成测试流水线，`integration-test` Profile 需要人工或后续 CI 调用。
3. 人工测试中仍存在输出日志和数据写入行为，但已从默认生命周期隔离。

## 6. 最终结论

第二轮优化已恢复可重复执行的后端完整默认测试门禁。日常提交可直接运行 `mvn clean test`，真实中间件和压测场景则通过独立 Profile 按需执行。
