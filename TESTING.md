# 测试规范

> 本文档是 [AGENTS.md](AGENTS.md) 测试章节的详细版本。

---

## 单元测试要求

- 新功能必须编写单元测试
- 关键业务逻辑覆盖率 ≥ 80%
- Service 层和 Util 层测试为必须

---

## 测试命名规范

```
test{方法名}{场景}{预期结果}()
```

示例：

```java
@Test
void testSeckillVoucherWhenStockEnoughThenSuccess() {
    // given: 准备测试数据
    Long voucherId = 1L;
    
    // when: 执行测试
    Result result = voucherOrderService.seckillVoucher(voucherId);
    
    // then: 验证结果
    assertTrue(result.isSuccess());
}

@Test
void testSeckillVoucherWhenStockEmptyThenFail() {
    // given: 准备测试数据（库存为空）
    Long voucherId = 2L;
    
    // when: 执行测试
    Result result = voucherOrderService.seckillVoucher(voucherId);
    
    // then: 验证结果
    assertFalse(result.isSuccess());
}
```

---

## Mock 策略

| 场景 | 方案 |
|------|------|
| Service 层单测 | Mock 数据库和外部依赖 |
| 集成测试 | 真实数据库或 H2 内存数据库 |
| Redis 操作 | Embedded Redis 或 Mock |
| 秒杀链路 | Kafka MockProducer |

---

## 运行测试

```bash
# 运行所有测试
mvn clean test

# 运行特定测试类
mvn "-Dtest=VoucherOrderServiceImplTest" test

# 运行特定测试方法
mvn "-Dtest=VoucherOrderServiceImplTest#testSeckillVoucher" test
```

---

## 查看覆盖率

```bash
mvn clean test jacoco:report
# 查看 target/site/jacoco/index.html
```
