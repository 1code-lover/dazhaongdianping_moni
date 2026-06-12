# 项目开发规范

## 开发流程

每个功能开发必须遵循以下完整流程，确保代码质量和可追溯性。

```
开发方案 → 审核 → 实施方案 → 审核 → 编写代码 → 测试方案 → 审核 → 执行测试 → 测试报告 → 审核 → 提交推送
```

---

## 步骤详解

### Step 1: 开发方案
- 功能需求分析
- 业务流程设计
- 数据模型设计
- 接口设计

**输出：** `docs/design/{date}-{feature}-requirements.md`
**等待审核通过后进入下一步**

### Step 2: 实施方案
- 文件结构规划
- Task拆分（含具体步骤）
- 代码实现细节
- 单元测试计划

**输出：** `docs/superpowers/plans/{date}-{feature}-plan.md`
**等待审核通过后进入下一步**

### Step 3: 编写代码
- 遵循现有代码规范
- 添加中文注释（文件头 + 函数注释）
- 编译验证通过

### Step 4: 测试方案
- 单元测试计划
- 接口测试计划
- 异常场景测试计划

**输出：** `docs/reports/{date}-{feature}-test-plan.md`
**等待审核通过后进入下一步**

### Step 5: 执行测试
- 运行单元测试
- 执行接口测试
- 验证业务流程

### Step 6: 测试报告
- 测试用例与结果
- 覆盖率统计
- 缺陷记录
- 测试结论

**输出：** `docs/reports/{date}-{feature}-test-report.md`
**等待审核通过后进入下一步**

### Step 7: 提交推送
- 语义化提交信息
- 推送到远程仓库

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
public Long createOrder(OrderDTO orderDTO) {
    ...
}
```

### 行内注释（复杂逻辑必须）

```java
// 分布式锁防止超卖
boolean locked = tryLock("order:" + comboId);
if (!locked) {
    throw new BusinessException("请稍后重试");
}
```

### 测试代码注释

```java
@Test
void testCreateOrder() {
    // given: 准备测试数据
    OrderDTO dto = new OrderDTO();
    dto.setComboId(1L);
    
    // when: 执行测试
    Long orderId = orderService.createOrder(dto);
    
    // then: 验证结果
    assertNotNull(orderId);
}
```

---

## 提交信息格式

```
feat: 添加xxx功能
fix: 修复xxx问题
test: 添加xxx测试
docs: 更新xxx文档
```
