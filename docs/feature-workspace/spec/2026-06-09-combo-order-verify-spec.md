# 本地生活平台 - 需求分析与功能计划

## 文档信息

| 项目 | 内容 |
|------|------|
| 文档标题 | 套餐、订单、核销功能需求分析与实现计划 |
| 创建日期 | 2026-06-09 |
| 版本 | v1.0 |
| 优先级 | P0 - 核心功能 |

---

## 一、现有系统分析

### 1.1 已有功能

| 模块 | 功能 | 数据表 | 状态 |
|------|------|--------|------|
| 商户 | CRUD、搜索、缓存 | tb_shop | ✅ |
| 优惠券 | 发布、领取、秒杀 | tb_voucher, tb_voucher_order, tb_seckill_voucher | ✅ |
| 用户 | 登录、签到、信息 | tb_user, tb_user_info | ✅ |
| 博客 | 发布、点赞、Feed | tb_blog, tb_blog_comments | ✅ |
| 关注 | 关注/取关 | tb_follow | ✅ |

### 1.2 缺失功能

| 功能 | 说明 | 优先级 |
|------|------|--------|
| 套餐模块 | 多项目组合销售 | P0 |
| 统一订单 | 统一订单管理和状态流转 | P0 |
| 核销功能 | 商家验证用户订单 | P0 |
| 商家入驻 | 商家申请入驻审核 | P1 |
| 评价系统 | 用户评价商户 | P1 |
| 数据统计 | 商家数据看板 | P2 |

---

## 二、需求分析

### 2.1 套餐模块需求

#### 业务需求
- 商家可以发布套餐（如：双人餐、家庭套餐）
- 套餐包含多个项目，原价和团购价
- 用户可以浏览、购买套餐
- 套餐有库存管理、有效期控制

#### 功能点

| 功能 | 说明 | 接口 |
|------|------|------|
| 发布套餐 | 商家创建套餐信息 | POST /combo |
| 编辑套餐 | 修改套餐信息 | PUT /combo |
| 上下架 | 控制套餐可见性 | PUT /combo/status |
| 套餐列表 | 按商户查询套餐 | GET /combo/list/{shopId} |
| 套餐详情 | 查看套餐详情 | GET /combo/{id} |
| 删除套餐 | 逻辑删除 | DELETE /combo/{id} |

#### 数据模型

```
tb_combo（套餐表）
├── id: 主键
├── shop_id: 商户ID
├── title: 套餐标题
├── sub_title: 副标题
├── cover: 封面图
├── images: 详情图片（JSON数组）
├── original_price: 原价（分）
├── price: 团购价（分）
├── content: 包含内容描述
├── rules: 使用规则
├── stock: 库存
├── sales: 已售数量
├── status: 状态（0下架 1上架）
├── begin_time: 生效时间
├── end_time: 失效时间
├── create_time: 创建时间
└── update_time: 更新时间
```

---

### 2.2 订单系统需求

#### 业务需求
- 统一管理优惠券订单和套餐订单
- 订单状态流转：待支付 → 待使用 → 已完成/已取消
- 支持退款申请
- 生成唯一核销码

#### 订单状态流转

```
┌─────────┐     支付      ┌─────────┐     核销      ┌─────────┐
│ 待支付   │ ──────────→ │ 待使用   │ ──────────→ │ 已完成   │
└─────────┘              └─────────┘              └─────────┘
     │                        │
     │ 取消/超时              │ 申请退款
     ▼                        ▼
┌─────────┐              ┌─────────┐
│ 已取消   │              │ 退款中   │ ──→ 已退款
└─────────┘              └─────────┘
```

#### 功能点

| 功能 | 说明 | 接口 |
|------|------|------|
| 创建订单 | 下单购买 | POST /order |
| 支付订单 | 模拟支付 | POST /order/pay |
| 取消订单 | 用户取消 | POST /order/cancel |
| 订单列表 | 我的订单 | GET /order/list |
| 订单详情 | 查看详情 | GET /order/{id} |
| 申请退款 | 提交退款 | POST /order/refund |
| 核销订单 | 商家核销 | POST /order/verify |

#### 数据模型

```
tb_order（统一订单表）
├── id: 主键（雪花算法）
├── order_no: 订单号
├── user_id: 用户ID
├── shop_id: 商户ID
├── order_type: 订单类型（1优惠券 2套餐）
├── biz_id: 业务ID（优惠券ID或套餐ID）
├── title: 商品标题
├── amount: 支付金额（分）
├── quantity: 数量
├── status: 订单状态（0待支付 1待使用 2已核销 3已取消 4退款中 5已退款）
├── verify_code: 核销码（6位数字）
├── verify_time: 核销时间
├── pay_time: 支付时间
├── cancel_time: 取消时间
├── refund_time: 退款时间
├── create_time: 创建时间
└── update_time: 更新时间
```

---

### 2.3 核销功能需求

#### 业务需求
- 用户购买后生成唯一核销码
- 商家可以通过输入核销码或扫码进行核销
- 核销后订单状态变为已完成
- 记录核销日志

#### 功能点

| 功能 | 说明 | 接口 |
|------|------|------|
| 核销订单 | 输入核销码验证 | POST /verify |
| 核销记录 | 商家核销历史 | GET /verify/record |
| 验证核销码 | 检查有效性 | GET /verify/check/{code} |

#### 核销流程

```
1. 用户出示核销码（6位数字）
2. 商家输入核销码
3. 系统验证核销码有效性
4. 验证订单状态是否为"待使用"
5. 验证订单是否过期
6. 更新订单状态为"已核销"
7. 记录核销日志
8. 返回核销成功
```

---

## 三、功能实现计划

### 3.1 第一阶段：套餐模块（2天）

#### Task 1: 数据库设计
- [ ] 创建 tb_combo 表
- [ ] 插入测试数据

#### Task 2: 实体类和Mapper
- [ ] 创建 Combo 实体类
- [ ] 创建 ComboMapper 接口

#### Task 3: Service层
- [ ] IComboService 接口
- [ ] ComboServiceImpl 实现

#### Task 4: Controller层
- [ ] ComboController（商家端）
- [ ] ComboController（用户端）

#### Task 5: 测试
- [ ] 单元测试
- [ ] 接口测试

---

### 3.2 第二阶段：订单系统（3天）

#### Task 1: 数据库设计
- [ ] 创建 tb_order 统一订单表
- [ ] 创建索引

#### Task 2: 实体类和Mapper
- [ ] 创建 Order 实体类
- [ ] 创建 OrderMapper 接口

#### Task 3: Service层
- [ ] IOrderService 接口
- [ ] OrderServiceImpl 实现
- [ ] 订单号生成工具
- [ ] 核销码生成工具

#### Task 4: Controller层
- [ ] OrderController（用户端）
- [ ] OrderController（商家端）

#### Task 5: 业务逻辑
- [ ] 下单流程（库存扣减）
- [ ] 支付流程（模拟支付）
- [ ] 取消流程（库存回滚）
- [ ] 超时自动取消（延迟队列）

#### Task 6: 测试
- [ ] 单元测试
- [ ] 并发测试

---

### 3.3 第三阶段：核销功能（1天）

#### Task 1: Service层
- [ ] IVerifyService 接口
- [ ] VerifyServiceImpl 实现

#### Task 2: Controller层
- [ ] VerifyController

#### Task 3: 业务逻辑
- [ ] 核销码验证
- [ ] 订单状态更新
- [ ] 核销日志记录

#### Task 4: 测试
- [ ] 核销流程测试
- [ ] 异常场景测试

---

## 四、接口详细设计

### 4.1 套餐接口

#### 发布套餐
```
POST /combo
Authorization: Bearer {token}

Request:
{
    "shopId": 1,
    "title": "双人超值套餐",
    "subTitle": "适合2-3人享用",
    "cover": "https://xxx.com/cover.jpg",
    "images": ["https://xxx.com/1.jpg", "https://xxx.com/2.jpg"],
    "originalPrice": 29800,
    "price": 16800,
    "content": "1. 招牌烤鱼 x1\n2. 凉菜 x2\n3. 饮品 x2",
    "rules": "1. 每桌限用1张\n2. 需提前预约\n3. 不与其他优惠同享",
    "stock": 100,
    "beginTime": "2026-06-01 00:00:00",
    "endTime": "2026-12-31 23:59:59"
}

Response:
{
    "success": true,
    "data": 1
}
```

#### 套餐列表
```
GET /combo/list/{shopId}?current=1&size=10

Response:
{
    "success": true,
    "data": [
        {
            "id": 1,
            "title": "双人超值套餐",
            "originalPrice": 29800,
            "price": 16800,
            "sales": 50,
            "cover": "https://xxx.com/cover.jpg"
        }
    ]
}
```

---

### 4.2 订单接口

#### 创建订单
```
POST /order
Authorization: Bearer {token}

Request:
{
    "orderType": 2,
    "bizId": 1,
    "quantity": 1
}

Response:
{
    "success": true,
    "data": {
        "orderId": 1234567890,
        "orderNo": "ORD20260609001",
        "amount": 16800,
        "verifyCode": "123456"
    }
}
```

#### 订单列表
```
GET /order/list?status=1&current=1&size=10
Authorization: Bearer {token}

Response:
{
    "success": true,
    "data": [
        {
            "id": 1,
            "orderNo": "ORD20260609001",
            "orderType": 2,
            "title": "双人超值套餐",
            "amount": 16800,
            "status": 1,
            "statusDesc": "待使用",
            "verifyCode": "123456",
            "shopName": "103茶餐厅",
            "createTime": "2026-06-09 10:00:00"
        }
    ]
}
```

---

### 4.3 核销接口

#### 核销订单
```
POST /verify
Authorization: Bearer {token}

Request:
{
    "verifyCode": "123456"
}

Response:
{
    "success": true,
    "data": {
        "orderId": 1,
        "orderNo": "ORD20260609001",
        "title": "双人超值套餐",
        "amount": 16800,
        "userName": "user_xxx",
        "verifyTime": "2026-06-09 15:00:00"
    }
}
```

---

## 五、数据库设计

### 5.1 套餐表

```sql
CREATE TABLE `tb_combo` (
    `id` bigint(20) NOT NULL AUTO_INCREMENT,
    `shop_id` bigint(20) NOT NULL COMMENT '商户ID',
    `title` varchar(100) NOT NULL COMMENT '套餐标题',
    `sub_title` varchar(200) DEFAULT NULL COMMENT '副标题',
    `cover` varchar(500) DEFAULT NULL COMMENT '封面图',
    `images` varchar(2000) DEFAULT NULL COMMENT '详情图片JSON',
    `original_price` bigint(20) NOT NULL COMMENT '原价（分）',
    `price` bigint(20) NOT NULL COMMENT '团购价（分）',
    `content` text COMMENT '包含内容',
    `rules` text COMMENT '使用规则',
    `stock` int(11) NOT NULL DEFAULT 0 COMMENT '库存',
    `sales` int(11) DEFAULT 0 COMMENT '已售数量',
    `status` tinyint(1) DEFAULT 1 COMMENT '状态：0下架 1上架',
    `is_deleted` tinyint(1) DEFAULT 0 COMMENT '逻辑删除：0未删除 1已删除',
    `begin_time` datetime DEFAULT NULL COMMENT '生效时间',
    `end_time` datetime DEFAULT NULL COMMENT '失效时间',
    `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
    `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_shop_id` (`shop_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='套餐表';
```

### 5.2 统一订单表

```sql
CREATE TABLE `tb_order` (
    `id` bigint(20) NOT NULL,
    `order_no` varchar(32) NOT NULL COMMENT '订单号',
    `user_id` bigint(20) NOT NULL COMMENT '用户ID',
    `shop_id` bigint(20) NOT NULL COMMENT '商户ID',
    `order_type` tinyint(1) NOT NULL COMMENT '订单类型：1优惠券 2套餐',
    `biz_id` bigint(20) NOT NULL COMMENT '业务ID（优惠券ID或套餐ID）',
    `title` varchar(100) DEFAULT NULL COMMENT '商品标题',
    `amount` bigint(20) NOT NULL COMMENT '支付金额（分）',
    `quantity` int(11) DEFAULT 1 COMMENT '数量',
    `status` tinyint(1) DEFAULT 0 COMMENT '状态：0待支付 1待使用 2已核销 3已取消 4退款中 5已退款',
    `verify_code` varchar(6) DEFAULT NULL COMMENT '核销码（6位数字）',
    `verify_time` datetime DEFAULT NULL COMMENT '核销时间',
    `pay_time` datetime DEFAULT NULL COMMENT '支付时间',
    `cancel_time` datetime DEFAULT NULL COMMENT '取消时间',
    `refund_time` datetime DEFAULT NULL COMMENT '退款时间',
    `is_deleted` tinyint(1) DEFAULT 0 COMMENT '逻辑删除：0未删除 1已删除',
    `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
    `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_order_no` (`order_no`),
    UNIQUE KEY `uk_verify_code` (`verify_code`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_shop_id` (`shop_id`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='统一订单表';
```

### 5.3 核销记录表

```sql
CREATE TABLE `tb_verify_record` (
    `id` bigint(20) NOT NULL AUTO_INCREMENT,
    `order_id` bigint(20) NOT NULL COMMENT '订单ID',
    `order_no` varchar(32) NOT NULL COMMENT '订单号',
    `user_id` bigint(20) NOT NULL COMMENT '用户ID',
    `shop_id` bigint(20) NOT NULL COMMENT '商户ID',
    `operator_id` bigint(20) NOT NULL COMMENT '操作人ID',
    `remark` varchar(255) DEFAULT NULL COMMENT '备注',
    `verify_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '核销时间',
    PRIMARY KEY (`id`),
    KEY `idx_order_id` (`order_id`),
    KEY `idx_shop_id` (`shop_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='核销记录表';
```

---

## 六、代码结构

### 6.1 新增文件

```
src/main/java/com/hmdp/
├── entity/
│   ├── Combo.java              # 套餐实体
│   └── Order.java              # 订单实体
├── mapper/
│   ├── ComboMapper.java        # 套餐Mapper
│   └── OrderMapper.java        # 订单Mapper
├── service/
│   ├── IComboService.java      # 套餐服务接口
│   ├── IOrderService.java      # 订单服务接口
│   └── IVerifyService.java     # 核销服务接口
├── service/impl/
│   ├── ComboServiceImpl.java   # 套餐服务实现
│   ├── OrderServiceImpl.java   # 订单服务实现
│   └── VerifyServiceImpl.java  # 核销服务实现
├── controller/
│   ├── ComboController.java    # 套餐接口
│   ├── OrderController.java    # 订单接口
│   └── VerifyController.java   # 核销接口
└── utils/
    ├── OrderNoGenerator.java   # 订单号生成器
    └── VerifyCodeGenerator.java # 核销码生成器
```

---

## 七、风险与注意事项

### 7.1 技术风险

| 风险 | 应对措施 |
|------|----------|
| 库存超卖 | Redis Lua原子扣减 |
| 订单重复 | 唯一索引 + 幂等校验 |
| 核销码冲突 | 唯一索引 + 重试机制 |
| 并发核销 | 分布式锁 |

### 7.2 业务风险

| 风险 | 应对措施 |
|------|----------|
| 恶意刷单 | 限流 + 风控 |
| 订单超时未支付 | 延迟队列自动取消 |
| 核销纠纷 | 核销日志记录 |

---

## 八、验收标准

### 8.1 套餐模块
- [ ] 商家可以发布、编辑、上下架套餐
- [ ] 用户可以浏览套餐列表和详情
- [ ] 库存管理正确
- [ ] 有效期控制生效

### 8.2 订单系统
- [ ] 用户可以下单购买
- [ ] 订单状态流转正确
- [ ] 库存扣减/回滚正确
- [ ] 订单查询正常

### 8.3 核销功能
- [ ] 核销码生成唯一
- [ ] 核销流程完整
- [ ] 核销记录可查
- [ ] 异常处理完善
