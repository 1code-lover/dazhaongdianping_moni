# Kafka 秒杀链路后续优化实施计划

## 1. 目标说明

当前 Kafka 秒杀链路已经完成了第一轮核心可靠性增强，已经具备以下能力：

- Redis Lua 前置原子校验
- Redis Stream 承接受理消息
- Relay 转发 Kafka
- Producer 同步等待 broker ack
- Consumer 手动 ack
- Consumer 有限次重试
- Relay pending-list 恢复
- 数据库唯一索引兜底
- traceId 全链路日志

这说明当前链路已经从“能跑”升级到了“具备基础可靠性”。

接下来要做的，不再是简单打通链路，而是继续把它往 **可恢复、可治理、可运维、可面试深讲** 的方向推进。

本计划主要覆盖四块：

1. 死信 Topic
2. 失败任务表
3. 补偿任务
4. 监控与对账

### 1.1 当前进展同步（2026-04-20）

基于本计划，当前代码已经落地了以下内容：

- 已完成：`seckill-order-dlt` 死信 Topic 处理链路（重试超限自动转死信）
- 已完成：死信消息落 `tb_voucher_order_fail_task`
- 已完成：失败任务状态流转（`RETRY_PENDING -> RETRYING -> DONE / MANUAL_HANDLE_REQUIRED`）
- 已完成：失败任务自动重试定时任务（按批次回投主 Topic）
- 已完成：失败任务人工处理接口（分页查询、手动重试、人工忽略）

**可运维增强（2026-04-21）已落地最小实现（日志级告警/巡检）：**

- Kafka：`KafkaLagMonitorScheduler` 定时通过 `AdminClient` 汇总主 Topic + 消费组的 **lag**，超过 `app.kafka.monitor.lag-threshold` 打 **WARN**（配置见 `application.yaml` 的 `app.kafka.monitor`）。
- Redis / MySQL：`SeckillStockReconcileScheduler` 定时比对 `seckill:stock:{voucherId}` 与 `tb_seckill_voucher.stock`，不一致或 DB 仍有库存但 Redis 无键时 **WARN**（`app.seckill.reconcile`）。

大盘可视化（Prometheus/Grafana）与「死信量 / 重试成功率」指标联动仍为可选增强。

**补充（2026-04-21）**：已实现消费侧异常粗分类与 Redis 预扣幂等回滚（脚本 `seckill_rollback.lua`）、死信落库后自动回滚与 `failure_type` 字段；不可再作为“待完成”项重复跟踪。

---

## 2. 总体实施顺序建议

建议按下面顺序推进：

### 第一阶段：失败隔离

先做：

- 死信 Topic
- Consumer 重试超限后转死信

目标：

- 不让坏消息一直阻塞主消费链路

### 第二阶段：失败可追踪

再做：

- 失败任务表
- 失败消息落库

目标：

- 让失败消息可查询、可分析、可人工处理

### 第三阶段：失败可修复

再做：

- 补偿任务
- 可恢复重试
- 不可恢复回滚 Redis

目标：

- 解决 Redis 已预扣但 MySQL 未落库的不一致问题

### 第四阶段：可观测与治理

最后做：

- Kafka 堆积监控
- 消费延迟监控
- 成功率、失败率、死信数统计
- Redis / MySQL 对账任务

目标：

- 让系统具备持续运维和持续优化能力

---

## 3. 第一阶段：死信 Topic

## 3.1 目的

当前 Consumer 已经具备：

- 手动 ack
- 重试 3 次

但问题是：

- 如果消息始终失败
- 当前还没有一个专门的“最终失败隔离区”

这就意味着：

- 失败消息只能停留在错误日志里
- 不方便后续人工排查和补偿

所以第一阶段要做的是：

- 增加死信 Topic

推荐命名：

- `seckill-order-dlt`

---

## 3.2 要做什么

### 1. 创建死信 Topic

建议创建：

- 主 Topic：`seckill-order`
- 死信 Topic：`seckill-order-dlt`

### 2. 配置 Consumer 重试超限后的处理逻辑

当前 Consumer 已经用：

- `SeekToCurrentErrorHandler`
- `FixedBackOff(1000ms, 3次)`

后续可升级成：

- 重试 3 次仍失败
- 自动把消息发送到 `seckill-order-dlt`

### 3. 死信消息中保留关键信息

至少保留：

- traceId
- orderId
- userId
- voucherId
- 原始 payload
- 异常原因
- 重试次数

---

## 3.3 预期收益

做完后能得到：

1. 坏消息不再长期阻塞主消费链路
2. 失败消息有统一落点
3. 后续补偿和人工排查有数据来源

---

## 3.4 面试表达

可以这样讲：

`我在 Consumer 手动 ack 和有限次重试的基础上，继续增加了死信 Topic。这样做的目的不是简单把失败消息丢掉，而是把主链路无法处理的坏消息隔离出来，避免阻塞正常消费，同时为后续补偿和人工排查保留现场。`

---

## 4. 第二阶段：失败任务表

## 4.1 目的

死信 Topic 能解决“消息隔离”，但还不够。

因为死信 Topic 更像 MQ 层的数据，而不是业务治理层的数据。

如果想让失败消息：

- 可查询
- 可统计
- 可运营
- 可人工处理

那还需要一张失败任务表。

---

## 4.2 建议表结构

推荐新增表，例如：

- `tb_voucher_order_fail_task`

建议字段：

- `id`
- `trace_id`
- `order_id`
- `user_id`
- `voucher_id`
- `topic`
- `raw_payload`
- `error_msg`
- `retry_count`
- `status`
- `next_retry_time`
- `create_time`
- `update_time`

其中 `status` 可以设计成：

- `INIT`
- `RETRYING`
- `SUCCESS`
- `ROLLBACK_SUCCESS`
- `MANUAL_HANDLE_REQUIRED`

---

## 4.3 要做什么

### 1. 定义失败任务实体和表结构

新增：

- Entity
- Mapper
- Service

### 2. Consumer 或死信处理逻辑中落失败任务表

当消息：

- 重试超限
- 进入死信

就同步记录一条失败任务。

### 3. 保证失败任务表本身幂等

建议按：

- `traceId`
- 或 `orderId`

做唯一约束或幂等更新，避免重复插入。

---

## 4.4 预期收益

做完后能得到：

1. 失败消息从“日志可见”升级为“数据库可管理”
2. 后续补偿任务有统一入口
3. 后续还可以做后台管理界面或人工处理流程

---

## 4.5 面试表达

可以这样讲：

`我没有把失败治理停留在死信 Topic，而是继续增加了失败任务表，把消息系统里的失败事件转成业务系统可管理的数据，这样失败消息就不只是日志，而是可查询、可补偿、可人工干预的治理对象。`

---

## 5. 第三阶段：补偿任务

## 5.1 目的

这是秒杀链路里最关键的最终一致性补强。

当前链路是：

- Redis 先预扣库存
- Redis 记录购买标记
- Redis Stream / Kafka 承接异步消息
- Consumer 最终落 MySQL

问题在于：

- 如果后半段失败
- Redis 和 MySQL 就会不一致

最典型的场景是：

- Redis 库存已经减了
- 用户 Redis 购买标记已经写了
- 但 MySQL 没有订单

所以第三阶段的目标就是：

- 把这类失败真正修回来

---

## 5.2 补偿策略

### 策略一：优先重试落库

如果失败原因是暂时性的：

- 数据库抖动
- 网络闪断
- Kafka 短时异常

优先选择：

- 重试落库

因为这时：

- 用户已经收到“已受理”结果
- 继续让订单最终成功更符合业务预期

### 策略二：确认不可恢复后回滚 Redis

如果失败原因明确不可恢复：

- 数据脏了
- 业务规则不允许创建订单
- 人工确认这笔订单不能成立

则执行回滚：

- Redis 库存加回去
- 删除 Redis 购买标记

---

## 5.3 要做什么

### 1. 新增补偿定时任务

定时扫描：

- 死信 Topic 对应任务
- 失败任务表中待处理记录

### 2. 判断任务是否可重试

- 可恢复 -> 重新投递 Kafka 或直接调用业务方法
- 不可恢复 -> 执行 Redis 回滚

### 3. 回滚 Redis 状态

至少回滚：

- `seckill:stock:{voucherId}`
- `seckill:order:{voucherId}` 中对应 `userId`

### 4. 更新失败任务状态

补偿成功后更新状态：

- `SUCCESS`
- `ROLLBACK_SUCCESS`
- `MANUAL_HANDLE_REQUIRED`

---

## 5.4 预期收益

做完后能得到：

1. Redis 预扣和 MySQL 最终落库之间有修复机制
2. 秒杀链路具备真正意义上的最终一致性治理能力
3. 可以回答面试官“Redis 扣了库存但订单失败了怎么办”

---

## 5.5 面试表达

可以这样讲：

`我把补偿分成两个方向。对于暂时性失败，优先通过重试和死信补偿让订单最终成功；对于确认不可恢复的失败，再回滚 Redis 里的预扣库存和购买标记。这样做可以兼顾用户体验和最终一致性。`

---

## 6. 第四阶段：监控与对账

## 6.1 目的

前面三阶段解决的是：

- 出问题时怎么办

第四阶段解决的是：

- 怎么尽早发现问题
- 怎么持续观察系统运行情况

如果没有监控和对账，再好的补偿链路也只能靠“出事后看日志”。

---

## 6.2 监控建议

建议逐步增加以下指标（代码已覆盖 **Consumer lag 汇总 + WARN 阈值**，其余可接指标系统）：

### Kafka 维度

- Topic 堆积量
- Consumer lag（已实现：定时计算 lag，超阈值 WARN 日志）
- 消费重试次数
- 死信数量

### 业务维度

- 秒杀请求成功率
- 秒杀请求失败率
- 重复下单命中次数
- 库存不足命中次数
- 订单最终落库成功率

### 补偿维度

- 待补偿任务数
- 补偿成功数
- 回滚成功数
- 人工介入任务数

---

## 6.3 对账任务建议

建议增加 Redis 与 MySQL 的库存对账（**秒杀库存已与实现对齐**）：

### 对账目标

检查：

- Redis 库存是否与 MySQL 秒杀库存一致（已实现：`SeckillStockReconcileScheduler` 比对 `seckill:stock:{id}` 与 `tb_seckill_voucher.stock`）
- Redis 一人一单标记是否与 MySQL 订单事实一致（仍为可选增强）

### 对账方式

定时任务按券维度扫描：

1. 读取 Redis 当前库存
2. 查询 MySQL 当前库存（与代码一致；订单数对账可后续扩展）
3. 发现异常差异则 **WARN** 日志（接入告警系统时可消费同一条日志或导出指标）

---

## 6.4 预期收益

做完后能得到：

1. 可以提前发现 Kafka 堆积和消费异常
2. 可以发现库存不一致问题
3. 可以支撑后续持续压测和优化

---

## 6.5 面试表达

可以这样讲：

`除了失败任务表和死信补偿，我还会用定时任务看 Kafka consumer lag（超阈值打 WARN）以及 Redis 秒杀库存和 MySQL 是否一致，把问题尽量暴露在指标和日志里，而不是只在出故障后才翻日志。`

---

## 7. 推荐落地优先级

结合当前项目状态，建议按下面顺序实际推进：

### P1

- 死信 Topic（已完成）
- Consumer 重试超限转死信（已完成）

### P2

- 失败任务表（已完成）
- 死信消息落库（已完成）

### P3

- 补偿定时任务（已完成：失败任务自动重试）
- Redis 库存 / 购买标记回滚（已完成：Lua 幂等回滚 + Consumer 不可恢复路径 + 死信落库后回滚；失败任务状态 `ROLLBACK_DONE`）
- 人工补偿入口（已完成：失败任务查询/手动重试/人工忽略接口；忽略时同步尝试 Redis 回滚）

### P4

- Kafka lag 日志阈值巡检（已完成：`KafkaLagMonitorScheduler`）
- 成功率 / 失败率统计（可选：指标导出 / 大盘）
- Redis / MySQL 秒杀库存对账（已完成：`SeckillStockReconcileScheduler`）

---

## 8. 一句话总结

当前 Kafka 秒杀链路的下一阶段目标，不再只是“把消息发出去”，而是继续补齐：

- 失败隔离
- 失败可追踪
- 失败可修复
- 运行可观测

把这四块补齐后，这套链路就会从“基础可靠”进一步升级到“具备完整治理能力”。

---

## 9. 进展追加记录（2026-04-20）

本节为 append-only 追加，不覆盖原计划内容，仅同步当前实际落地进展。

### 9.1 已完成

- 死信 Topic：`seckill-order-dlt`
- Consumer 重试超限后自动转死信
- 死信消息落 `tb_voucher_order_fail_task`
- 失败任务状态流转：`RETRY_PENDING -> RETRYING -> DONE / MANUAL_HANDLE_REQUIRED`
- 自动重试任务：定时扫描 `RETRY_PENDING` 并回投主 Topic
- 人工处理接口：
  - `GET /voucher-order-fail-task/page`
  - `POST /voucher-order-fail-task/{id}/retry`
  - `POST /voucher-order-fail-task/{id}/ignore`
- Kafka consumer lag 定时巡检（超阈值 WARN）：`KafkaLagMonitorScheduler`
- Redis 与 MySQL 秒杀库存定时对账（不一致 WARN）：`SeckillStockReconcileScheduler`

### 9.2 待完成

- 死信量、重试成功率、业务成功率等指标 **大盘**（Prometheus/Grafana 等）与告警联动
- Redis 一人一单与订单事实对账（当前已实现秒杀 **库存** 对账）
- （可选）更细的业务级异常分类（如按错误码路由、与运营策略联动）

### 9.2.1 进展追加（2026-04-21）：lag 与库存对账

- **Kafka lag**：`com.hmdp.config.KafkaLagMonitorScheduler`，依赖 `AdminClient`、`spring.kafka.template.default-topic`、`spring.kafka.consumer.group-id`，阈值与周期：`app.kafka.monitor`。
- **库存对账**：`com.hmdp.config.SeckillStockReconcileScheduler`，周期：`app.seckill.reconcile.interval-ms`。

### 9.3 进展追加（2026-04-21）：Redis 自动回滚 + 异常分类

- **Redis 回滚**：新增 `src/main/resources/seckill_rollback.lua`，在 `sismember` 为真时 `SREM` 一人一单标记并 `INCRBY` 库存，避免重复回滚。
- **回滚服务**：`SeckillRedisRollbackService` 统一执行回滚脚本。
- **异常分类**：`MqExceptionClassifier` + `SeckillOrderException`；`VoucherOrderServiceImpl` 对「获取锁失败」抛可恢复异常、「DB 无库存」抛不可恢复异常。
- **Consumer**：不可恢复时先回滚 Redis 再 `acknowledge`，避免无限重试；可恢复则继续抛出让 `SeekToCurrentErrorHandler` 重试。
- **死信**：`saveDeadLetterTaskAndRollback` 落库后执行回滚，成功则 `status=ROLLBACK_DONE`，失败则 `MANUAL_HANDLE_REQUIRED`；`failure_type=NON_RECOVERABLE`。
- **库表**：`tb_voucher_order_fail_task` 增加 `failure_type`（新库见 `voucher_order_fail_task.sql`；已有库执行 `src/main/resources/db/voucher_order_fail_task_failure_type.sql`）。
