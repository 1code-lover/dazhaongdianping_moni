# 商家申请单元测试 baseMapper 未注入

## 问题现象

测试源码恢复编译后，`ShopApplyServiceTest` 的 5 个用例全部在业务断言前抛出空指针。异常显示 MyBatis-Plus `ServiceImpl` 的继承字段 `baseMapper` 为 null。

## 排查过程

1. 检查测试类，确认使用 `@InjectMocks` 创建 `ShopApplyServiceImpl`。
2. 检查异常栈，确认失败位置来自 `lambdaQuery()`、`getById()` 和直接访问 `baseMapper`。
3. 确认 Mockito 能注入当前类声明的 Mapper 字段，但没有可靠注入父类 `ServiceImpl` 中的泛型继承字段。
4. 在测试初始化阶段显式设置父类字段，再执行全部商家申请测试。

## 根因

测试夹具错误地假设 `@InjectMocks` 会自动完成 MyBatis-Plus 父类内部字段的注入，导致被测 Service 虽然创建成功，但基础 CRUD 链路不可用。

## 解决方案

在 `ShopApplyServiceTest#setUp` 中使用 Spring 测试工具将 Mock 的 `ShopApplyMapper` 注入 `baseMapper`，保留纯 Mockito 单元测试，不启动完整 Spring 容器。

## 验证结果

- `mvn -q -Dtest=ShopApplyServiceTest test`：通过，5 个用例全部成功。
- 再次执行完整测试后，错误数从 22 降至 17；剩余错误均来自本机 Redis `127.0.0.1:6379` 连接拒绝。

## 关键收获

测试 MyBatis-Plus `ServiceImpl` 子类时，除了业务 Mapper 字段，还要显式处理父类 `baseMapper`，否则链式查询和基础 CRUD 会在测试中空指针。
