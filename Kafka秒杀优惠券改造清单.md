# Kafka 秒杀优惠券改造清单

## 1. 改造目标

- 将当前基于 Redis Stream 的异步下单链路升级为 `Redis Lua + Kafka + MySQL` 的秒杀架构
- 保留 Redis 在高并发资格校验和预扣库存上的优势
- 引入 Kafka 做异步削峰、解耦和更可靠的消费恢复
- 强化幂等、重试、死信、补偿和可观测性，提升工程可讲性

## 2. 当前系统现状

- 秒杀入口在 [VoucherOrderServiceImpl.java](/D:/Java/hm-dianping/src/main/java/com/hmdp/service/impl/VoucherOrderServiceImpl.java:1)
- 入口先执行 [seckill.lua](/D:/Java/hm-dianping/src/main/resources/seckill.lua:1)
- Lua 脚本完成库存校验、一人一单校验、Redis 预扣库存
- Lua 成功后将订单消息写入 `stream.orders`
- 后台通过 Redis Stream 消费，再转发给 Kafka
- Kafka Consumer 异步消费后创建订单并落 MySQL

## 3. 目标架构

```text
用户请求
-> Redis Lua 原子校验 + XADD stream.orders
-> Redis Stream To Kafka Relay
-> Kafka Topic 削峰
-> Kafka Consumer 异步消费
-> MySQL 扣减最终库存并创建订单
-> 成功确认 / 失败重试 / 死信补偿
```

一句话概括：

`Redis 负责前置原子校验，Kafka 负责异步削峰，MySQL 负责最终业务落库。`

## 4. 设计原则

- Redis 负责秒杀入口的原子资格校验
- Kafka 负责异步传输、解耦和消费恢复
- MySQL 作为最终业务事实来源
- 幂等必须在 Redis、Consumer、数据库三层共同保障
- Relay 必须做到“Kafka 发送成功后再 ACK Redis Stream”
- 方案重点是高并发、异步削峰、最终一致性、幂等和补偿

## 5. 改造清单

### 5.1 主链路改造

- 保留 `VoucherOrderServiceImpl.seckillVoucher(...)` 中的 Redis Lua 校验逻辑
- 保留 Lua 中的 `XADD stream.orders`
- 新增 `VoucherOrderMessage` 作为 Kafka 消息模型
- 新增 `VoucherOrderProducer`
- 新增 `RedisStreamToKafkaRelay`
- 新增 `VoucherOrderConsumer`
- 将订单创建逻辑下沉为可复用业务方法，供 Consumer 调用
- 当前接口成功语义定义为：`请求已通过 Redis 校验并成功写入 Redis Stream，表示已受理，不代表订单已同步落库完成`

### 5.2 幂等与库存安全

- Consumer 侧继续做重复下单校验
- 数据库库存扣减必须带 `stock > 0` 条件
- Redis Lua 继续负责一人一单和预扣库存
- 数据库已补充唯一索引脚本 `src/main/resources/db/voucher_order_unique_index.sql`
- 数据库唯一索引 `unique(user_id, voucher_id)` 作为最终幂等兜底

### 5.3 可靠性增强

- Producer 开启 `acks=all`
- Producer 保留重试和幂等配置
- Producer 已改成同步等待 broker ack，只有 Kafka 真正确认成功才返回 success
- Consumer 已改为手动 ack，业务成功后才提交 offset
- Consumer 已接入 `SeekToCurrentErrorHandler + FixedBackOff(1000ms, 3次)` 做有限次重试
- Relay 已支持 pending-list 恢复
- Relay 保持“Kafka 发送成功后再 ACK Redis Stream”

### 5.4 一致性补偿

- 已验证 Kafka Consumer 失败后不会立即 ack，会基于 offset 重试
- 已验证 Kafka 不可用时，Relay 不应误判发送成功
- 已补充：`MqExceptionClassifier` 粗分可恢复/不可恢复；不可恢复时在 Consumer 侧先执行 Redis 幂等回滚（`seckill_rollback.lua`）再 ack，避免长期预扣不一致
- 已补充：进入死信后落失败任务并自动回滚 Redis，状态可为 `ROLLBACK_DONE`；表字段 `failure_type` 记录 `RECOVERABLE|NON_RECOVERABLE`
- 当前最小补偿链路为：`Kafka 重试 + Redis Stream pending-list 恢复 + Redis 预扣回滚（幂等）`
- 已补充：`KafkaLagMonitorScheduler`（Kafka lag 超阈值 WARN）、`SeckillStockReconcileScheduler`（`seckill:stock:{id}` 与 `tb_seckill_voucher.stock` 不一致 WARN）；大盘类告警仍可再接 Prometheus 等

### 5.5 可观测性

- 已为链路日志补充 `orderId`、`userId`、`voucherId`、`traceId`
- 已覆盖日志节点：Relay 转发成功/失败、Kafka send 成功/失败、Kafka consume 成功/失败、订单创建成功、重复订单命中
- 已补充：`app.kafka.monitor` 下周期拉取消费组 lag，超阈值打 WARN；`app.seckill.reconcile` 下库存对账 WARN
- 后续可继续补充：指标导出、下单成功率/死信数大盘、与告警系统联动

## 6. 组件职责说明

### 6.1 秒杀请求入口

- 位置：[VoucherOrderServiceImpl.java](/D:/Java/hm-dianping/src/main/java/com/hmdp/service/impl/VoucherOrderServiceImpl.java:1)
- 职责：
- 接收秒杀请求
- 生成订单 ID
- 执行 Lua 脚本
- 返回“已受理”结果

### 6.2 Lua 脚本

- 位置：[seckill.lua](/D:/Java/hm-dianping/src/main/resources/seckill.lua:1)
- 职责：
- 判断库存
- 判断是否重复下单
- 预扣 Redis 库存
- 写入 `stream.orders`

### 6.3 Redis Stream To Kafka Relay

- 位置：[RedisStreamToKafkaRelay.java](/D:/Java/hm-dianping/src/main/java/com/hmdp/mq/RedisStreamToKafkaRelay.java:1)
- 职责：
- 消费 `stream.orders`
- 转换成 `VoucherOrderMessage`
- 发送到 Kafka
- Kafka 成功后 ACK Redis Stream

### 6.4 Kafka Producer

- 位置：[VoucherOrderProducerImpl.java](/D:/Java/hm-dianping/src/main/java/com/hmdp/mq/impl/VoucherOrderProducerImpl.java:1)
- 职责：
- 构造消息 JSON
- 指定 Topic
- 以 `userId` 作为 key 发送 Kafka

### 6.5 Kafka Consumer

- 位置：[VoucherOrderConsumer.java](/D:/Java/hm-dianping/src/main/java/com/hmdp/mq/VoucherOrderConsumer.java:1)
- 职责：
- 监听 Kafka Topic
- 反序列化消息
- 调用订单创建逻辑
- 执行业务幂等与落库

## 7. Topic 和分区设计建议

- Topic：`seckill-order`
- 死信 Topic：`seckill-order-dlt`
- 分区数建议：`3` 或 `6`
- 副本数建议：`2` 或 `3`
- Message key 建议使用 `userId`

说明：

- 分区数决定消费并发能力
- 使用 `userId` 作为 key，有助于同一用户消息尽量进入同一分区
- 不强求全局顺序，更依赖幂等和数据库约束保证一致性

## 8. 幂等设计

### 8.1 Redis 层

- Lua 保证库存校验和一人一单校验原子执行

### 8.2 Kafka 消费层

- Consumer 不能假设消息只会到达一次
- 必须在业务逻辑中处理重复消费

### 8.3 数据库层

- 建议增加 `user_id + voucher_id` 唯一索引
- 即使出现重复消息，也不会生成重复订单

## 9. 最终一致性设计

- Redis 里的预扣库存属于高并发优化手段
- MySQL 中的库存和订单才是最终业务结果
- 如果 Kafka 消费失败，需要通过重试、死信和补偿修复 Redis 与 MySQL 差异

## 10. 典型失败场景

### 10.1 Producer 发送失败

- 记录失败日志
- 可补失败重试或失败表

### 10.2 Consumer 消费失败

- 不提交 offset，触发重试
- 重试超限后写入死信 Topic

### 10.3 消息重复消费

- Consumer 做业务判重
- 数据库唯一索引兜底

### 10.4 Redis 已预扣库存但订单落库失败

- 进入补偿流程
- 回滚 Redis 库存和购买标记

### 10.5 服务重启

- Kafka 保留未消费消息
- Consumer 重启后可继续消费

### 10.6 Redis Stream 和 Kafka 之间断点

- 通过 Relay 消除直接断点
- 只有 Kafka 成功后才 ACK Stream
- 失败消息保留在 pending 或补偿链路中

## 11. 面试表达建议

不要只说“用了 Kafka”，要强调职责分工：

- Redis：高并发原子校验
- Kafka：异步削峰和可恢复消费
- MySQL：最终业务落库

面试重点建议突出：

- 幂等
- 超卖控制
- 重复消费
- 消息堆积
- 死信处理
- 最终一致性补偿

一句推荐表达：

`我没有简单把 Redis Stream 替换成 Kafka，而是把秒杀系统升级成 Redis 前置原子校验 + Kafka 异步削峰 + MySQL 最终落库的链路。`

## 12. 推荐实施顺序

### 第一阶段：最小可运行版本

- 引入 Kafka 依赖和配置
- 新增消息模型
- 新增 Producer
- 新增 Redis Stream -> Kafka Relay
- 新增 Consumer
- 打通主链路

### 第二阶段：可靠性增强

- 增加唯一索引
- 增加手动提交 offset
- 增加失败重试和死信
- 增加链路日志
- 增加 pending-list 恢复

### 第三阶段：一致性补偿增强

- 增加补偿任务
- 增加库存对账任务（定时比对 Redis 秒杀库存与 DB，见 `SeckillStockReconcileScheduler`）
- 增加补偿日志和监控（含 Kafka lag 巡检，见 `KafkaLagMonitorScheduler`）

## 13. 压测准备建议

建议准备三套对比方案：

- 同步下单
- Redis Stream 异步下单
- Kafka 异步下单

重点指标：

- QPS
- 平均响应时间
- TP95
- TP99
- 下单成功率
- 重复下单率
- 超卖率
- Kafka 堆积量
- DB CPU 和慢 SQL

## 14. 最终结论

- 这次改造不是简单地“把 Redis Stream 换成 Kafka”
- 更准确地说，是将秒杀链路升级成 Redis + Kafka + MySQL 分层协作的高并发架构
- 面试价值主要体现在：
- 架构分层合理
- 高并发细节完整
- 一致性考虑比较全面
- 方案既能落地，也能扩展

## 15. 代码落地清单

- [seckill.lua](/D:/Java/hm-dianping/src/main/resources/seckill.lua:1)
- [VoucherOrderServiceImpl.java](/D:/Java/hm-dianping/src/main/java/com/hmdp/service/impl/VoucherOrderServiceImpl.java:1)
- [VoucherOrderMessage.java](/D:/Java/hm-dianping/src/main/java/com/hmdp/mq/VoucherOrderMessage.java:1)
- [VoucherOrderProducer.java](/D:/Java/hm-dianping/src/main/java/com/hmdp/mq/VoucherOrderProducer.java:1)
- [VoucherOrderProducerImpl.java](/D:/Java/hm-dianping/src/main/java/com/hmdp/mq/impl/VoucherOrderProducerImpl.java:1)
- [RedisStreamToKafkaRelay.java](/D:/Java/hm-dianping/src/main/java/com/hmdp/mq/RedisStreamToKafkaRelay.java:1)
- [VoucherOrderConsumer.java](/D:/Java/hm-dianping/src/main/java/com/hmdp/mq/VoucherOrderConsumer.java:1)
- [KafkaConfig.java](/D:/Java/hm-dianping/src/main/java/com/hmdp/config/KafkaConfig.java:1)
- [application.yaml](/D:/Java/hm-dianping/src/main/resources/application.yaml:1)

## 16. Kafka 本地启动与运行说明

### 16.1 启动前准备

本地至少先准备好：

- MySQL
- Redis
- Kafka

当前项目默认配置见 [application.yaml](/D:/Java/hm-dianping/src/main/resources/application.yaml:1)：

- MySQL：`127.0.0.1:3306`
- Redis：`127.0.0.1:6379`
- Kafka：`127.0.0.1:9092`

### 16.2 正确启动顺序

建议顺序：

1. 启动 MySQL
2. 启动 Redis
3. 启动 Kafka
4. 启动 Spring Boot 项目

也就是说：

`先启动本地 Kafka，再启动项目。`

原因：

- Relay 启动后需要连接 Redis 和 Kafka
- Consumer 启动后需要监听 Kafka Topic
- Kafka 如果未先启动，项目里的 Kafka 链路会不可用或报错

### 16.3 Kafka 启动要点

至少确认：

- Kafka Broker 已启动
- 端口监听在 `9092`
- `spring.kafka.bootstrap-servers` 和实际地址一致

如果你用的是传统 Kafka：

1. 先启动 ZooKeeper
2. 再启动 Kafka Broker

如果你用的是 KRaft：

- 只需要启动 Kafka Broker

### 16.4 Topic 是否要手动创建

当前项目默认 Topic：

- `seckill-order`

如果 Broker 允许自动建 Topic，首次发送时可能自动创建。  
如果没有开启自动创建，就要提前手动创建 `seckill-order`。

更规范的面试说法：

`我会手动创建 Topic，并按吞吐和消费并发设计分区数，而不是依赖自动创建。`

### 16.5 项目启动后会发生什么

项目启动后会：

1. 初始化 Kafka Producer 和 Consumer
2. 启动 `RedisStreamToKafkaRelay`
3. 检查并初始化 Redis Stream 消费组
4. 接收 Lua 写入的 `stream.orders`
5. Relay 转发到 Kafka
6. Consumer 消费后执行订单创建和落库

### 16.6 如何验证链路已跑通

建议验证方式：

1. 启动 MySQL、Redis、Kafka、Spring Boot
2. 先准备好 Redis 中的秒杀库存 key，比如 `seckill:stock:{voucherId}`
3. 调用 `POST /voucher-order/seckill/{id}`
4. 查看日志中是否出现 Relay 和 Consumer 处理记录
5. 检查 MySQL 是否落了订单
6. 检查 Redis 库存是否扣减

如果这些都正常，就说明：

`Lua -> Redis Stream -> Relay -> Kafka -> Consumer -> MySQL`

这条链路已经打通。

### 16.7 常见启动排查项

如果跑不起来，优先检查：

1. MySQL 是否已启动
2. Redis 是否已启动，密码是否与配置一致
3. Kafka 是否监听在 `9092`
4. `seckill-order` Topic 是否存在
5. Redis 中是否已准备 `seckill:stock:{voucherId}`
6. Consumer 是否报错
7. Relay 是否有发送失败或 pending 积压

### 16.8 面试时的标准回答

可以直接这样说：

`这套本地环境我会先启动 MySQL、Redis 和 Kafka，再启动 Spring Boot 项目。因为秒杀入口依赖 Redis Lua 和 Redis Stream，同时 Relay 启动后还要连接 Kafka，所以 Kafka 必须先于项目启动。启动后我会通过调用 POST /voucher-order/seckill/{id}，结合日志、Redis 和 MySQL 数据，验证整条异步下单链路是否已经打通。`
