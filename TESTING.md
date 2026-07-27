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
    assertTrue(Boolean.TRUE.equals(result.getSuccess()));
}

@Test
void testSeckillVoucherWhenStockEmptyThenFail() {
    // given: 准备测试数据（库存为空）
    Long voucherId = 2L;
    
    // when: 执行测试
    Result result = voucherOrderService.seckillVoucher(voucherId);
    
    // then: 验证结果
    assertFalse(Boolean.TRUE.equals(result.getSuccess()));
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

---

## 最近联调测试报告（2026-07-27）

本节记录最近一次本地环境联调与页面验证结果，适合作为项目展示、README 截图说明和后续回归参考。

### 测试环境

| 组件 | 状态 | 说明 |
| --- | --- | --- |
| JDK | 21 | 本地已验证可运行 |
| Maven | 3.9.16 | 已安装并用于启动项目 |
| MySQL | UP | 本地 brew services 启动 |
| Redis | UP | 本地 brew services 启动 |
| Kafka | UP | 本地 brew services 启动 |
| Elasticsearch | UP | 已通过 Docker 本地启动并完成接口验证 |
| 前端 | Vite 本地运行 | `http://127.0.0.1:3000` |
| 后端 | Spring Boot 本地运行 | `http://127.0.0.1:8081` |

### 已完成验证项

#### 1. 服务启动与健康检查

- MySQL、Redis、Kafka 可正常启动
- 后端可在 `8081` 端口启动
- 前端可在 `3000` 端口启动
- 健康检查中 `db`、`redis` 为 `UP`

#### 2. 用户登录链路

- 验证码发送成功
- 验证码登录成功
- 登录后可获取用户信息
- 登录态可驱动前端用户中心和订单页面展示

#### 3. 页面展示验证

已实际验证并截图的页面：

- 首页
- 登录页
- 商户列表页
- 商户详情页
- 用户中心页
- 订单页

截图目录：

- `docs/screenshots/home.jpeg`
- `docs/screenshots/login.jpeg`
- `docs/screenshots/shop-list.jpeg`
- `docs/screenshots/shop-detail.jpeg`
- `docs/screenshots/user-center.jpeg`
- `docs/screenshots/order-list.jpeg`

#### 4. 订单链路展示

- 已为测试账号写入一条本地测试订单
- 订单页可展示：
  - 订单号
  - 套餐标题
  - 金额
  - 状态
  - 核销码
- 用户中心页已增加最近订单预览

#### 5. 搜索链路验证

- Docker Desktop 已恢复并成功启动 `elasticsearch:7.17.25`
- `http://127.0.0.1:9200` 可正常访问
- `/actuator/health` 已恢复为整体 `UP`
- 已执行一次商户全量同步：

```bash
curl -X POST http://127.0.0.1:8081/shop/sync
```

- 已验证搜索接口返回有效结果：

```bash
curl -G http://127.0.0.1:8081/shop/search \
  --data-urlencode "keyword=103" \
  --data-urlencode "current=1" \
  --data-urlencode "size=5"
```

验证结果：

- 能正确返回 `103茶餐厅`
- 说明 Elasticsearch 接入、索引同步、搜索接口三者链路可用

### 本次发现的问题

#### 问题 1：`spring-boot:run` 默认会被测试编译阻塞

现象：

- 启动后端时，Maven 会先编译测试代码
- `ReviewServiceTest` 中使用了已失效的 `result.isSuccess()`
- 导致项目在启动前失败

处理结果：

- 已修复 `ReviewServiceTest` 中的断言写法
- 已将 `scripts/start-app.sh` 调整为默认跳过测试编译，提升本地联调稳定性

#### 问题 1.1：`ReviewServiceTest` 已从“无法运行”修复为“可稳定通过”

最近回归命令：

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn -Dtest=ReviewServiceTest test
```

最终结果：

- `Tests run: 7, Failures: 0, Errors: 0, Skipped: 0`
- `BUILD SUCCESS`

修复过程中的关键问题：

- `Result#isSuccess()` 断言与当前 `Result#getSuccess()` 不一致
- 缺少 `tb_review` 表与 `tb_shop` 扩展字段
- `tb_shop_apply` 表结构缺少代码所依赖的 `shop_id`
- 测试夹具订单重复使用同一个 `verify_code`，触发唯一索引冲突
- JDK 21 下，Surefire 测试 JVM 缺少 MyBatis-Plus 所需的 `--add-opens`

已采取的修复动作：

- 修正测试断言
- 在测试中补齐评价表及依赖夹具数据
- 为测试订单生成唯一核销码
- 为 `maven-surefire-plugin` 增加与运行时一致的 `--add-opens`

结论：

- 当前 `ReviewServiceTest` 已具备稳定执行条件
- 这组测试可以作为评价模块回归测试的基础用例继续保留
- 后续若扩展评价功能，建议沿用“测试自带夹具”的方式补充更多边界场景

#### 问题 2：`TESTING.md` 示例与当前 `Result` 结构不一致

现象：

- 文档示例仍使用 `result.isSuccess()`
- 与当前 `Result#getSuccess()` 不一致

处理结果：

- 已同步修正文档示例

#### 问题 3：`.gitignore` 存在损坏字符与冗余内容

现象：

- 文件中存在异常空字符
- 影响可读性，也容易引发后续维护问题

处理结果：

- 已整体清理并重写 `.gitignore`
- 保留截图目录白名单与本地日志忽略规则

### 当前仍需注意的事项

- 订单页与用户中心当前依赖本地测试账号与测试订单数据展示
- 若要继续做接口自动化与回归测试，建议补充一组稳定的测试夹具数据
- Elasticsearch 当前已可用，但若 Docker Desktop 未启动，健康检查会再次降为 `DOWN`

### 结论

本次本地联调结果整体良好，已经达到以下目标：

- 项目可本地启动
- 核心页面可正常展示
- README 已具备截图展示能力
- 用户登录、商户浏览、订单展示链路已经具备可演示性

如果后续继续推进，建议优先做这三件事：

1. 补一份正式的接口测试用例清单
2. 为订单、搜索、秒杀准备独立测试数据
3. 继续补充搜索关键字、高亮、地理位置排序等更细粒度回归用例
