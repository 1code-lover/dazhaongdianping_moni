# 评价服务测试编译失败

## 问题现象

执行 `mvn -q test` 时，`ReviewServiceTest` 在测试编译阶段失败，7 个断言均提示 `Result` 不存在 `isSuccess()` 方法，导致后续测试无法执行。

## 排查过程

1. 检查 `src/test/java/com/hmdp/service/ReviewServiceTest.java`，确认有 7 处调用 `result.isSuccess()`。
2. 检查 `src/main/java/com/hmdp/dto/Result.java`，确认成功状态访问器为 `getSuccess()`，不存在 `isSuccess()`。
3. 对照项目其他测试的调用方式，确定测试应使用现有 DTO API，而不是为单个测试额外扩展生产代码。
4. 将 7 处断言定点修改为 `result.getSuccess()`。
5. 执行 `mvn -q -DskipTests test`，测试源码编译通过。
6. 再次执行 `mvn -q test`，测试已经进入执行阶段；当前剩余失败主要由本机 `127.0.0.1:6379` Redis 未启动引起，不再存在本次测试编译错误。

## 根因

测试代码使用了与 `Result` 实际 JavaBean 访问器不一致的方法名。`success` 字段类型为 `Boolean`，当前 DTO 明确定义的是 `getSuccess()`，但新增评价测试误写为 `isSuccess()`。

## 解决方案

仅修改 `ReviewServiceTest` 中 7 处断言，将 `result.isSuccess()` 替换为 `result.getSuccess()`。该方案不改变生产接口和序列化行为，影响范围最小，也与项目其他测试保持一致。

## 验证结果

- `mvn -q -DskipTests test`：通过，证明主代码和测试代码均可编译。
- `mvn -q test`：测试编译通过并进入执行阶段；完整测试因本地 Redis 连接被拒绝而失败，属于环境依赖问题。

## 关键收获

通过优先修正测试与现有 DTO 契约的不一致，以最小改动恢复测试编译链路，并将编译缺陷与外部中间件环境故障分层定位。
