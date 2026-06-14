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
chore: 其他改动（配置、依赖等）
refactor: 代码重构
perf: 性能优化
```

---

## 分支管理规范

### 分支命名

- **功能分支**：`feat/{issue-id}-{feature-name}`
  - 例：`feat/123-user-login`
- **修复分支**：`fix/{issue-id}-{bug-name}`
  - 例：`fix/456-cache-bug`
- **文档分支**：`docs/{description}`
  - 例：`docs/api-documentation`
- **重构分支**：`refactor/{description}`
  - 例：`refactor/shop-service`

### 分支保护规则

- `master` 分支受保护，不允许直接推送
- 必须通过 Pull Request 合并
- PR 需要至少 1 人审核通过
- 所有 CI 检查必须通过

### Pull Request 流程

1. 从 `master` 创建功能分支
2. 本地开发完成，编写测试和文档
3. 提交 PR，填写详细描述
4. 等待审核和 CI 检查
5. 审核通过后合并到 `master`
6. 删除功能分支

---

## 代码风格规范

### 命名规范

**类名**（PascalCase）
```java
// ✓ 正确
public class UserService
public class VoucherOrderController
public class CacheClient

// ✗ 错误
public class user_service
public class UserService_Impl
```

**方法名**（camelCase）
```java
// ✓ 正确
public Result queryUserById(Long userId)
public void saveBlog(Blog blog)
public Boolean isFollowing(Long userId, Long followUserId)

// ✗ 错误
public Result query_user_by_id(Long userId)
public void SaveBlog(Blog blog)
```

**常量**（UPPER_SNAKE_CASE）
```java
// ✓ 正确
public static final String LOGIN_USER_KEY = "login:user:";
public static final Integer MAX_PAGE_SIZE = 10;
public static final Long CACHE_EXPIRATION_MINUTES = 30L;

// ✗ 错误
public static final String loginUserKey = "login:user:";
public static final Integer max_page_size = 10;
```

**变量名**（camelCase）
```java
// ✓ 正确
String userPhone;
Integer pageSize;
List<Shop> shopList;
Map<String, Object> resultMap;

// ✗ 错误
String user_phone;
Integer PageSize;
List<Shop> shop_list;
Map<String, Object> result_map;
```

### 代码格式

- **缩进**：使用 4 个空格（不用 Tab）
- **行长**：不超过 120 个字符
- **空行**：方法之间保留 1 个空行，逻辑块之间保留 1 个空行
- **导入包**：按照字母顺序排序，分为三组：
  - 标准库（java.* / javax.*）
  - 第三方库（com.* / org.* / cn.* 等）
  - 项目内部（com.hmdp.*）

```java
// ✓ 正确的导入顺序
import java.util.*;
import javax.annotation.Resource;

import com.alibaba.fastjson.JSON;
import org.springframework.stereotype.Service;

import com.hmdp.dto.Result;
import com.hmdp.service.IUserService;
```

### 集合和泛型

```java
// ✓ 正确
List<String> names = new ArrayList<>();
Map<String, Integer> countMap = new HashMap<>();
Set<Long> userIds = new HashSet<>();

// ✗ 错误
List names = new ArrayList();
Map countMap = new HashMap();
List<String> names = new ArrayList(); // 右侧应该使用 <>
```

---

## 测试规范

### 单元测试要求

- 新功能必须编写单元测试
- 关键业务逻辑覆盖率 ≥ 80%
- Service 层和 Util 层测试为必须

### 测试命名规范

```java
@Test
void test{方法名}{场景}{预期结果}() {
    // given: 准备测试数据
    // when: 执行被测方法
    // then: 验证结果
}
```

例子：
```java
@Test
void testSeckillVoucherWhenStockEnoughThenSuccess() {
    // given
    Long voucherId = 1L;
    
    // when
    Result result = voucherOrderService.seckillVoucher(voucherId);
    
    // then
    assertTrue(result.isSuccess());
}

@Test
void testSeckillVoucherWhenStockEmptyThenFail() {
    // given
    Long voucherId = 2L;
    // 清空库存
    
    // when
    Result result = voucherOrderService.seckillVoucher(voucherId);
    
    // then
    assertFalse(result.isSuccess());
}
```

### Mock 策略

- Service 层单元测试：Mock 数据库和外部依赖
- 集成测试：使用真实数据库或内存数据库（H2）
- Redis 操作：使用 Embedded Redis 或 Mock
- 秒杀链路：使用 Kafka MockProducer

---

## 代码审查清单

提交 PR 前，自检以下项：

- [ ] 代码风格符合规范
- [ ] 添加了必要的中文注释
- [ ] 编写了单元测试
- [ ] 测试覆盖率 ≥ 80%
- [ ] 没有硬编码的魔法值
- [ ] 没有 TODO / FIXME 注释
- [ ] 没有打印 System.out.println
- [ ] 使用了 slf4j 日志框架
- [ ] 数据库查询有性能考虑
- [ ] Redis 操作有异常处理
- [ ] 接口有必要的权限检查

---

## 环境和工具

### 推荐 IDE 和插件

**IntelliJ IDEA**（推荐）
- 插件：
  - Alibaba Java Coding Guidelines（代码规范检查）
  - Save Actions（自动格式化）
  - SonarLint（代码质量分析）
  - Lombok Plugin（Lombok 支持）
  - CheckStyle（代码风格检查）

### 本地开发环境

```bash
# 检查 Java 版本（需要 Java 8）
java -version

# 检查 Maven 版本（需要 3.6+）
mvn -v

# 检查 MySQL 版本（需要 5.7+）
mysql --version

# 检查 Redis 版本（需要 6.x）
redis-cli --version

# 检查 Kafka 版本（需要 2.x+）
# Kafka 通常不需要单独检查，通过配置文件验证
```

### IDE 代码格式化配置

**IntelliJ IDEA 设置**
1. Preferences → Editor → Code Style → Java
2. 设置缩进为 4 个空格
3. 设置行长限制为 120
4. 启用"Optimize imports"
5. 导入 Alibaba 代码规范检查规则

### 调试工具

- **Postman / Reqable**：API 测试
- **Redis Desktop Manager**：Redis 数据查看
- **MySQL Workbench**：数据库管理
- **Kafka Topic UI**：Kafka 消息查看

---

## 常见问题和解决方案

### Q1: 如何快速修复代码风格问题？

```bash
# IntelliJ IDEA 快捷键
# Mac: Cmd + Shift + L (触发代码格式化)
# Windows/Linux: Ctrl + Shift + L

# 或通过菜单：Code → Reformat Code
```

### Q2: 如何检查单元测试覆盖率？

```bash
mvn clean test jacoco:report
# 查看 target/site/jacoco/index.html
```

### Q3: 如何本地运行完整测试套件？

```bash
# 运行所有测试
mvn clean test

# 运行特定测试类
mvn "-Dtest=VoucherOrderServiceImplTest" test

# 运行特定测试方法
mvn "-Dtest=VoucherOrderServiceImplTest#testSeckillVoucher" test
```

### Q4: 代码提交前需要做什么检查？

1. 本地运行所有测试：`mvn clean test`
2. 代码格式化：IDE 中执行格式化
3. 检查拼写和注释
4. 验证 commit message 格式
5. 确保没有遗留的调试代码
