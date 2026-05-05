# hm-dianping 项目说明

`hm-dianping` 是一个基于 `Spring Boot + Redis + MySQL + Kafka + Nginx` 的大众点评类项目。  
项目围绕本地生活服务场景，实现了用户登录、商户查询、博客探店、关注 Feed、优惠券秒杀等功能，并在此基础上补充了缓存优化、异步削峰、分布式锁、失败补偿、接口限流和自动化测试等工程化能力。

这个仓库不仅是一个业务练手项目，也适合作为高并发、缓存、消息队列、限流和测试能力的综合实践项目。

## 一、项目亮点

- 基于 Redis 实现短信验证码登录、Token 会话缓存、签到统计
- 基于 Redis 的 `Set`、`ZSet`、`Bitmap`、`GEO` 等数据结构实现业务能力
- 秒杀链路采用 `Redis Lua + Redis Stream + Kafka + MySQL`
- 通过 Redisson 分布式锁、数据库唯一索引、乐观扣减库存防止超卖和重复下单
- 通过 `Caffeine + Redis` 实现二级缓存
- 通过 Redis Pub/Sub 处理多节点本地缓存失效
- 基于 `Spring AOP + Redis` 实现轻量级接口限流
- 提供 API / UI 自动化测试与 Allure 报告支持

## 二、核心功能

### 1. 用户模块

- 手机号验证码登录
- Token 登录态维护
- 用户信息查询
- 退出登录
- 用户签到与连续签到统计

### 2. 商户模块

- 商户详情查询
- 按类型分页查询商户
- 按名称关键字查询商户
- 商户类型列表查询

### 3. 博客 / 探店模块

- 发布探店博客
- 查询热门博客
- 博客点赞
- 查询点赞用户列表
- 关注用户动态流

### 4. 关注模块

- 关注 / 取关用户
- 判断是否已关注
- 查询共同关注

### 5. 优惠券 / 秒杀模块

- 普通优惠券新增与查询
- 秒杀券新增
- 秒杀下单
- 秒杀订单异步落库
- 失败任务补偿与重试

## 三、技术栈

### 后端框架

- Java 8
- Spring Boot 2.3.12
- Spring MVC
- MyBatis-Plus
- Spring Data Redis
- Spring Kafka
- Redisson

### 存储与中间件

- MySQL
- Redis
- Kafka
- Nginx

### 缓存 / 并发治理

- Caffeine
- Redis Lua
- Redis Stream
- 分布式锁
- 接口限流

### 测试

- PyTest
- Selenium
- Allure

## 四、项目架构设计

### 1. 登录与会话设计

项目采用手机号验证码登录。

- 验证码缓存到 Redis：`login:code:{phone}`
- 用户登录后，生成 Token 并将用户会话写入 Redis：`login:token:{token}`

服务端通过拦截器完成：

- Token 刷新
- 登录态校验
- 用户信息写入 `ThreadLocal`

相关文件：

- [UserController.java](/D:/Java/hm-dianping/src/main/java/com/hmdp/controller/UserController.java:1)
- [UserServiceImpl.java](/D:/Java/hm-dianping/src/main/java/com/hmdp/service/impl/UserServiceImpl.java:1)
- [RefreshTokenInterceptor.java](/D:/Java/hm-dianping/src/main/java/com/hmdp/utils/RefreshTokenInterceptor.java:1)
- [LoginInterceptor.java](/D:/Java/hm-dianping/src/main/java/com/hmdp/utils/LoginInterceptor.java:1)

### 2. 缓存设计

项目既有基础 Redis 缓存，也引入了二级缓存方案：

- L1：Caffeine 本地缓存
- L2：Redis
- L3：MySQL

主要优化点：

- 缓存穿透：缓存空对象
- 缓存击穿：互斥锁 / 逻辑过期
- 热点数据：本地缓存加速
- 多节点一致性：Redis Pub/Sub 广播缓存失效消息

相关文件：

- [CacheClient.java](/D:/Java/hm-dianping/src/main/java/com/hmdp/utils/CacheClient.java:1)
- [LocalCacheConfig.java](/D:/Java/hm-dianping/src/main/java/com/hmdp/config/LocalCacheConfig.java:1)
- [CacheInvalidationConfig.java](/D:/Java/hm-dianping/src/main/java/com/hmdp/config/CacheInvalidationConfig.java:1)
- [CacheInvalidationListener.java](/D:/Java/hm-dianping/src/main/java/com/hmdp/config/CacheInvalidationListener.java:1)

### 3. 秒杀链路设计

秒杀是项目中最核心的高并发场景，链路如下：

```text
用户请求
-> Controller 入口
-> Redis Lua 原子校验库存 / 一人一单 / 预扣库存
-> XADD 写入 Redis Stream
-> Relay 转发到 Kafka
-> Kafka Consumer 异步消费
-> MySQL 扣减最终库存并创建订单
```

关键设计点：

- Redis Lua 负责秒杀资格原子校验
- Redis 负责前置预扣库存，不作为最终订单结果
- Kafka 负责削峰、解耦、失败重试和死信处理
- MySQL 使用 `stock > 0` 和唯一索引做最终兜底
- Redisson 分布式锁进一步保护同一用户并发创建订单

相关文件：

- [VoucherOrderServiceImpl.java](/D:/Java/hm-dianping/src/main/java/com/hmdp/service/impl/VoucherOrderServiceImpl.java:1)
- [seckill.lua](/D:/Java/hm-dianping/src/main/resources/seckill.lua:1)
- [RedisStreamToKafkaRelay.java](/D:/Java/hm-dianping/src/main/java/com/hmdp/mq/RedisStreamToKafkaRelay.java:1)
- [VoucherOrderConsumer.java](/D:/Java/hm-dianping/src/main/java/com/hmdp/mq/VoucherOrderConsumer.java:1)
- [VoucherOrderDeadLetterConsumer.java](/D:/Java/hm-dianping/src/main/java/com/hmdp/mq/VoucherOrderDeadLetterConsumer.java:1)

### 4. 限流设计

项目补充了一套轻量级限流方案，当前采用：

- 自定义注解 `@RateLimit`
- Spring AOP 统一拦截
- Redis 固定窗口计数
- 验证码接口附加冷却时间控制

首批限流接口：

- `/user/code`
- `/user/login`
- `/voucher-order/seckill/{id}`

相关文件：

- [RateLimit.java](/D:/Java/hm-dianping/src/main/java/com/hmdp/annotation/RateLimit.java:1)
- [RateLimitAspect.java](/D:/Java/hm-dianping/src/main/java/com/hmdp/aspect/RateLimitAspect.java:1)
- [RateLimitServiceImpl.java](/D:/Java/hm-dianping/src/main/java/com/hmdp/service/impl/RateLimitServiceImpl.java:1)
- [RateLimitKeyResolver.java](/D:/Java/hm-dianping/src/main/java/com/hmdp/utils/RateLimitKeyResolver.java:1)

## 五、目录结构

```text
hm-dianping
├─ src/main/java/com/hmdp
│  ├─ controller        # Controller 层
│  ├─ service           # Service 接口
│  ├─ service/impl      # Service 实现
│  ├─ mapper            # MyBatis-Plus Mapper
│  ├─ entity            # 实体类
│  ├─ dto               # DTO / 返回对象
│  ├─ config            # Spring 配置、缓存、Kafka、调度器
│  ├─ mq                # 消息队列与异步链路
│  ├─ utils             # 工具类
│  ├─ annotation        # 自定义注解
│  ├─ aspect            # AOP 切面
│  ├─ exception         # 异常定义
│  └─ enums             # 枚举
├─ src/main/resources
│  ├─ application.yaml
│  ├─ application-local.example.yaml
│  ├─ db
│  ├─ mapper
│  ├─ seckill.lua
│  └─ seckill_rollback.lua
├─ tests                # 自动化测试
├─ scripts              # 测试 / 压测脚本
├─ heima_qianduan       # 前端静态资源与 Nginx
└─ *.md                 # 设计文档、压测记录、面试材料
```

## 六、快速开始

### 1. 环境要求

- JDK 8
- Maven 3.6+
- MySQL 5.7+
- Redis 6.x
- Kafka 2.x+

### 2. 数据初始化

可以使用以下 SQL 文件初始化数据库：

- [src/main/resources/db/hmdp.sql](/D:/Java/hm-dianping/src/main/resources/db/hmdp.sql:1)
- [heima_qianduan/hmdp.sql](/D:/Java/hm-dianping/heima_qianduan/hmdp.sql:1)

如果要体验增强版秒杀链路，建议额外执行：

- [src/main/resources/db/voucher_order_unique_index.sql](/D:/Java/hm-dianping/src/main/resources/db/voucher_order_unique_index.sql:1)
- [src/main/resources/db/voucher_order_fail_task.sql](/D:/Java/hm-dianping/src/main/resources/db/voucher_order_fail_task.sql:1)
- [src/main/resources/db/voucher_order_fail_task_failure_type.sql](/D:/Java/hm-dianping/src/main/resources/db/voucher_order_fail_task_failure_type.sql:1)

### 3. 配置说明

项目采用“公共配置 + 本地私有配置”方式：

- [application.yaml](/D:/Java/hm-dianping/src/main/resources/application.yaml:1)  
  可提交到仓库，使用占位符和默认值，不包含真实敏感配置

- [application-local.example.yaml](/D:/Java/hm-dianping/src/main/resources/application-local.example.yaml:1)  
  本地配置示例文件

推荐做法：

1. 复制 `application-local.example.yaml`
2. 重命名为 `application-local.yaml`
3. 填入你本地真实的 MySQL / Redis / Kafka 配置

`application-local.yaml` 已加入 `.gitignore`，不会提交到 GitHub。

你也可以通过环境变量注入配置，例如：

- `DB_URL`
- `DB_USERNAME`
- `DB_PASSWORD`
- `REDIS_HOST`
- `REDIS_PORT`
- `REDIS_PASSWORD`
- `KAFKA_BOOTSTRAP_SERVERS`

### 4. 启动顺序

推荐启动顺序：

1. MySQL
2. Redis
3. Kafka
4. Spring Boot 后端
5. Nginx 前端

### 5. 启动后端

```bash
mvn spring-boot:run
```

或者直接运行启动类：

- [HmDianPingApplication.java](/D:/Java/hm-dianping/src/main/java/com/hmdp/HmDianPingApplication.java:1)

默认后端地址：

```text
http://127.0.0.1:8081
```

### 6. 启动前端

项目自带前端静态资源和 Nginx，可直接本地启动：

```bash
cd heima_qianduan/nginx-1.18.0
start nginx.exe
```

默认前端地址：

```text
http://127.0.0.1:8080
```

## 七、测试说明

项目包含 API / UI / 秒杀链路自动化测试。

详见：

- [tests/README.md](/D:/Java/hm-dianping/tests/README.md:1)

常用命令：

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

## 八、适合展开的工程化主题

这个项目比较适合在简历和面试中展开以下内容：

- Redis 缓存设计
- 二级缓存与缓存一致性
- 秒杀高并发链路
- Kafka 削峰、重试、死信与补偿
- Redis 预扣库存与回滚
- 分布式锁与幂等控制
- 接口限流
- 自动化测试平台与回归测试
- 压测与性能优化

## 九、上传 GitHub 前建议

建议在公开仓库前做以下整理：

- 保留 `application.yaml`，不要提交真实本地配置
- 不提交 `application-local.yaml`
- 清理日志、报告、临时缓存文件
- 适当补充架构图、接口截图、压测结果图
- 增加 `LICENSE` 文件，例如 `MIT`

## 十、后续优化方向

- 将限流规则抽离到配置层
- 为热点接口补充 Nginx / 网关层粗限流
- 增加 Prometheus / Grafana 监控指标
- 为秒杀与缓存链路补充更多集成测试
- 补充英文版 README 与架构图

## License

当前仓库尚未补充开源协议。  
如果你准备公开到 GitHub，建议新增 `LICENSE` 文件。
