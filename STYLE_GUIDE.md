# 代码风格规范

> 本文档是 [AGENTS.md](AGENTS.md) 代码风格章节的详细版本。

---

## 命名规范

### 类名（PascalCase）

```java
// ✓ 正确
public class UserService
public class VoucherOrderController
public class CacheClient

// ✗ 错误
public class user_service
public class UserService_Impl
```

### 方法名（camelCase）

```java
// ✓ 正确
public Result queryUserById(Long userId)
public void saveBlog(Blog blog)
public Boolean isFollowing(Long userId, Long followUserId)

// ✗ 错误
public Result query_user_by_id(Long userId)
public void SaveBlog(Blog blog)
```

### 常量（UPPER_SNAKE_CASE）

```java
// ✓ 正确
public static final String LOGIN_USER_KEY = "login:user:";
public static final Integer MAX_PAGE_SIZE = 10;
public static final Long CACHE_EXPIRATION_MINUTES = 30L;

// ✗ 错误
public static final String loginUserKey = "login:user:";
```

### 变量名（camelCase）

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
```

---

## 代码格式

- **缩进**：4 个空格（不用 Tab）
- **行长**：不超过 120 个字符
- **空行**：方法间 1 行，逻辑块间 1 行

---

## 导入包顺序

按字母顺序分三组，组间空行：

```java
// 第一组：标准库
import java.util.*;
import javax.annotation.Resource;

// 第二组：第三方库
import com.alibaba.fastjson.JSON;
import org.springframework.stereotype.Service;

// 第三组：项目内部
import com.hmdp.dto.Result;
import com.hmdp.service.IUserService;
```

---

## 集合和泛型

```java
// ✓ 正确：右侧使用菱形运算符
List<String> names = new ArrayList<>();
Map<String, Integer> countMap = new HashMap<>();
Set<Long> userIds = new HashSet<>();

// ✗ 错误
List names = new ArrayList();
Map countMap = new HashMap();
```

---

## 代码注释

### 文件头（必须）

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
public Long createOrder(OrderDTO orderDTO) { ... }
```

### 行内注释（复杂逻辑必须）

```java
// 分布式锁防止超卖
boolean locked = tryLock("order:" + comboId);
```

---

## 设计原则

### 最小开发原则（单一职责）

每个函数只负责一个小功能，不允许一个函数负责多个功能。

```java
// ✓ 正确：每个函数职责单一
public Long createOrder(OrderDTO orderDTO) {
    validateOrder(orderDTO);
    Long orderId = generateOrderId();
    saveOrder(orderDTO, orderId);
    return orderId;
}
```

### 函数拆分标准

| 维度 | 标准 |
|------|------|
| 行数 | 函数体 ≤ 30 行 |
| 职责 | 只做一件事 |
| 命名 | 能用一句话描述功能 |
| 参数 | ≤ 4 个 |
