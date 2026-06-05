

一个基于 `Spring Boot + Redis + MySQL + Kafka + Nginx` 的大众点评类本地生活服务项目。

项目覆盖了登录鉴权、商户查询、博客探店、关注 Feed、优惠券秒杀等典型业务场景，同时加入了缓存优化、异步削峰、分布式锁、失败补偿、接口限流、自动化测试等工程化能力，适合作为高并发项目练习和简历项目扩展。

## 项目亮点

- 手机验证码登录，基于 Redis 存储验证码与登录态
- Redis 多数据结构实践：`String`、`Set`、`ZSet`、`Bitmap`、`GEO`
- 秒杀链路采用 `Redis Lua + Redis Stream + Kafka + MySQL`
- 通过 Lua、唯一索引、Redisson、乐观锁共同防止超卖和重复下单
- 两级缓存架构：`Caffeine + Redis`
- 通过 Redis Pub/Sub 保证多节点本地缓存失效同步
- 基于 `Spring AOP + Redis` 的轻量级接口限流
- 提供 API / UI 自动化测试能力

## 主要功能

### 用户

- 发送手机验证码
- 验证码登录
- Token 登录态管理
- 退出登录
- 用户资料查询
- 用户签到与连续签到统计

### 商户

- 商户详情查询
- 按类型分页查询商户
- 按关键字查询商户
- 商户类型列表查询

### 博客 / Feed

- 发布探店博客
- 查询热门博客
- 博客点赞
- 查询点赞用户
- 关注后 Feed 流

### 关注

- 关注 / 取关用户
- 判断是否已关注
- 查询共同关注

### 优惠券 / 秒杀

- 普通优惠券新增与查询
- 秒杀券新增
- 秒杀下单
- 异步订单落库
- 重试与失败补偿

### 搜索

- 商户全文搜索（名称、地址、商圈）
- 支持高亮显示
- 支持地理位置排序（距离排序）
- Canal实时数据同步

## 技术栈

### 后端

- Java 8
- Spring Boot 2.3.12
- Spring MVC
- MyBatis-Plus
- Spring Data Redis
- Redisson
- Spring Kafka
- Jackson
- Hutool

### 中间件

- MySQL
- Redis
- Kafka
- Nginx

### 缓存与并发

- Caffeine
- Redis Lua
- Redis Stream
- 分布式锁
- 接口限流

### 测试

- JUnit 5
- PyTest
- Selenium
- Allure

### 监控

- Micrometer指标采集
- Prometheus指标暴露
- JVM、HTTP、Redis、Kafka指标
- 自定义业务指标（秒杀成功率、缓存命中率）

## 核心架构

### 1. 登录与鉴权

用户通过手机验证码登录，Redis 中保存：

- 验证码：`login:code:{phone}`
- 用户 Token：`login:token:{token}`

后端通过拦截器完成：

- Token 刷新
- 登录校验
- `ThreadLocal` 用户上下文透传

### 2. 商户缓存架构

商户相关数据采用两级缓存：

- L1：Caffeine 本地缓存
- L2：Redis 分布式缓存
- L3：MySQL 数据库

主要策略包括：

- 缓存穿透防护：空值缓存
- 缓存击穿防护：互斥锁 / 逻辑过期
- 热点数据加速：本地缓存
- 多节点一致性：Redis Pub/Sub 广播失效

相关文件：

- [CacheClient.java](/D:/Java/hm-dianping/src/main/java/com/hmdp/utils/CacheClient.java:1)
- [LocalCacheConfig.java](/D:/Java/hm-dianping/src/main/java/com/hmdp/config/LocalCacheConfig.java:1)

### 3. 秒杀链路

秒杀是本项目最核心的高并发场景：

```text
用户请求
-> Controller 接口入口
-> Redis Lua 校验库存 / 一人一单 / 预扣库存
-> XADD 写入 Redis Stream
-> Relay 中转到 Kafka
-> Kafka 消费者异步落库
-> MySQL 扣减最终库存并创建订单
```

核心思路：

- Redis Lua 保证资格校验和预扣减的原子性
- Redis 负责快速拦截，不作为最终订单真相来源
- Kafka 负责削峰、解耦、重试、死信分流
- MySQL 唯一索引和 `stock > 0` 作为最终一致性兜底
- Redisson 分布式锁进一步保护同一用户的并发下单

相关文件：

- [VoucherOrderServiceImpl.java](/D:/Java/hm-dianping/src/main/java/com/hmdp/service/impl/VoucherOrderServiceImpl.java:1)
- [seckill.lua](/D:/Java/hm-dianping/src/main/resources/seckill.lua:1)
- [RedisStreamToKafkaRelay.java](/D:/Java/hm-dianping/src/main/java/com/hmdp/mq/RedisStreamToKafkaRelay.java:1)
- [VoucherOrderConsumer.java](/D:/Java/hm-dianping/src/main/java/com/hmdp/mq/VoucherOrderConsumer.java:1)

### 4. 接口限流

项目实现了一个轻量级限流方案，基于：

- 自定义注解 `@RateLimit`
- Spring AOP 切面拦截
- Redis 固定窗口计数与冷却控制

首批保护接口包括：

- `/user/code`
- `/user/login`
- `/voucher-order/seckill/{id}`

相关文件：

- [RateLimit.java](/D:/Java/hm-dianping/src/main/java/com/hmdp/annotation/RateLimit.java:1)
- [RateLimitAspect.java](/D:/Java/hm-dianping/src/main/java/com/hmdp/aspect/RateLimitAspect.java:1)
- [RateLimitServiceImpl.java](/D:/Java/hm-dianping/src/main/java/com/hmdp/service/impl/RateLimitServiceImpl.java:1)

## 项目结构

```text
hm-dianping
├─ src/main/java/com/hmdp
│  ├─ controller
│  ├─ service
│  ├─ service/impl
│  ├─ mapper
│  ├─ entity
│  ├─ dto
│  ├─ config
│  ├─ mq
│  ├─ utils
│  ├─ aspect
│  ├─ annotation
│  ├─ exception
│  └─ enums
├─ src/main/resources
│  ├─ application.yaml
│  ├─ application-local.example.yaml
│  ├─ db
│  ├─ mapper
│  ├─ seckill.lua
│  └─ seckill_rollback.lua
├─ src/test/java
├─ tests
├─ scripts
├─ heima_qianduan
└─ docs
```

## 快速开始

### 环境要求

- JDK 8
- Maven 3.6+
- MySQL 5.7+
- Redis 6.x
- Kafka 2.x+

### 数据库初始化

基础建表脚本：

- [src/main/resources/db/hmdp.sql](/D:/Java/hm-dianping/src/main/resources/db/hmdp.sql:1)
- [heima_qianduan/hmdp.sql](/D:/Java/hm-dianping/heima_qianduan/hmdp.sql:1)

秒杀增强链路建议额外执行：

- [src/main/resources/db/voucher_order_unique_index.sql](/D:/Java/hm-dianping/src/main/resources/db/voucher_order_unique_index.sql:1)
- [src/main/resources/db/voucher_order_fail_task.sql](/D:/Java/hm-dianping/src/main/resources/db/voucher_order_fail_task.sql:1)
- [src/main/resources/db/voucher_order_fail_task_failure_type.sql](/D:/Java/hm-dianping/src/main/resources/db/voucher_order_fail_task_failure_type.sql:1)

### 配置说明

公共配置文件：

- [application.yaml](/D:/Java/hm-dianping/src/main/resources/application.yaml:1)

本地示例配置：

- [application-local.example.yaml](/D:/Java/hm-dianping/src/main/resources/application-local.example.yaml:1)

推荐方式：

1. 复制 `application-local.example.yaml`
2. 重命名为 `application-local.yaml`
3. 替换其中的占位值为你自己的本地配置

`application-local.yaml` 已被 Git 忽略，不应提交。

也可以通过环境变量注入：

- `DB_URL`
- `DB_USERNAME`
- `DB_PASSWORD`
- `REDIS_HOST`
- `REDIS_PORT`
- `REDIS_PASSWORD`
- `KAFKA_BOOTSTRAP_SERVERS`
- `KAFKA_RELAY_CONSUMER_NAME`

### 启动顺序

推荐启动顺序：

1. MySQL
2. Redis
3. Kafka
4. Spring Boot 后端
5. Nginx 前端

### 启动后端

```bash
mvn spring-boot:run
```

或直接运行：

- [HmDianPingApplication.java](/D:/Java/hm-dianping/src/main/java/com/hmdp/HmDianPingApplication.java:1)

默认地址：

```text
http://127.0.0.1:8081
```

### 启动前端

```bash
cd heima_qianduan/nginx-1.18.0
start nginx.exe
```

默认地址：

```text
http://127.0.0.1:8080
```

## 测试

Java 回归测试建议按需运行：

```bash
mvn "-Dtest=VoucherOrderServiceImplTransactionTest,CacheClientLockTest,RedisStreamToKafkaRelayTest,ConfigurationTemplateSanitizationTest" test
```

Python 自动化测试说明见：

- [tests/README.md](/D:/Java/hm-dianping/tests/README.md:1)

示例：

```bash
pytest -m api --base-url http://127.0.0.1:8081
```

```bash
pytest -m ui --ui-base-url http://127.0.0.1:8080 --headless
```

```bash
pytest --base-url http://127.0.0.1:8081 --ui-base-url http://127.0.0.1:8080 --alluredir reports/allure-results
```

如果安装了 Allure：

```bash
allure serve reports/allure-results
```

## 文档导航

当前仓库中除了源码，还有不少设计、压测、面试准备和实施记录文档。暂时主要分布在仓库根目录和 `tests/` 目录：

### 设计 / 改造方案

- `二级缓存改造方案.md`
- `二级缓存改造清单.md`
- `Kafka秒杀优惠券改造清单.md`
- `Kafka秒杀链路后续优化实施计划.md`
- `限流设计方案.md`

### 报告 / 验证记录

- `二级缓存改造后验证报告.md`
- `S1-shop查询基线压测报告.md`
- `秒杀链路测试结果记录.md`
- `唯一索引与补偿链路验证步骤.md`
- `压测对比测试计划.md`

### 面试 / 讲解材料

- `Kafka秒杀链路面试准备文档.md`
- `Kafka秒杀链路面试题.md`
- `秒杀并发与Kafka面试话术-1_3_8分钟版.md`
- `项目技术问答.md`
- `项目技术问答详解.md`
- `tests/自动化测试平台*.md`

后续如果继续整理仓库，建议把这些文档统一迁移到 `docs/` 下分目录管理。

## 后续优化建议

- 将限流规则进一步配置化
- 增加网关或 Nginx 粗粒度限流
- 增加监控、指标与告警
- 补充更多缓存链路与秒杀链路集成测试
- 继续清理剩余乱码注释和历史临时文件
- 为仓库补充 `LICENSE`

## License

当前仓库还没有明确的开源协议。  
如果计划公开发布到 GitHub，建议补充一个 `LICENSE` 文件。
