# 评价系统 - 需求分析

## 文档信息

| 项目 | 内容 |
|------|------|
| 文档标题 | 评价系统需求分析 |
| 创建日期 | 2026-06-16 |
| 版本 | v1.0 |

---

## 一、业务背景

用户完成订单（优惠券核销或套餐核销）后，可以对商户进行评价，帮助其他用户了解商户服务质量。

### 1.1 现状分析

当前系统缺少：
- 用户无法对已消费的商户进行评价
- 商家无法查看和回复用户评价
- 其他用户无法参考评价信息

### 1.2 目标

1. 用户可对已核销订单发表评价（文字+图片+评分）
2. 商家可回复用户评价
3. 其他用户可查看商户评价列表
4. 商户详情页展示评分和评价数量

---

## 二、功能需求

### 2.1 用户端功能

| 功能 | 说明 |
|------|------|
| 发表评价 | 订单完成后，用户可发表文字+图片+评分评价 |
| 查看我的评价 | 用户可查看自己发表的所有评价 |
| 查看商户评价 | 用户可查看商户的评价列表 |

### 2.2 商家端功能

| 功能 | 说明 |
|------|------|
| 查看评价列表 | 商家可查看自己店铺的所有评价 |
| 回复评价 | 商家可对用户评价进行回复 |

### 2.3 展示功能

| 功能 | 说明 |
|------|------|
| 商户评分 | 商户详情页展示平均评分 |
| 评价数量 | 商户详情页展示评价总数 |
| 评价列表 | 按时间倒序展示评价 |

---

## 三、数据模型设计

### 3.1 评价表（tb_review）

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
    UNIQUE KEY uk_order_id (order_id) COMMENT '每个订单只能评价一次',
    INDEX idx_shop_id (shop_id),
    INDEX idx_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='评价表';
```

### 3.2 字段说明

| 字段 | 类型 | 说明 |
|------|------|------|
| user_id | BIGINT | 评价用户 |
| shop_id | BIGINT | 被评价商户 |
| order_id | BIGINT | 关联订单（唯一约束） |
| order_type | TINYINT | 1=优惠券订单，2=套餐订单 |
| score | INT | 1-5星评分 |
| content | TEXT | 评价文字内容 |
| images | VARCHAR(1000) | 图片URL，逗号分隔 |
| reply | TEXT | 商家回复内容 |
| reply_time | DATETIME | 商家回复时间 |
| status | TINYINT | 0=隐藏，1=显示 |
| is_deleted | TINYINT(1) | 0=未删除，1=已删除 |

### 3.3 商户表扩展字段

```sql
-- 在tb_shop表中添加评分相关字段
ALTER TABLE tb_shop ADD COLUMN avg_score DECIMAL(2,1) DEFAULT 0 COMMENT '平均评分';
ALTER TABLE tb_shop ADD COLUMN review_count INT DEFAULT 0 COMMENT '评价数量';
```

**存储策略**：评价提交时同步更新 `tb_shop` 的 `avg_score` 和 `review_count`。

---

## 四、接口设计

### 4.1 用户端接口

| 接口 | 方法 | 说明 |
|------|------|------|
| `/review` | POST | 发表评价 |
| `/review/{id}` | GET | 查看单条评价详情 |
| `/review/my` | GET | 我的评价列表 |
| `/review/shop/{shopId}` | GET | 商户评价列表 |

### 4.2 商家端接口

| 接口 | 方法 | 说明 |
|------|------|------|
| `/shop/review/list` | GET | 商家查看评价列表 |
| `/shop/review/reply` | PUT | 商家回复评价 |

### 4.3 接口详情

#### 发表评价

**POST /review**

请求体：
```json
{
  "orderId": 123,
  "orderType": 2,
  "score": 5,
  "content": "非常好吃，服务态度很好！",
  "images": "http://xxx.com/1.jpg,http://xxx.com/2.jpg"
}
```

响应：
```json
{
  "success": true,
  "data": 1
}
```

#### 商户评价列表

**GET /review/shop/{shopId}?current=1&size=10**

响应：
```json
{
  "success": true,
  "data": [
    {
      "id": 1,
      "userId": 1001,
      "userName": "张三",
      "score": 5,
      "content": "非常好吃",
      "images": ["http://xxx.com/1.jpg"],
      "reply": "感谢您的好评",
      "createTime": "2026-06-16 10:00:00"
    }
  ]
}
```

#### 商家回复评价

**PUT /shop/review/reply**

请求体：
```json
{
  "reviewId": 1,
  "reply": "感谢您的好评，欢迎下次光临！"
}
```

响应：
```json
{
  "success": true
}
```

#### 查看单条评价

**GET /review/{id}**

响应：
```json
{
  "success": true,
  "data": {
    "id": 1,
    "userId": 1001,
    "userName": "张三",
    "shopId": 100,
    "orderId": 123,
    "orderType": 2,
    "score": 5,
    "content": "非常好吃",
    "images": ["http://xxx.com/1.jpg"],
    "reply": "感谢您的好评",
    "replyTime": "2026-06-16 12:00:00",
    "createTime": "2026-06-16 10:00:00"
  }
}
```

---

## 五、业务规则

### 5.1 发表评价规则

1. 只有已完成的订单才能评价（状态为已核销）
2. 每个订单只能评价一次
3. 评分范围 1-5 星
4. 评价内容不超过 500 字
5. 图片最多 9 张

### 5.2 商家回复规则

1. 每条评价只能回复一次
2. 回复内容不超过 200 字

### 5.3 评分计算

1. 商户平均评分 = 所有评价评分之和 / 评价数量
2. 评分保留 1 位小数

---

## 六、验收标准

1. 用户可对已核销订单发表评价
2. 每个订单只能评价一次（唯一约束）
3. 评价列表按时间倒序展示
4. 商家可回复用户评价
5. 商户详情页展示平均评分和评价数量
6. 重复评价返回错误提示
7. 未核销订单不能评价
8. 商家回复后记录回复时间

---

## 七、优先级

| 功能 | 优先级 | 说明 |
|------|--------|------|
| 发表评价 | P0 | 核心功能 |
| 商户评价列表 | P0 | 核心功能 |
| 商家回复 | P1 | 重要功能 |
| 我的评价列表 | P1 | 重要功能 |
| 图片上传 | P1 | 重要功能 |
