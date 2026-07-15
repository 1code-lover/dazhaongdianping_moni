# 商家入驻 - 需求分析与功能计划

## 文档信息

| 项目 | 内容 |
|------|------|
| 文档标题 | 商家入驻功能需求分析与实现计划 |
| 创建日期 | 2026-06-10 |
| 版本 | v1.0 |
| 优先级 | P1 |

---

## 一、业务背景

### 1.1 现状分析

当前系统中商户数据是直接写入数据库的，没有入驻审核流程。商家入驻功能可以让商家自主申请入驻，经过平台审核后才能发布商品。

### 1.2 业务目标

1. 商家可以提交入驻申请
2. 平台可以审核商家申请
3. 审核通过后商家可以管理店铺
4. 审核拒绝后商家可以重新申请

---

## 二、用户角色

| 角色 | 说明 | 权限 |
|------|------|------|
| 商家 | 想入驻平台的商户 | 提交申请、查看状态、管理店铺 |
| 平台管理员 | 审核商家申请 | 审核申请、查看商家列表 |

---

## 三、功能需求

### 3.1 商家端功能

| 功能 | 说明 | 优先级 |
|------|------|--------|
| 提交入驻申请 | 填写店铺信息、联系方式、营业执照 | P0 |
| 查看申请状态 | 查看审核进度和结果 | P0 |
| 重新申请 | 被拒绝后可以修改并重新提交 | P1 |
| 管理店铺 | 审核通过后管理店铺信息 | P1 |

### 3.2 平台端功能

| 功能 | 说明 | 优先级 |
|------|------|--------|
| 查看申请列表 | 按状态筛选申请 | P0 |
| 审核申请 | 通过或拒绝申请 | P0 |
| 查看申请详情 | 查看商家提交的详细信息 | P0 |

---

## 四、业务流程

### 4.1 入驻申请流程

```
商家登录
    ↓
填写入驻申请
    ↓
提交申请
    ↓
平台审核
    ↓
  ┌────┴────┐
  ↓         ↓
通过       拒绝
  ↓         ↓
开通店铺   通知商家
  ↓         ↓
可发布商品  可重新申请
```

### 4.2 状态流转

```
待审核(0) → 审核通过(1)
    ↓
审核拒绝(2) → 重新申请 → 待审核(0)
```

---

## 五、数据模型

### 5.1 商家入驻申请表

```sql
CREATE TABLE `tb_shop_apply` (
    `id` bigint(20) NOT NULL AUTO_INCREMENT,
    `user_id` bigint(20) NOT NULL COMMENT '申请人用户ID',
    `shop_name` varchar(100) NOT NULL COMMENT '店铺名称',
    `shop_type_id` bigint(20) NOT NULL COMMENT '店铺类型ID',
    `shop_img` varchar(500) DEFAULT NULL COMMENT '店铺封面图/Logo',
    `contact_name` varchar(50) NOT NULL COMMENT '联系人姓名',
    `contact_phone` varchar(20) NOT NULL COMMENT '联系电话',
    `address` varchar(200) NOT NULL COMMENT '店铺地址',
    `longitude` decimal(10,7) DEFAULT NULL COMMENT '经度',
    `latitude` decimal(10,7) DEFAULT NULL COMMENT '纬度',
    `license_no` varchar(50) DEFAULT NULL COMMENT '营业执照号',
    `license_img` varchar(500) DEFAULT NULL COMMENT '营业执照图片',
    `description` text DEFAULT NULL COMMENT '店铺描述',
    `status` tinyint(1) DEFAULT 0 COMMENT '状态：0待审核 1通过 2拒绝',
    `reject_reason` varchar(255) DEFAULT NULL COMMENT '拒绝原因',
    `audit_time` datetime DEFAULT NULL COMMENT '审核时间',
    `auditor_id` bigint(20) DEFAULT NULL COMMENT '审核人ID',
    `is_deleted` tinyint(1) DEFAULT 0 COMMENT '逻辑删除：0未删除 1已删除',
    `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
    `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商家入驻申请表';
```

### 5.2 字段说明

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| user_id | bigint | 是 | 申请人用户ID |
| shop_name | varchar(100) | 是 | 店铺名称 |
| shop_type_id | bigint | 是 | 店铺类型（关联tb_shop_type） |
| contact_name | varchar(50) | 是 | 联系人姓名 |
| contact_phone | varchar(20) | 是 | 联系电话 |
| address | varchar(200) | 是 | 店铺地址 |
| license_no | varchar(50) | 否 | 营业执照号 |
| license_img | varchar(500) | 否 | 营业执照图片URL |
| description | text | 否 | 店铺描述 |
| status | tinyint | 是 | 0待审核 1通过 2拒绝 |
| reject_reason | varchar(255) | 否 | 拒绝原因（拒绝时必填） |

---

## 六、接口设计

### 6.1 商家端接口

#### 提交入驻申请

```
POST /shop/apply
Authorization: Bearer {token}

Request:
{
    "shopName": "好吃餐厅",
    "shopTypeId": 1,
    "contactName": "张三",
    "contactPhone": "13800138000",
    "address": "北京市朝阳区xxx路xxx号",
    "licenseNo": "91110000MA12345678",
    "licenseImg": "https://xxx.com/license.jpg",
    "description": "专注美食20年"
}

Response:
{
    "success": true,
    "data": 1
}
```

#### 查看申请状态

```
GET /shop/apply/status
Authorization: Bearer {token}

Response:
{
    "success": true,
    "data": {
        "id": 1,
        "shopName": "好吃餐厅",
        "status": 0,
        "statusDesc": "待审核",
        "rejectReason": null,
        "createTime": "2026-06-10 10:00:00"
    }
}
```

### 6.2 平台端接口

#### 查看申请列表

```
GET /shop/apply/list?status=0&current=1&size=10
Authorization: Bearer {admin_token}

Response:
{
    "success": true,
    "data": [
        {
            "id": 1,
            "shopName": "好吃餐厅",
            "contactName": "张三",
            "contactPhone": "13800138000",
            "status": 0,
            "createTime": "2026-06-10 10:00:00"
        }
    ]
}
```

#### 审核申请

```
POST /shop/apply/audit
Authorization: Bearer {admin_token}

Request:
{
    "applyId": 1,
    "status": 1,
    "rejectReason": null
}

Response:
{
    "success": true
}
```

---

## 七、实现计划

### 7.1 Task 1: 数据库设计

- [ ] 创建tb_shop_apply表
- [ ] 插入测试数据

### 7.2 Task 2: 创建实体和Mapper

- [ ] 创建ShopApply实体类
- [ ] 创建ShopApplyMapper接口

### 7.3 Task 3: 创建Service

- [ ] 创建IShopApplyService接口
- [ ] 创建ShopApplyServiceImpl实现

### 7.4 Task 4: 创建Controller

- [ ] 创建ShopApplyController（商家端）
- [ ] 创建AdminShopApplyController（平台端）

### 7.5 Task 5: 业务逻辑

- [ ] 提交申请（校验重复申请）
- [ ] 审核通过（创建店铺）
- [ ] 审核拒绝（记录原因）
- [ ] 重新申请

### 7.6 Task 6: 单元测试

- [ ] 测试提交申请
- [ ] 测试审核流程
- [ ] 测试状态流转

---

## 八、验收标准

### 8.1 商家端

- [ ] 可以提交入驻申请
- [ ] 可以查看申请状态
- [ ] 被拒绝后可以重新申请

### 8.2 平台端

- [ ] 可以查看申请列表
- [ ] 可以审核申请
- [ ] 审核通过后自动创建店铺

---

## 九、风险与注意事项

| 风险 | 应对措施 |
|------|----------|
| 重复申请 | 同一用户只能有一个待审核的申请 |
| 审核通过后店铺创建失败 | 事务保证，失败回滚 |
| 营业执照图片存储 | 使用对象存储或本地存储 |
