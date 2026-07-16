# Maven 测试因外部依赖无法稳定执行

## 基本信息

- 类型：bug
- 日期：2026-07-16
- 相关模块：Maven 测试生命周期、评价服务测试、Redis 集成测试、压测工具
- 相关文件：
  - `pom.xml`
  - `src/test/java/com/hmdp/service/ReviewServiceTest.java`
  - `src/test/java/com/hmdp/RedissonTest.java`
  - `src/test/java/com/hmdp/integration/SeckillRedisRollbackIntegrationTest.java`
  - `src/test/java/com/hmdp/BatchTokenGeneratorTest.java`
  - `src/test/java/com/hmdp/HmDianPingApplicationTests.java`
  - `src/test/java/com/hmdp/NormalTest.java`

## 问题现象

项目执行 `mvn test` 时，75 个测试中有 17 个错误。即使本轮业务代码和定向单测正确，只要开发机没有启动 Redis，Redisson bean 创建和 Spring 上下文加载就会失败，导致默认质量门禁不可用。

## 根因分析

根因不是单一 Redis 配置，而是测试职责混杂：人工生成 Token、数据预热和性能基准被当成普通测试；真实 Redis 集成测试虽然已有标签，但 Maven 没有排除配置；评价服务业务规则测试又错误使用 `@SpringBootTest`，把一个可独立验证的 Service 单测绑定到了完整基础设施。

## 解决方案

1. 在 Surefire 中默认排除 integration/manual 标签。
2. 提供 `integration-test` 和 `manual-test` Profile，保留显式执行能力。
3. 给真实 Redis 测试和人工工具补齐职责标签及说明。
4. 将 `ReviewServiceTest` 重构为 Mockito 单元测试，显式注入继承的 `baseMapper`，并为每个场景只配置必要的 Mapper 行为。
5. 移除测试对 Java 11 `String.repeat` 的依赖，保证 Java 8 兼容。

## 为什么选这个方案

默认测试的目标应该是快速、确定、无外部副作用，而真实中间件验证和压测也不应该被删除。通过 Maven Profile 分层，可以同时满足日常质量门禁和按需集成验证；把评价测试改成纯单测后，还能保留 7 个业务场景并显著缩短执行时间。

## 其他方案与为什么没选

- 引入 Embedded Redis：只能覆盖 Redis，无法解决人工测试对数据库、完整 Spring 环境和数据写入的依赖。
- 引入 Testcontainers：适合后续 CI 集成测试，但当前项目还需要协调 MySQL、Kafka 等服务，作为本轮最小修复成本过高。
- 使用 `@Disabled` 永久跳过：容易让测试被遗忘，也不方便显式恢复执行，因此采用标签和 Profile。

## 验证与结果

- `mvn -q -Dtest=ReviewServiceTest test`：7 个评价服务单元测试通过。
- `mvn -q clean test`：64 个默认测试全部通过，0 failures、0 errors、0 skipped。
- `mvn -q -Pmanual-test -Dtest=NormalTest test`：通过，manual Profile 生效。
- Profile 属性检查：integration/manual 的包含和排除规则符合设计。

## 面试表达版本

我发现项目的 Maven 测试把单元测试、真实 Redis 集成测试和压测工具混在了一起，导致开发机没启动 Redis 时整个测试门禁都会失败。我先按测试职责增加 integration 和 manual 分层，再用 Maven Profile 保留显式执行入口。同时把评价服务从 `@SpringBootTest` 改成纯 Mockito 单测，保留了 7 个业务场景。最终 `mvn clean test` 从 17 个环境错误恢复为 64 个测试全部通过，而且真实集成测试和压测能力都没有被删除。
