# 评价系统 - 测试方案

## 文档信息

| 项目 | 内容 |
|------|------|
| 文档标题 | 评价系统测试方案 |
| 创建日期 | 2026-06-17 |
| 版本 | v1.1 |
| 更新说明 | 根据代码修改更新测试数据准备和商家身份验证逻辑 |

---

## 一、测试目标

验证评价系统所有接口功能正常，包括：
- 用户提交评价
- 用户查看评价详情
- 用户查看我的评价列表
- 用户查看商户评价列表
- 商家查看评价列表
- 商家回复评价

---

## 二、测试环境

| 项目 | 说明 |
|------|------|
| 后端服务 | Spring Boot 2.3.12 |
| 数据库 | MySQL 5.7 |
| 缓存 | Redis 6.x |
| 测试工具 | PowerShell |

---

## 三、前置条件

### 3.1 数据库准备

1. 已执行 `review_tables.sql` 创建评价表
2. 已执行 `shop_apply_tables.sql` 创建商家入驻申请表（含shop_id字段）
3. 已有测试订单数据（状态为已核销）

### 3.2 测试数据

| 数据 | 说明 |
|------|------|
| 用户token | 手机号 13800138001 登录获取 |
| 商家token | 手机号 13800138000 登录获取（已入驻商家） |
| 测试订单 | orderId=1，状态为已核销（status=2） |
| 测试商户 | shopId=15，已审核通过的店铺 |

---

## 四、测试用例

### 4.1 提交评价

| 用例ID | 测试场景 | 输入 | 预期结果 |
|--------|----------|------|----------|
| TC-01 | 正常提交评价 | orderId=已核销订单, score=5, content="好评" | 成功，返回评价ID |
| TC-02 | 重复评价 | orderId=已评价订单 | 失败，"该订单已评价" |
| TC-03 | 评分超出范围 | score=6 | 失败，"评分范围为1-5" |
| TC-04 | 评分低于范围 | score=0 | 失败，"评分范围为1-5" |
| TC-05 | 内容超过500字 | content=501字 | 失败，"评价内容不超过500字" |
| TC-06 | 图片超过9张 | images=10张 | 失败，"图片最多9张" |
| TC-07 | 订单未核销 | orderId=未核销订单 | 失败，"订单未核销，无法评价" |
| TC-08 | 订单不存在 | orderId=不存在 | 失败，"订单不存在" |

### 4.2 查看评价详情

| 用例ID | 测试场景 | 输入 | 预期结果 |
|--------|----------|------|----------|
| TC-09 | 正常查看 | id=存在评价 | 成功，返回评价详情 |
| TC-10 | 评价不存在 | id=不存在 | 失败，"评价不存在" |

### 4.3 我的评价列表

| 用例ID | 测试场景 | 输入 | 预期结果 |
|--------|----------|------|----------|
| TC-11 | 正常查询 | current=1, size=10 | 成功，返回评价列表 |
| TC-12 | 空列表 | 用户无评价 | 成功，返回空列表 |

### 4.4 商户评价列表

| 用例ID | 测试场景 | 输入 | 预期结果 |
|--------|----------|------|----------|
| TC-13 | 正常查询 | shopId=存在商户 | 成功，返回评价列表 |
| TC-14 | 分页查询 | current=2, size=5 | 成功，返回第2页数据 |

### 4.5 商家查看评价列表

| 用例ID | 测试场景 | 输入 | 预期结果 |
|--------|----------|------|----------|
| TC-15 | 正常查询 | current=1, size=10 | 成功，返回评价列表 |
| TC-16 | 无店铺 | 用户未入驻 | 失败，"您还没有入驻店铺" |

### 4.6 商家回复评价

| 用例ID | 测试场景 | 输入 | 预期结果 |
|--------|----------|------|----------|
| TC-17 | 正常回复 | reviewId=存在评价, reply="感谢" | 成功 |
| TC-18 | 重复回复 | reviewId=已回复评价 | 失败，"该评价已回复" |
| TC-19 | 评价不存在 | reviewId=不存在 | 失败，"评价不存在" |
| TC-20 | 回复超长 | reply=201字 | 失败，"回复内容不超过200字" |
| TC-21 | 无权回复 | 非本店商家 | 失败，"无权回复该评价" |

---

## 五、测试步骤

### 5.1 准备测试数据

```powershell
# 1. 获取用户token（手机号 13800138001）
docker exec hmdp-redis redis-cli -a 123456 SET "login:code:13800138001" "123456" EX 300
$headers = @{ "Content-Type" = "application/json" }
$body = '{"phone":"13800138001","code":"123456"}'
$resp = Invoke-WebRequest -Uri "http://localhost:8081/user/login" -Method Post -Headers $headers -Body $body
$userToken = ($resp.Content | ConvertFrom-Json).data

# 2. 获取商家token（手机号 13800138000）
docker exec hmdp-redis redis-cli -a 123456 SET "login:code:13800138000" "123456" EX 300
$body = '{"phone":"13800138000","code":"123456"}'
$resp = Invoke-WebRequest -Uri "http://localhost:8081/user/login" -Method Post -Headers $headers -Body $body
$shopToken = ($resp.Content | ConvertFrom-Json).data
```

### 5.2 执行测试用例

```powershell
# ========== 用户端测试 ==========

# TC-01: 正常提交评价
$userHeaders = @{ "Content-Type" = "application/json"; "authorization" = $userToken }
$body = '{"orderId":1,"orderType":2,"score":5,"content":"非常好吃"}'
Invoke-WebRequest -Uri "http://localhost:8081/review" -Method Post -Headers $userHeaders -Body $body

# TC-09: 查看评价详情
Invoke-WebRequest -Uri "http://localhost:8081/review/1" -Method Get -Headers $userHeaders

# TC-11: 我的评价列表
Invoke-WebRequest -Uri "http://localhost:8081/review/my?current=1&size=10" -Method Get -Headers $userHeaders

# TC-13: 商户评价列表
Invoke-WebRequest -Uri "http://localhost:8081/review/shop/15?current=1&size=10" -Method Get -Headers $userHeaders

# ========== 商家端测试 ==========

# TC-15: 商家查看评价列表
$shopHeaders = @{ "Content-Type" = "application/json"; "authorization" = $shopToken }
Invoke-WebRequest -Uri "http://localhost:8081/shop/review/list?current=1&size=10" -Method Get -Headers $shopHeaders

# TC-17: 商家回复评价
$body = '{"reviewId":1,"reply":"感谢您的好评"}'
Invoke-WebRequest -Uri "http://localhost:8081/shop/review/reply" -Method Put -Headers $shopHeaders -Body $body
```

---

## 六、关键验证点

### 6.1 审核通过后shop_id保存

验证审核通过后，`tb_shop_apply` 表中的 `shop_id` 字段是否正确保存：

```sql
SELECT id, user_id, shop_id, shop_name, status FROM tb_shop_apply WHERE status = 1;
```

### 6.2 商户评分更新

验证评价提交后，`tb_shop` 表的 `avg_score` 和 `review_count` 是否正确更新：

```sql
SELECT id, name, avg_score, review_count FROM tb_shop WHERE id = 15;
```

### 6.3 商家身份验证

验证商家回复时，是否正确通过 `shop_id` 验证商家身份。

---

## 七、验收标准

| 序号 | 验收项 | 说明 |
|------|--------|------|
| 1 | 所有测试用例通过 | 21个测试用例全部通过 |
| 2 | 接口响应时间 | 平均响应时间 < 200ms |
| 3 | 数据一致性 | 评价提交后商户评分正确更新 |
| 4 | 商家身份验证 | 通过shop_id正确验证商家身份 |
| 5 | 错误提示友好 | 错误信息清晰明确 |
