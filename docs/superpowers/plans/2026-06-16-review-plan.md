# 评价系统 - 实施方案

> **For agentic workers:** Use superpowers:subagent-driven-development to implement this plan task-by-task.

**Goal:** 实现评价系统，支持用户发表评价、商家回复、评价列表展示

**Tech Stack:** Spring Boot, MyBatis-Plus, MySQL, Redis

---

## 文件结构

```
src/main/java/com/hmdp/
├── entity/
│   └── Review.java                    # 评价实体
├── dto/
│   ├── ReviewDTO.java                 # 评价请求DTO
│   └── ReviewReplyDTO.java            # 商家回复DTO
├── mapper/
│   └── ReviewMapper.java              # 评价Mapper
├── service/
│   ├── IReviewService.java            # 评价服务接口
│   └── impl/
│       └── ReviewServiceImpl.java     # 评价服务实现
└── controller/
    ├── ReviewController.java          # 用户端评价控制器
    └── ShopReviewController.java      # 商家端评价控制器

src/main/resources/db/
└── review_tables.sql                  # 评价表SQL脚本

src/test/java/com/hmdp/service/
└── ReviewServiceTest.java             # 评价服务单元测试
```

---

## Task 1: 数据库表设计

**Files:**
- Create: `src/main/resources/db/review_tables.sql`

- [ ] **Step 1: 创建评价表**

```sql
CREATE TABLE tb_review (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL COMMENT '用户ID',
    shop_id BIGINT NOT NULL COMMENT '商户ID',
    order_id BIGINT NOT NULL COMMENT '订单ID',
    order_type TINYINT NOT NULL COMMENT '订单类型：1优惠券 2套餐',
    score INT NOT NULL COMMENT '评分：1-5星',
    content TEXT COMMENT '评价内容',
    images VARCHAR(1000) COMMENT '评价图片，多个用逗号分隔',
    reply TEXT COMMENT '商家回复',
    reply_time DATETIME DEFAULT NULL COMMENT '商家回复时间',
    status TINYINT DEFAULT 1 COMMENT '状态：0隐藏 1显示',
    is_deleted TINYINT(1) DEFAULT 0 COMMENT '逻辑删除：0未删除 1已删除',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_order_id (order_id),
    INDEX idx_shop_id (shop_id),
    INDEX idx_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='评价表';
```

- [ ] **Step 2: 扩展商户表字段**

```sql
ALTER TABLE tb_shop ADD COLUMN avg_score DECIMAL(2,1) DEFAULT 0 COMMENT '平均评分';
ALTER TABLE tb_shop ADD COLUMN review_count INT DEFAULT 0 COMMENT '评价数量';
```

---

## Task 2: 实体和DTO

**Files:**
- Create: `src/main/java/com/hmdp/entity/Review.java`
- Create: `src/main/java/com/hmdp/dto/ReviewDTO.java`
- Create: `src/main/java/com/hmdp/dto/ReviewReplyDTO.java`

- [ ] **Step 1: 创建Review实体**

```java
/**
 * 评价实体类
 * 对应数据库表：tb_review
 */
@Data
@TableName("tb_review")
public class Review implements Serializable {
    private static final long serialVersionUID = 1L;
    
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    
    /** 用户ID */
    private Long userId;
    
    /** 商户ID */
    private Long shopId;
    
    /** 订单ID */
    private Long orderId;
    
    /** 订单类型：1优惠券 2套餐 */
    private Integer orderType;
    
    /** 评分：1-5星 */
    private Integer score;
    
    /** 评价内容 */
    private String content;
    
    /** 评价图片，多个用逗号分隔 */
    private String images;
    
    /** 商家回复 */
    private String reply;
    
    /** 商家回复时间 */
    private LocalDateTime replyTime;
    
    /** 状态：0隐藏 1显示 */
    private Integer status;
    
    /** 逻辑删除：0未删除 1已删除 */
    private Integer isDeleted;
    
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
```

- [ ] **Step 2: 创建ReviewDTO**

```java
/**
 * 评价请求DTO
 */
@Data
public class ReviewDTO {
    /** 订单ID */
    private Long orderId;
    
    /** 订单类型：1优惠券 2套餐 */
    private Integer orderType;
    
    /** 评分：1-5星 */
    private Integer score;
    
    /** 评价内容（不超过500字） */
    private String content;
    
    /** 评价图片（最多9张，逗号分隔） */
    private String images;
}
```

- [ ] **Step 3: 创建ReviewReplyDTO**

```java
/**
 * 商家回复DTO
 */
@Data
public class ReviewReplyDTO {
    /** 评价ID */
    private Long reviewId;
    
    /** 回复内容（不超过200字） */
    private String reply;
}
```

---

## Task 3: Mapper层

**Files:**
- Create: `src/main/java/com/hmdp/mapper/ReviewMapper.java`

- [ ] **Step 1: 创建ReviewMapper**

```java
@Mapper
public interface ReviewMapper extends BaseMapper<Review> {
}
```

---

## Task 4: Service层

**Files:**
- Create: `src/main/java/com/hmdp/service/IReviewService.java`
- Create: `src/main/java/com/hmdp/service/impl/ReviewServiceImpl.java`

- [ ] **Step 1: 创建服务接口**

```java
public interface IReviewService extends IService<Review> {
    Result submitReview(ReviewDTO reviewDTO);
    Result getReviewById(Long id);
    Result getMyReviews(Integer current, Integer size);
    Result getShopReviews(Long shopId, Integer current, Integer size);
    Result getShopReviewList(Integer current, Integer size);
    Result replyReview(ReviewReplyDTO replyDTO);
}
```

- [ ] **Step 2: 实现submitReview方法**

```java
@Override
@Transactional
public Result submitReview(ReviewDTO reviewDTO) {
    // 1. 校验订单是否存在且已核销
    Order order = orderMapper.selectById(reviewDTO.getOrderId());
    if (order == null) {
        return Result.fail("订单不存在");
    }
    if (order.getStatus() != 2) {
        return Result.fail("订单未核销，无法评价");
    }
    
    // 2. 校验是否已评价
    Long count = lambdaQuery()
            .eq(Review::getOrderId, reviewDTO.getOrderId())
            .eq(Review::getIsDeleted, 0)
            .count();
    if (count > 0) {
        return Result.fail("该订单已评价");
    }
    
    // 3. 校验评分范围
    if (reviewDTO.getScore() < 1 || reviewDTO.getScore() > 5) {
        return Result.fail("评分范围为1-5");
    }
    
    // 4. 校验内容长度
    if (StrUtil.isNotBlank(reviewDTO.getContent()) && reviewDTO.getContent().length() > 500) {
        return Result.fail("评价内容不超过500字");
    }
    
    // 5. 校验图片数量
    if (StrUtil.isNotBlank(reviewDTO.getImages())) {
        String[] images = reviewDTO.getImages().split(",");
        if (images.length > 9) {
            return Result.fail("图片最多9张");
        }
    }
    
    // 6. 保存评价
    Review review = new Review();
    review.setUserId(UserHolder.getUser().getId());
    review.setShopId(order.getShopId());
    review.setOrderId(reviewDTO.getOrderId());
    review.setOrderType(reviewDTO.getOrderType());
    review.setScore(reviewDTO.getScore());
    review.setContent(reviewDTO.getContent());
    review.setImages(reviewDTO.getImages());
    review.setStatus(1);
    review.setIsDeleted(0);
    review.setCreateTime(LocalDateTime.now());
    review.setUpdateTime(LocalDateTime.now());
    save(review);
    
    // 7. 更新商户评分（SQL原子更新）
    updateShopScore(order.getShopId());
    
    return Result.ok(review.getId());
}
```

- [ ] **Step 3: 实现getReviewById方法**

```java
@Override
public Result getReviewById(Long id) {
    Review review = getById(id);
    if (review == null || review.getIsDeleted() == 1) {
        return Result.fail("评价不存在");
    }
    return Result.ok(review);
}
```

- [ ] **Step 4: 实现getMyReviews方法**

```java
@Override
public Result getMyReviews(Integer current, Integer size) {
    Long userId = UserHolder.getUser().getId();
    Page<Review> page = lambdaQuery()
            .eq(Review::getUserId, userId)
            .eq(Review::getIsDeleted, 0)
            .orderByDesc(Review::getCreateTime)
            .page(new Page<>(current, size));
    return Result.ok(page.getRecords());
}
```

- [ ] **Step 5: 实现getShopReviews方法**

```java
@Override
public Result getShopReviews(Long shopId, Integer current, Integer size) {
    Page<Review> page = lambdaQuery()
            .eq(Review::getShopId, shopId)
            .eq(Review::getIsDeleted, 0)
            .eq(Review::getStatus, 1)
            .orderByDesc(Review::getCreateTime)
            .page(new Page<>(current, size));
    return Result.ok(page.getRecords());
}
```

- [ ] **Step 6: 实现getShopReviewList方法**

```java
@Override
public Result getShopReviewList(Integer current, Integer size) {
    // 获取当前商家的店铺ID
    Long shopId = getCurrentShopId();
    if (shopId == null) {
        return Result.fail("您还没有入驻店铺");
    }
    
    Page<Review> page = lambdaQuery()
            .eq(Review::getShopId, shopId)
            .eq(Review::getIsDeleted, 0)
            .orderByDesc(Review::getCreateTime)
            .page(new Page<>(current, size));
    return Result.ok(page.getRecords());
}
```

- [ ] **Step 7: 实现replyReview方法**

```java
@Override
@Transactional
public Result replyReview(ReviewReplyDTO replyDTO) {
    // 1. 校验评价是否存在
    Review review = getById(replyDTO.getReviewId());
    if (review == null || review.getIsDeleted() == 1) {
        return Result.fail("评价不存在");
    }
    
    // 2. 校验是否已回复
    if (review.getReply() != null) {
        return Result.fail("该评价已回复");
    }
    
    // 3. 校验商家身份（是否是该店铺的商家）
    Long shopId = getCurrentShopId();
    if (shopId == null || !shopId.equals(review.getShopId())) {
        return Result.fail("无权回复该评价");
    }
    
    // 4. 校验回复内容长度
    if (StrUtil.isNotBlank(replyDTO.getReply()) && replyDTO.getReply().length() > 200) {
        return Result.fail("回复内容不超过200字");
    }
    
    // 5. 保存回复
    review.setReply(replyDTO.getReply());
    review.setReplyTime(LocalDateTime.now());
    review.setUpdateTime(LocalDateTime.now());
    updateById(review);
    
    return Result.ok();
}
```

- [ ] **Step 8: 实现updateShopScore方法（私有）**

```java
/**
 * 更新商户评分
 * 使用SQL原子更新，避免并发问题
 */
private void updateShopScore(Long shopId) {
    // 使用SQL原子更新
    shopMapper.updateShopScore(shopId);
}
```

在ShopMapper中添加：
```java
@Update("UPDATE tb_shop SET avg_score = (SELECT AVG(score) FROM tb_review WHERE shop_id = #{shopId} AND is_deleted = 0), review_count = (SELECT COUNT(*) FROM tb_review WHERE shop_id = #{shopId} AND is_deleted = 0) WHERE id = #{shopId}")
void updateShopScore(@Param("shopId") Long shopId);
```

---

## Task 5: Controller层

**Files:**
- Create: `src/main/java/com/hmdp/controller/ReviewController.java`
- Create: `src/main/java/com/hmdp/controller/ShopReviewController.java`

- [ ] **Step 1: 创建用户端ReviewController**

```java
@RestController
@RequestMapping("/review")
public class ReviewController {
    
    @Resource
    private IReviewService reviewService;
    
    @PostMapping
    public Result submitReview(@RequestBody ReviewDTO reviewDTO) {
        return reviewService.submitReview(reviewDTO);
    }
    
    @GetMapping("/{id}")
    public Result getReviewById(@PathVariable Long id) {
        return reviewService.getReviewById(id);
    }
    
    @GetMapping("/my")
    public Result getMyReviews(
            @RequestParam(value = "current", defaultValue = "1") Integer current,
            @RequestParam(value = "size", defaultValue = "10") Integer size) {
        return reviewService.getMyReviews(current, size);
    }
    
    @GetMapping("/shop/{shopId}")
    public Result getShopReviews(
            @PathVariable Long shopId,
            @RequestParam(value = "current", defaultValue = "1") Integer current,
            @RequestParam(value = "size", defaultValue = "10") Integer size) {
        return reviewService.getShopReviews(shopId, current, size);
    }
}
```

- [ ] **Step 2: 创建商家端ShopReviewController**

```java
@RestController
@RequestMapping("/shop/review")
public class ShopReviewController {
    
    @Resource
    private IReviewService reviewService;
    
    @GetMapping("/list")
    public Result getShopReviewList(
            @RequestParam(value = "current", defaultValue = "1") Integer current,
            @RequestParam(value = "size", defaultValue = "10") Integer size) {
        return reviewService.getShopReviewList(current, size);
    }
    
    @PutMapping("/reply")
    public Result replyReview(@RequestBody ReviewReplyDTO replyDTO) {
        return reviewService.replyReview(replyDTO);
    }
}
```

---

## Task 6: 单元测试

**Files:**
- Create: `src/test/java/com/hmdp/service/ReviewServiceTest.java`

- [ ] **Step 1: 测试submitReview**

测试用例：
- 正常提交评价
- 重复评价返回失败
- 评分超出范围返回失败
- 未核销订单评价返回失败
- 内容超过500字返回失败
- 图片超过9张返回失败

- [ ] **Step 2: 测试replyReview**

测试用例：
- 正常回复评价
- 重复回复返回失败
- 评价不存在返回失败
- 非本店商家回复返回失败
- 回复内容超过200字返回失败

- [ ] **Step 3: 测试getShopReviews**

测试用例：
- 分页查询正常
- 按时间倒序

- [ ] **Step 4: 测试getShopReviewList**

测试用例：
- 商家查看评价列表正常
- 无店铺时返回失败

---

## 实施顺序

1. 执行数据库脚本
2. 创建实体和DTO
3. 创建Mapper
4. 创建Service接口和实现
5. 创建Controller
6. 编写单元测试
7. 运行测试验证

---

## 注意事项

1. **最小开发原则**：每个函数只负责一个小功能
2. **中文注释**：文件头注释和函数注释必须
3. **事务管理**：提交评价和更新商户评分需要事务
4. **并发安全**：更新商户评分需要考虑并发问题
