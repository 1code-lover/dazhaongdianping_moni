# Kafka 秒杀链路面试准备文档

## 1. 这套 Kafka 改造在项目里是怎么用的

这个项目里的 Kafka 不是直接替代 Redis，而是和 Redis 分工配合：

- Redis 负责秒杀入口的高并发原子校验
- Redis Lua 负责库存判断、一人一单判断、预扣库存
- Redis Stream 负责把“已通过资格校验的下单请求”先落成一条本地消息
- Relay 组件负责把 Redis Stream 中的消息转发到 Kafka
- Kafka 负责异步削峰、解耦和更可靠的消费恢复
- Consumer 负责真正创建订单、扣减数据库库存、落 MySQL
- MySQL 负责最终业务事实，以订单表和库存扣减结果为准

你可以把它概括成一句话：

`Redis 做前置原子校验，Kafka 做异步削峰，MySQL 做最终一致性落库。`

## 2. 项目里的实际链路

### 2.1 请求入口

接口是：

- `POST /voucher-order/seckill/{id}`

入口代码在 [VoucherOrderController.java](/D:/Java/hm-dianping/src/main/java/com/hmdp/controller/VoucherOrderController.java:1) 和 [VoucherOrderServiceImpl.java](/D:/Java/hm-dianping/src/main/java/com/hmdp/service/impl/VoucherOrderServiceImpl.java:1)。

用户发起秒杀请求后，服务端会：

1. 获取当前用户 ID
2. 生成全局订单 ID
3. 执行 Redis Lua 脚本
4. Lua 校验通过后，直接返回“抢购成功，订单已受理”

这里要特别强调一个点：

`接口返回成功，表示请求已经通过 Redis 原子校验并成功写入 Redis Stream，属于“已受理”，不代表订单已经同步落库完成。`

真正的订单创建，是后续通过 `Redis Stream -> Relay -> Kafka -> Consumer -> MySQL` 这条异步链路完成的。

所以当面试官问“为什么 Kafka 挂了，接口还返回成功”时，你可以回答：

`因为我的接口成功语义定义的是受理成功，不是最终完成成功。秒杀入口只对 Redis 原子校验和消息入流负责，最终订单落库由后续异步链路完成。这样做的好处是不会把 Kafka 可用性直接绑定到接口可用性上。`

这里的核心点是：秒杀请求不会在接口线程里直接落库，而是尽量快速返回，把真正的订单处理异步化。

### 2.2 Lua 脚本做了什么

Lua 脚本见 [seckill.lua](/D:/Java/hm-dianping/src/main/resources/seckill.lua:1)。

它做了三件关键事：

1. 判断 Redis 里的秒杀库存是否充足
2. 判断用户是否已经下过单
3. 如果校验通过，就预扣 Redis 库存、记录下单用户，并 `XADD stream.orders`

这里的意义是：

- 库存校验和一人一单校验都在 Redis 内原子执行
- 能在真正访问数据库前拦掉大量无效请求
- 通过 `XADD` 把“成功受理”的下单请求写进 Redis Stream，避免接口线程直接做重活

### 2.3 Redis Stream 到 Kafka 的桥接

桥接组件在 [RedisStreamToKafkaRelay.java](/D:/Java/hm-dianping/src/main/java/com/hmdp/mq/RedisStreamToKafkaRelay.java:1)。

它的职责是：

- 作为 Redis Stream 消费组消费者，持续读取 `stream.orders`
- 把 Stream 消息转换成 `VoucherOrderMessage`
- 调用 Producer 转发到 Kafka Topic
- 只有 Kafka 发送成功后，才 ACK Redis Stream 消息

这一步非常适合面试讲，因为它解决的是“Redis 已经预扣库存了，但消息怎么可靠地进入 Kafka”这个问题。

你可以把它描述成：

`我在 Redis Stream 和 Kafka 之间加了一个 Relay，Kafka 发送成功后才 ACK Stream，用它把秒杀入口和 Kafka 解耦，同时降低 Redis 到 Kafka 之间的丢消息风险。`

### 2.4 Kafka Producer 怎么发消息

Producer 接口和实现分别在：

- [VoucherOrderProducer.java](/D:/Java/hm-dianping/src/main/java/com/hmdp/mq/VoucherOrderProducer.java:1)
- [VoucherOrderProducerImpl.java](/D:/Java/hm-dianping/src/main/java/com/hmdp/mq/impl/VoucherOrderProducerImpl.java:1)

当前实现里：

- Topic 默认是 `seckill-order`
- 消息体是 `VoucherOrderMessage` 的 JSON
- Kafka message key 使用 `userId`

使用 `userId` 作为 key 的好处是：

- 同一用户的消息更容易落到同一个分区
- 有助于降低同一用户重复请求带来的乱序影响
- 便于面试时解释“按业务维度做分区 key 设计”

### 2.5 Kafka Consumer 怎么消费

Consumer 在 [VoucherOrderConsumer.java](/D:/Java/hm-dianping/src/main/java/com/hmdp/mq/VoucherOrderConsumer.java:1)。

它负责：

1. 监听 Kafka Topic
2. 把 JSON 反序列化成 `VoucherOrderMessage`
3. 转成 `VoucherOrder`
4. 调用 `voucherOrderService.handleVoucherOrderFromMQ(order)`

真正落库逻辑还是在 [VoucherOrderServiceImpl.java](/D:/Java/hm-dianping/src/main/java/com/hmdp/service/impl/VoucherOrderServiceImpl.java:1) 里，核心包括：

- 按用户维度加分布式锁
- 校验是否重复下单
- 扣减数据库库存，条件是 `stock > 0`
- 保存订单

所以整条链路里，Kafka 负责异步传输，真正的业务幂等和最终一致性还是靠 Consumer 侧业务逻辑和数据库约束兜底。

## 3. 为什么这个项目要引入 Kafka

你不要把它讲成“我把 Redis Stream 替换成 Kafka”，更准确的说法是：

`我把秒杀系统升级成了 Redis 前置原子校验 + Kafka 异步削峰 + MySQL 最终落库的链路。`

引入 Kafka 的核心动机有四个：

1. 提高异步削峰能力
2. 提高消息积压和消费恢复能力
3. 让消息链路更适合扩展多个 Consumer 实例
4. 让面试表达从“线程池异步”升级成“可讲可靠性和可恢复性的消息架构”

## 4. Kafka 在这个项目里的职责边界

这是面试里最容易被问的点。

### Kafka 负责什么

- 异步化
- 削峰填谷
- 解耦下单入口和订单落库
- 支持消费重试和服务重启后的消息恢复

### Kafka 不负责什么

- 不负责秒杀资格校验
- 不负责库存原子扣减
- 不负责业务幂等
- 不直接保证数据库最终一致性

如果面试官问“为什么 Redis 还要保留”，你可以回答：

`因为 Kafka 擅长异步传输和削峰，但不适合直接做高并发秒杀资格校验。秒杀最核心的是库存判断和一人一单，这两个操作必须在入口阶段快速、原子地完成，所以我保留了 Redis + Lua。`

## 5. 这套方案的优点

### 5.1 性能层面

- 秒杀请求可以快速返回，不阻塞在数据库事务上
- Redis 先拦截无库存和重复下单请求，减少无效流量
- Kafka 把高并发下单请求异步化，降低 DB 峰值写压力

### 5.2 架构层面

- 入口校验和订单落库解耦
- 更容易横向扩展消费端
- 服务重启后消息恢复能力比本地线程池更强

### 5.3 面试表达层面

- 能讲清楚 Redis、Kafka、MySQL 三者分工
- 能延伸到幂等、重试、死信、最终一致性
- 比“只是用线程池异步下单”更有工程说服力

## 6. 当前实现已经具备的亮点

结合现有代码，你可以明确说已经做了这些：

1. Kafka 已经接入 Spring Boot，见 [application.yaml](/D:/Java/hm-dianping/src/main/resources/application.yaml:1)
2. Producer 已经封装，消息模型已独立抽象
3. Consumer 已经可以监听 Topic 并异步执行业务
4. Redis Stream 到 Kafka 的 Relay 已经存在
5. Relay 是“发送 Kafka 成功后再 ACK Redis Stream”
6. 秒杀入口仍然保留 Redis Lua 的原子校验
7. Consumer 侧仍保留 Redisson 锁 + DB 库存条件扣减
8. Consumer 已改成手动 ack，业务成功后才提交 offset
9. Kafka Consumer 已接入有限次重试（`SeekToCurrentErrorHandler + FixedBackOff`）
10. Relay 启动时会恢复 Redis Stream pending-list
11. 链路日志已经补充 `traceId / orderId / userId / voucherId`
12. 数据库唯一索引脚本已经补充，作为最终幂等兜底
13. 已经验证过：Consumer 失败时不会立即 ack，而是会按 offset 重试
14. 已经验证过：Kafka 不可用时，接口仍可返回“已受理”，但 Relay 不应误判为 Kafka 已成功确认
15. 已完成：重试超限消息进入死信 Topic（`seckill-order-dlt`）
16. 已完成：死信消息落失败任务表 `tb_voucher_order_fail_task`
17. 已完成：失败任务自动重试（定时任务扫描 `RETRY_PENDING` 并回投主 Topic）
18. 已完成：失败任务管理接口（分页查询/手动重试/人工忽略）

## 7. 当前实现还可以继续补强的点

这部分你面试里反而会加分，因为说明你知道方案边界。

### 7.1 Kafka Producer 可靠性还可以继续增强

当前配置已经打开了：

- `acks=all`
- `retries=3`
- `enable.idempotence=true`

当前代码层面也已经做了：

- Producer 发送时同步等待 broker ack
- 只有真正拿到 Kafka send result 才返回 success
- 成功日志会打印 `traceId / orderId / userId / voucherId / partition / offset`
- 失败日志会明确打印发送失败

这一步非常关键，因为它避免了“只是把消息提交给客户端发送线程，就误判为 Kafka 发送成功”的问题。

当前还可以继续补：

- 发送失败落本地失败表或重试队列
- 更明确的 Topic 分区规划
- 对 Broker 长时间不可用场景增加失败治理策略

### 7.2 Kafka Consumer 可靠性还可以增强

当前代码里 Consumer 用的是 `@KafkaListener`，并且已经完成了：

- 手动提交 offset
- 仅在业务成功后 `ack.acknowledge()`
- 使用 `SeekToCurrentErrorHandler + FixedBackOff(1000ms, 3次)` 做有限次重试

这意味着当前已经不是“建议改手动提交”，而是已经落地了基础版本。

进一步的工程化增强还可以包括：

- 区分可恢复异常和不可恢复异常（按异常类型路由）
- 增加消费时延、重试次数、死信量监控
- 增加失败任务重试退避策略（指数退避、熔断窗口）

### 7.3 数据库幂等还可以更强

当前业务里已经有：

- 用户维度分布式锁
- 查询重复订单
- `stock > 0` 条件扣减

更稳的做法是：

- 在订单表增加唯一索引 `unique(user_id, voucher_id)`

这会让你在回答“怎么防止重复消费导致重复订单”时更硬。

### 7.4 最终一致性补偿还可以继续做

最典型的问题是：

- Redis 里库存已经预扣
- 但 Kafka 或 Consumer 最终落库失败

当前已经具备的最小恢复能力包括：

- Consumer 失败后不 ack，通过 offset 做重试
- Relay 启动时恢复 Redis Stream pending-list

这说明当前方案已经具备“失败后不立即丢消息”的基础保障。

与最新代码对齐时，可以区分「已落地」和「仍可演进」：

- 死信、失败任务、补偿重试与 Redis 幂等回滚：已落地
- Redis 秒杀库存与 MySQL `tb_seckill_voucher.stock` 定时对账：`SeckillStockReconcileScheduler`（不一致打 WARN）
- Kafka consumer lag 巡检：`KafkaLagMonitorScheduler`（lag 超阈值打 WARN）
- 仍可演进：一人一单与订单事实对账、指标大盘与告警平台对接

## 7.5 为什么建议把 Kafka Consumer 改成手动确认

这是你面试里非常值得讲的一点，因为它体现的是你对消息可靠性的理解，而不是只会把 Kafka 跑起来。

### 当前配置是什么

当前项目配置在 [application.yaml](/D:/Java/hm-dianping/src/main/resources/application.yaml:1) 里，可以看到：

- `spring.kafka.consumer.enable-auto-commit=true`

这意味着 Consumer offset 会由 Kafka 客户端自动提交，而不是在业务成功后由我们自己控制提交时机。

### 自动提交有什么问题

自动提交最大的问题是：

`消息是否真正处理成功` 和 `offset 是否已经提交` 不是强绑定的。

也就是说，可能出现下面这种情况：

1. Consumer 已经从 Kafka 拉到了消息
2. Kafka 客户端按自动提交策略把 offset 提交了
3. 但此时业务代码还没真正执行成功，或者刚执行一半服务就挂了
4. 因为 offset 已经提交，Consumer 重启后这条消息不会再被重新消费
5. 最终结果就是：消息丢了，Redis 已经预扣库存，但 MySQL 没有成功创建订单

对于秒杀系统来说，这类问题非常致命，因为它会直接影响最终一致性。

### 为什么秒杀场景更适合手动确认

因为秒杀链路不是“消费到消息就算成功”，而是必须满足下面条件之后，才算这条消息真正完成：

1. Kafka 消息成功反序列化
2. `VoucherOrder` 对象构建成功
3. 订单业务逻辑执行成功
4. 数据库库存扣减成功
5. 订单写入 MySQL 成功
6. 没有触发重复下单、库存不足等异常分支

只有这些都完成了，才应该提交 offset。

你在面试里可以这么说：

`秒杀场景里我不会让 Kafka 自动提交 offset，因为消息被拉取不代表订单已经真正落库。更合理的做法是把 offset 提交时机后移到业务成功之后，这样才能避免消息已确认但订单未落库的丢单问题。`

### 手动确认的核心思想

手动确认其实很简单，本质就是：

- Kafka 负责把消息投递给你
- 你自己决定什么时候告诉 Kafka“这条消息我处理完了”

也就是：

- 业务成功 -> `ack.acknowledge()`
- 业务失败 -> 不提交 offset，让 Kafka 后续重试，或者进入失败处理链路

## 7.6 手动确认的修改方案

下面这部分建议你面试时按“配置层、消费层、失败处理层”三个层次讲，会显得非常完整。

### 第一层：修改 Consumer 提交模式

把 Kafka Listener 容器改成手动确认模式。

常见做法是配置 `AckMode.MANUAL` 或 `AckMode.MANUAL_IMMEDIATE`。

两者区别：

- `MANUAL`：调用 `ack.acknowledge()` 后，由容器按批次或时机提交
- `MANUAL_IMMEDIATE`：调用 `ack.acknowledge()` 后立即提交

对于秒杀订单这种更强调处理结果确定性的场景，我更建议：

- `MANUAL_IMMEDIATE`

因为这样更容易讲清楚“业务成功后立刻确认”。

### 第二层：修改 Consumer 方法签名

当前 Consumer 在 [VoucherOrderConsumer.java](/D:/Java/hm-dianping/src/main/java/com/hmdp/mq/VoucherOrderConsumer.java:1) 中，大致是：

1. 收到 `payload`
2. 反序列化成 `VoucherOrderMessage`
3. 转成 `VoucherOrder`
4. 调用业务方法处理

改成手动确认后，Consumer 方法要增加一个 `Acknowledgment ack` 参数。

逻辑改成：

1. 反序列化消息
2. 调用订单处理逻辑
3. 如果业务成功，则执行 `ack.acknowledge()`
4. 如果失败，则抛异常，不提交 offset

伪代码如下：

```java
@KafkaListener(topics = "seckill-order", containerFactory = "manualAckKafkaListenerContainerFactory")
public void onMessage(String payload, Acknowledgment ack) {
    VoucherOrderMessage msg = objectMapper.readValue(payload, VoucherOrderMessage.class);
    VoucherOrder order = convert(msg);
    voucherOrderService.handleVoucherOrderFromMQ(order);
    ack.acknowledge();
}
```

核心点就一句话：

`只有 handleVoucherOrderFromMQ(order) 真正执行成功后，才 acknowledge。`

### 第三层：失败时不确认，让 Kafka 重试

如果消费失败，不要调用 `ack.acknowledge()`。

通常做法是：

- 记录错误日志
- 抛出异常
- 让 Spring Kafka 的错误处理机制接管

这样消息不会被当成“已完成”，而是会进入重试逻辑。

你面试时可以这么讲：

`我把 offset 提交和业务成功绑定起来，如果订单落库失败，我就不确认 offset，而是交给 Kafka 的重试机制处理，避免消息提前确认导致丢单。`

## 7.7 手动确认之后，还要配什么

只改手动确认还不够，完整方案还要把“重试、死信、幂等”一起讲出来。

### 1. 有限次重试

不能无限重试，否则一条坏消息会长期阻塞消费。

建议：

- 本地重试 3 次
- 每次退避一段时间
- 超限后进入死信 Topic

### 2. 死信队列

如果某条消息始终消费失败，比如：

- 数据格式错误
- 业务数据脏数据
- 下游依赖长期异常

就把它投递到死信 Topic，例如：

- `seckill-order-dlt`

这样主消费链路不会一直卡住，同时又保留了后续人工排查和补偿的机会。

### 3. 数据库唯一索引兜底

就算有了手动确认，也不能假设 Kafka 一定只投递一次。

因为以下情况都可能导致重复消费：

- Consumer 业务成功了，但 ack 提交前进程挂了
- Kafka 在异常边界下重复投递
- 手动补偿时重复重放消息

所以数据库仍然建议增加唯一索引：

- `unique(user_id, voucher_id)`

这样你在面试时就能讲成“三层幂等”：

1. Redis Lua：入口防重复
2. Consumer 业务校验：消费侧防重复
3. DB 唯一索引：最终兜底

## 7.8 面试时怎么回答“为什么要手动确认”

### 简版回答

`因为自动提交 offset 会导致消息是否确认和业务是否成功解耦。对于秒杀订单来说，消息被拉取不代表订单已经成功落库，所以我会改成手动确认，只有在订单创建成功后才提交 offset，避免丢单。`

### 进阶版回答

`我这个秒杀链路是 Redis 预扣库存、Kafka 异步削峰、MySQL 最终落库。这里最怕的问题是 Redis 已经扣了库存，但 Kafka 消息却因为消费失败没有成功落库。如果 offset 还是自动提交，就会出现消息已经确认但订单没创建成功的情况。为了把消息确认时机和业务成功强绑定，我会把 Consumer 改成手动 ack，业务成功再提交 offset；失败则不确认，让 Kafka 重试，重试超限再进入死信队列。同时再用数据库唯一索引兜底重复消费。`

## 7.9 面试官可能继续追问的问题

### 1. 手动确认是不是就绝对不会重复消费了

不是。

手动确认解决的是“过早确认导致丢单”的问题，不是“绝不重复投递”的问题。

Kafka 的设计本身更接近至少一次投递，所以消费侧仍然要做幂等。

### 2. 为什么不在消费前先 ack，成功后再补偿

因为这样会把风险提前释放。

对于秒杀订单，核心目标是：

- 宁可重复消费后被幂等拦住
- 也不能提前确认后造成真实丢单

所以更合理的是“先执行业务，后确认消息”。

### 3. 手动确认会不会降低吞吐

会有一点点影响，但这是值得的。

因为秒杀系统最怕的不是少一点吞吐，而是订单数据不一致、库存已经扣了但订单没创建成功。

在这种场景下，可靠性优先级通常高于极致吞吐。

## 7.10 推荐落地顺序

如果后面真的继续改代码，建议按下面顺序实施：

1. Consumer 改手动 ack
2. 业务成功后再提交 offset
3. 增加 Kafka 消费失败重试
4. 增加死信 Topic
5. 增加数据库唯一索引 `user_id + voucher_id`
6. 增加补偿任务，处理 Redis 预扣成功但 MySQL 落库失败的场景

你可以把这一组改造总结成一句话：

`我后续会把 Kafka 消费从自动提交升级成业务成功后手动确认，再配合重试、死信和数据库唯一索引，把秒杀订单链路从“能跑”提升到“可靠可讲”。`

## 7.10 数据库唯一索引、死信队列与补偿方案

如果你面试只讲“我用了 Kafka”，其实很容易停留在会用框架的层面。

真正能体现工程深度的，是你有没有把下面三个问题讲清楚：

1. 重复消费怎么办
2. 多次消费失败怎么办
3. Redis 已预扣库存但 MySQL 没落库怎么办

这三个问题分别对应：

- 数据库唯一索引
- 死信队列
- 一致性补偿

### 7.10.1 为什么还要加数据库唯一索引

很多同学会以为已经有了：

- Redis Lua 一人一单
- Consumer 侧重复下单校验
- Redisson 锁

就已经足够了。

但从“最终一致性”和“消息重复投递”的角度看，这还不够。

因为 Kafka 消费模型本质上更接近至少一次投递，以下情况都可能导致同一条订单消息被重复处理：

1. Consumer 业务成功，但 ack 前进程挂了
2. Kafka 发生重复投递
3. 死信消息被人工重放
4. 补偿任务重复执行
5. 多实例并发消费边界下出现重复进入业务逻辑

如果这时候只有应用层判重，没有数据库兜底，那么最坏情况仍然可能出现重复订单。

所以数据库唯一索引的作用不是替代 Redis 和 Consumer 判重，而是做最终防线。

### 7.10.2 唯一索引应该怎么设计

推荐在订单表 `tb_voucher_order` 上增加联合唯一索引：

- `unique(user_id, voucher_id)`

它表达的业务含义非常明确：

- 同一个用户
- 对同一张优惠券
- 最多只能生成一笔订单

面试时你可以这样说：

`我把一人一单做成三层保障：Redis Lua 在入口拦截，Consumer 业务逻辑里再判重，数据库层再通过 unique(user_id, voucher_id) 做最终兜底。这样即使 Kafka 发生重复消费，也不会生成重复订单。`

### 7.10.3 唯一索引落地 SQL

你可以准备下面这段 SQL：

```sql
ALTER TABLE tb_voucher_order
ADD CONSTRAINT uk_user_voucher UNIQUE (user_id, voucher_id);
```

如果表里已经有历史脏数据，正式执行前要先排查重复数据。

### 7.10.4 有了唯一索引后，Consumer 应该怎么处理

加了唯一索引之后，不代表应用层判重就不要了。

更合理的做法是：

1. 先做业务判重，减少无意义数据库异常
2. 如果极端情况下还是插入重复
3. 捕获唯一索引冲突异常
4. 把它识别成“幂等命中”，而不是系统故障

也就是说，在面试里你要体现一个思想：

`唯一索引不是为了替代应用逻辑，而是为了在异常边界下兜底。`

## 7.11 为什么需要死信队列

手动确认 + 重试已经能解决一部分问题，但还有一种场景：

- 某条消息就是处理不了

比如：

- 消息体字段缺失
- JSON 结构错误
- 对应优惠券数据已经损坏
- 下游依赖异常持续很久
- 消费代码遇到不可恢复异常

如果这类消息一直重试，就会带来两个问题：

1. 反复报错，占用消费资源
2. 阻塞后续正常消息

所以要引入死信队列，把“短期无法成功处理的消息”隔离出去。

### 7.11.1 死信队列在这套秒杀架构里的作用

死信队列不是为了让失败消息消失，而是为了：

- 不阻塞主消费链路
- 保留失败消息现场
- 支持后续人工排查
- 支持补偿任务或人工重放

推荐死信 Topic：

- `seckill-order-dlt`

其中 `dlt` 表示 dead letter topic。

### 7.11.2 推荐的消费失败处理顺序

你可以把失败处理流程讲成下面这样：

1. Kafka Consumer 收到秒杀订单消息
2. 执行业务处理
3. 如果失败，先进行有限次重试
4. 重试仍失败，则不再阻塞主链路
5. 将消息投递到死信 Topic
6. 死信消费者落失败任务表并标记 `RETRY_PENDING`
7. 定时任务自动重试，成功后更新为 `DONE`
8. 超过阈值更新为 `MANUAL_HANDLE_REQUIRED`，走人工处理

这个表达会让面试官觉得你不是只会“失败就打日志”，而是有完整失败治理思路。

### 7.11.2.1 当前项目已落地的处理动作

当前版本已经具备以下治理闭环，可直接在面试中陈述：

- 自动链路：`主消费失败 -> DLT -> 失败任务表 -> 定时重试 -> DONE`
- 人工链路：`MANUAL_HANDLE_REQUIRED -> 手动重试/人工忽略`
- 管理接口：
  - `GET /voucher-order-fail-task/page`
  - `POST /voucher-order-fail-task/{id}/retry`
  - `POST /voucher-order-fail-task/{id}/ignore`

### 7.11.2.2 面试官追问：你怎么观测 Kafka 堆积？Redis 和 DB 库存怎么对账？

**Kafka lag：** 用 `AdminClient` 拉取主 Topic 各分区的 **latest offset**，再读消费组已提交 offset，分分区做 `end - committed` 后汇总。项目里由 `KafkaLagMonitorScheduler` 周期执行，超过 `app.kafka.monitor.lag-threshold` 打 **WARN**，便于接入日志告警或后续接 Prometheus。

**库存对账：** 秒杀场景以 `tb_seckill_voucher` 为库存事实源之一，定时比对 Redis 键 `seckill:stock:{voucherId}` 与表中 `stock`；DB 仍大于 0 但 Redis 无键、或两边数值不一致，打 **WARN**。注意异步窗口内有短暂不一致可能，对账更适合做「持续巡检」而非毫秒级强一致证明。

### 7.11.3 重试次数怎么设计

推荐思路：

- 即时重试 3 次
- 指数退避或固定退避
- 超过阈值进入死信 Topic

为什么不无限重试？

因为无限重试会放大坏消息的破坏力，让整个消费线程长期被一条异常消息拖住。

你可以这样回答：

`我会把异常分成可恢复异常和不可恢复异常。可恢复异常做有限次重试，不可恢复异常或者重试超限则进入死信 Topic，避免一条坏消息拖垮整个消费链路。`

## 7.12 Redis 已预扣库存但订单落库失败，怎么补偿

这是秒杀项目里最能体现你理解深度的问题之一。

因为你的链路是：

1. Redis Lua 先预扣库存
2. Redis Stream 记录受理消息
3. Relay 转发 Kafka
4. Kafka Consumer 最终落 MySQL

这意味着：

- Redis 上的变化发生在前
- MySQL 上的最终业务落库发生在后

如果后半段失败，就会出现数据不一致。

### 7.12.1 典型不一致场景

最典型的是：

1. Lua 校验通过
2. Redis 库存已减 1
3. 用户购买标记已写入 Redis
4. 消息已经进入 Stream/Kafka
5. 但 Consumer 落库失败
6. 最终 MySQL 没有订单，数据库库存也没真正对应扣减成功

这时如果不补偿，就会出现：

- Redis 库存比真实库存更少
- 用户在 Redis 中被标记成“已购买”
- 但数据库实际上并没有订单

这会直接影响后续业务正确性。

### 7.12.2 补偿的目标是什么

补偿不是简单“重试一下”，而是要恢复系统业务一致性。

目标一般有两个方向：

#### 方向一：重试落库成功

即：

- 保留当前 Redis 状态不变
- 继续把失败消息重试或重放
- 最终让 MySQL 成功创建订单

这种方案适合：

- 失败原因是暂时性的
- 比如数据库短时抖动、网络闪断、下游依赖瞬时异常

#### 方向二：回滚 Redis 侧预扣结果

即：

- Redis 库存加回去
- 删除用户购买标记
- 把这次秒杀资格恢复掉

这种方案适合：

- 明确无法成功落库
- 比如数据脏了、消息非法、业务规则判定必须失败

### 7.12.3 生产上更常见的思路

生产里更常见的是：

- 先尽量重试落库
- 重试超限后再进入补偿逻辑

原因很简单：

- 秒杀请求本来就已经向用户返回“受理成功”
- 从用户体验上，优先保证订单最终成功通常更合理
- 只有明确无法完成时，才考虑回滚 Redis 资源

所以你可以面试里这么讲：

`对于 Redis 预扣成功但 MySQL 落库失败的情况，我会优先走重试和死信补偿，尽量让订单最终创建成功；如果确认无法成功，再执行库存和购买标记的回滚补偿。`

## 7.13 补偿方案怎么设计

### 7.13.1 补偿数据来源

补偿任务至少要能拿到这些信息：

- orderId
- userId
- voucherId
- traceId
- 失败原因
- 失败次数
- 首次失败时间
- 最后一次失败时间

这些数据可以来自：

- 死信 Topic
- 失败任务表
- 消费错误日志归集

更推荐的是：

- 死信 Topic + 失败任务表结合

这样既有消息链路，也有可查询可运营的数据视图。

### 7.13.2 补偿任务的执行方式

可以设计一个定时任务，周期性扫描失败任务或死信消息，然后：

1. 判断是否还能重试
2. 如果可以，重新投递 Kafka 或直接执行业务补偿
3. 如果确认不可恢复，则回滚 Redis 预扣库存和购买标记
4. 记录补偿结果

### 7.13.3 Redis 回滚要回滚什么

如果最终决定回滚，就至少要处理两部分：

1. 库存 Key
2. 用户已购买标记

也就是把 Lua 中成功写入的内容反向撤销。

你面试时可以这样说：

`补偿不是只补 MySQL，还要把 Redis 里的预扣库存和一人一单标记一起考虑，否则会出现库存和购买资格被长期错误占用。`

### 7.13.4 补偿时最容易忽略的问题

补偿流程本身也要幂等。

比如：

- 同一条死信被重复处理
- 补偿任务执行到一半中断
- 回滚 Redis 后又被再次回滚

所以补偿也建议有状态机，例如：

- `INIT`
- `RETRYING`
- `SUCCESS`
- `ROLLBACK_SUCCESS`
- `MANUAL_HANDLE_REQUIRED`

这样你在面试里就能体现“补偿任务自身也需要可控、可恢复、可幂等”。

## 7.14 这三件事放在一起，面试怎么讲

你可以把“唯一索引 + 死信 + 补偿”总结成一套完整可靠性方案：

`Kafka 只是把消息异步化了，但真正让秒杀链路可靠，还要补三层能力：第一层是 Consumer 成功后手动提交 offset，避免提前确认；第二层是数据库唯一索引兜底重复消费，避免重复订单；第三层是失败重试、死信队列和补偿任务，处理 Redis 已预扣但 MySQL 落库失败的最终一致性问题。这样整条链路才从“能跑”升级成“可恢复、可兜底、可解释”。`

## 7.15 面试官追问示例

### 1. 已经有 Redis 一人一单了，为什么还要数据库唯一索引

答：

`Redis 的一人一单主要解决入口高并发拦截，但 Kafka 消费是异步链路，可能出现重复投递、重复消费和补偿重放。数据库唯一索引是最终防线，用来兜住所有应用层漏网情况。`

### 2. 为什么需要死信，而不是一直重试

答：

`因为一直重试会让坏消息长期阻塞主链路。更合理的做法是有限次重试，超过阈值进入死信 Topic，把失败消息隔离出来，后续再人工排查或自动补偿。`

### 3. Redis 预扣成功但 MySQL 失败，为什么不立刻回滚

答：

`因为很多失败是暂时性的，比如数据库抖动、网络瞬时异常，直接回滚会让订单链路反复震荡。更合理的是先重试和死信补偿，尽量让订单最终成功，只有确认不可恢复时再回滚 Redis。`

## 7.15 Kafka、RabbitMQ、RocketMQ 怎么对比

这部分在面试里非常常见，尤其当你已经讲了“我在秒杀项目里用了 Kafka”之后，面试官很容易追问：

- 为什么选 Kafka，不选 RabbitMQ？
- 为什么不是 RocketMQ？
- 如果换成 RocketMQ / RabbitMQ 行不行？

你要注意，这类问题不是在考你背八股，而是在考你有没有“结合业务场景做技术选型”的能力。

### 7.15.1 先给一句总回答

你可以先用一句总括回答把方向定住：

`这三种消息队列都能做异步削峰，但如果结合秒杀这种高吞吐、可堆积、可恢复的场景，我更倾向于 Kafka；如果更强调灵活路由和传统业务系统集成，RabbitMQ 会更合适；如果更强调消息可靠性、顺序、延时和事务消息能力，RocketMQ 也很适合做订单场景。我的这个项目之所以选 Kafka，是因为我想突出高吞吐削峰、消费恢复和可扩展消费能力。`

这句话好处是：

- 先承认三者都能用
- 再强调是“结合场景做选择”
- 最后把话题拉回你当前项目的技术决策

## 7.16 Kafka、RabbitMQ、RocketMQ 的核心差异

### 7.16.1 Kafka 的特点

Kafka 最突出的标签是：

- 高吞吐
- 分区模型清晰
- 对消息堆积和消费恢复很强
- 适合日志、埋点、异步削峰、大流量消息处理

Kafka 的优势在于：

1. 吞吐量高，特别适合高并发大流量场景
2. 消息持久化和顺序写磁盘机制比较强
3. Consumer 组扩展能力强，适合横向扩容
4. 对消息积压的承受能力通常比较好
5. 很适合做“高峰流量先接住，再慢慢消费”的模型

Kafka 的短板也要知道：

1. 它不是以复杂路由见长
2. 业务语义不像 RabbitMQ 那么灵活
3. 传统业务里如果要做很多精细投递控制，开发体验不一定最好
4. 事务消息、延迟消息这类能力不是它最突出的一面

### 7.16.2 RabbitMQ 的特点

RabbitMQ 最突出的标签是：

- 协议成熟
- 路由灵活
- 交换机模型丰富
- 适合传统业务系统异步解耦

RabbitMQ 的优势在于：

1. Exchange + Queue + Binding 模型很灵活
2. 路由能力强，适合复杂业务分发
3. 社区成熟，很多企业业务系统都在用
4. 对低延迟、小规模业务异步场景很友好

RabbitMQ 的短板：

1. 极高吞吐场景下一般不如 Kafka
2. 面对超大规模消息堆积时，扩展性和处理模型通常不如 Kafka 自然
3. 如果你要讲秒杀削峰和海量消息承压，RabbitMQ 的说服力一般不如 Kafka 或 RocketMQ

### 7.16.3 RocketMQ 的特点

RocketMQ 最突出的标签是：

- 业务消息能力强
- 顺序消息、延迟消息、事务消息都比较适合电商场景
- 对订单、交易、通知类业务有很强工程适配性

RocketMQ 的优势通常包括：

1. 更贴近业务型消息队列
2. 顺序消息支持更容易讲
3. 延时消息、事务消息在业务系统里很有代表性
4. 在订单、支付、库存类场景里很常见

RocketMQ 的短板也要会说：

1. 社区生态和通用性上，很多场景下 Kafka 更常见
2. 如果场景重点是超大吞吐、日志流、埋点流、海量异步削峰，Kafka 往往更自然
3. 如果你的项目本身并没有用到事务消息和复杂延时语义，RocketMQ 的优势不一定完全发挥出来

## 7.17 结合秒杀场景，三者怎么选

你面试时最好不要只横向背特性，而是结合秒杀业务来讲。

### 对秒杀最关键的几个点

秒杀系统最关注的通常是：

1. 高并发流量削峰
2. 消息积压能力
3. 服务重启后的恢复能力
4. 消费侧扩容能力
5. 幂等与最终一致性配合能力

如果按这几个维度去看：

#### Kafka

更适合强调：

- 高吞吐削峰
- 消息积压
- 可扩展消费
- 服务重启后继续消费

#### RabbitMQ

更适合强调：

- 路由灵活
- 业务模块解耦
- 中小规模业务异步通知

#### RocketMQ

更适合强调：

- 电商业务消息
- 顺序消息
- 延时消息
- 事务消息

## 7.18 为什么我这个项目更适合讲 Kafka

你这个项目当前链路是：

- Redis Lua 前置原子校验
- Redis Stream 本地承接受理消息
- Relay 转发 Kafka
- Kafka Consumer 异步落库

从这个设计出发，Kafka 在这里最适合强调的是：

### 1. 高并发削峰能力

秒杀流量会在短时间内集中打进来，Kafka 可以把瞬时高峰流量先承接住，再由 Consumer 平滑处理。

### 2. 消息积压和恢复能力

如果消费端一时处理不过来，Kafka 更适合承接积压。

这点面试里很好讲，因为它比“单机线程池异步下单”更有工程深度。

### 3. Consumer 组扩展能力

如果后续订单量进一步提升，可以通过增加 Consumer 实例和分区数来扩展消费能力。

### 4. 更适合讲可靠性增强

例如：

- 手动 ack
- 死信
- 重试
- 补偿
- 幂等

这些都能和 Kafka 消费模型自然结合起来讲。

所以你可以这么总结：

`我选 Kafka，不是因为它唯一能做秒杀，而是因为这套项目的重点想放在高吞吐削峰、消息积压恢复和可扩展消费能力上，这些点和 Kafka 的特点是比较匹配的。`

## 7.19 如果面试官问：为什么不用 RabbitMQ

这是高频追问。

你可以这样回答：

`RabbitMQ 也能做异步削峰，但它更强的是灵活路由和传统业务解耦。我的这个秒杀项目重点不在复杂路由，而在高峰流量承接、消息积压和消费恢复，所以我更倾向于 Kafka。尤其是秒杀这种突发流量场景，用 Kafka 来承接异步下单请求，更容易讲高吞吐和可扩展消费。`

如果想再进一层，可以补一句：

`如果项目是很多业务模块之间做事件通知、延迟不高、消息量也没那么极端，RabbitMQ 其实会是很合理的选择。`

这样回答会显得你不是在“踩 RabbitMQ”，而是在理性选型。

## 7.20 如果面试官问：为什么不用 RocketMQ

这个问题也非常常见，尤其是 Java 岗和电商业务岗。

你可以这样回答：

`RocketMQ 其实也很适合订单类业务，尤其是顺序消息、延时消息、事务消息这些能力，在电商系统里很有代表性。如果我这个项目重点想突出事务消息或者订单链路里的业务语义能力，RocketMQ 也是不错的选择。之所以这里用 Kafka，是因为我这版改造更想强调高并发削峰、消息积压承接和消费扩展能力。`

如果还想再补强一句：

`换句话说，不是 RocketMQ 不合适，而是我这个项目当前讲法更偏高吞吐异步削峰，所以 Kafka 更贴近我想表达的架构重点。`

## 7.21 如果面试官继续追问：那到底谁更好

正确回答一定不是“某一个绝对更好”，而是：

`没有绝对更好的消息队列，关键看业务侧重点。`

你可以按下面方式回答：

- 如果我要强调高吞吐、积压能力、可扩展消费，我倾向 Kafka
- 如果我要强调灵活路由、业务模块解耦，我倾向 RabbitMQ
- 如果我要强调订单业务能力、顺序消息、延迟消息、事务消息，我倾向 RocketMQ

这就是一个非常成熟的面试回答了。

## 7.22 一张面试可直接背的对比表

### Kafka

- 优势：高吞吐、可堆积、易扩展、适合削峰
- 短板：复杂业务路由能力一般
- 适合：秒杀削峰、日志流、埋点流、大流量异步场景

### RabbitMQ

- 优势：路由灵活、协议成熟、业务解耦友好
- 短板：极高吞吐和超大积压场景通常不如 Kafka
- 适合：中小规模业务异步、通知、复杂路由分发

### RocketMQ

- 优势：业务消息能力强，顺序/延时/事务消息更适合电商
- 短板：如果不需要这些业务能力，优势不一定能完全体现
- 适合：订单、支付、库存、交易型业务链路

## 7.23 你这个项目最推荐的回答模板

### 简版

`RabbitMQ、RocketMQ、Kafka 都能做秒杀异步化，但我这个项目重点想突出高并发削峰、消息积压承接和消费扩展，所以我选了 Kafka；如果更强调复杂路由，我会考虑 RabbitMQ；如果更强调订单业务里的顺序、延时和事务消息，我会考虑 RocketMQ。`

### 进阶版

`我不会把 MQ 选型讲成谁绝对更强，而是看业务重点。我的秒杀项目入口已经用 Redis Lua 解决了原子校验问题，后面的核心诉求是把瞬时高峰流量异步化并平滑消费，所以我更关注吞吐、堆积和消费恢复能力，这些点和 Kafka 更匹配。RabbitMQ 更适合复杂路由型业务，RocketMQ 更适合强调顺序、延时、事务消息的订单链路。如果后续我想把订单链路进一步往事务消息方向升级，RocketMQ 也是很值得考虑的方案。`

## 7.23 这次项目真实排障过程怎么讲

这一部分其实非常有面试价值。

因为很多时候，面试官不只想听“你做了什么功能”，还想知道：

- 项目遇到问题时你怎么定位
- 你能不能区分表象和根因
- 你是怎么一步步把系统从“起不来”修到“能跑通”的

这次你这个项目的排障过程，完全可以整理成一个比较完整的工程案例。

## 7.24 排障背景

在把秒杀链路升级成：

- Redis Lua 原子校验
- Redis Stream 承接受理消息
- Relay 转发 Kafka
- Kafka Consumer 异步落库

之后，项目在启动阶段连续报错，表现为多个 Bean 创建失败。

一开始看上去像是：

- Redis 有问题
- Kafka 有问题
- Bean 注入有问题
- 甚至像是代码逻辑写坏了

但真正排下来，问题是分层出现、连续暴露的。

这正好适合你在面试里讲“我怎么做系统化排障”。

## 7.25 第一阶段：先识别这是编译产物问题，不是业务逻辑问题

最开始的报错集中在这些类：

- `RedisStreamToKafkaRelay`
- `VoucherOrderProducerImpl`
- `VoucherOrderConsumer`
- `SeckillStockPreheatRunner`
- `VoucherOrderMessage`
- `VoucherOrder`
- `SeckillVoucher`

典型异常是：

- `log cannot be resolved`
- `The blank final field ... may not have been initialized`
- `setXxx(...) is undefined`
- `getXxx(...) is undefined`

这类报错有一个非常典型的特征：

- 不是运行期业务异常
- 而是“编译时应该生成的方法和字段没有生成”

我当时的判断是：

`这不是 Kafka 本身的问题，也不是 Redis 逻辑问题，而是 Lombok 相关代码生成在当前构建链路里没有生效，导致 target/classes 里的编译产物不完整。`

### 为什么能判断是 Lombok 问题

因为这些缺失项都高度集中在 Lombok 自动生成的内容上：

- `@Slf4j` 生成的 `log`
- `@RequiredArgsConstructor` 生成的构造器
- `@Data` 生成的 getter/setter

而且错误不是偶发出现在单个类，而是成片出现在多个新接入或依赖 Lombok 的类上。

所以第一步不是去怀疑 Kafka 消息没发出去，而是先把项目编译产物修到可启动。

## 7.26 第二阶段：先把启动链上的 Lombok 依赖类“显式化”

因为当时最紧急的目标不是“研究 IDE 为啥 Lombok 失效”，而是先让项目恢复可运行。

所以处理策略是：

- 对启动链路上直接报错的类
- 去掉关键 Lombok 依赖
- 改成显式构造器、显式 logger、显式 getter/setter

具体做法包括：

### 1. `RedisStreamToKafkaRelay`

处理内容：

- 去掉 `@Slf4j`
- 去掉 `@RequiredArgsConstructor`
- 手写 `Logger`
- 手写构造器

### 2. `VoucherOrderProducerImpl`

处理内容：

- 去掉 `@Slf4j`
- 去掉 `@RequiredArgsConstructor`
- 手写 `Logger`
- 手写构造器

### 3. `VoucherOrderConsumer`

处理内容：

- 去掉 `@Slf4j`
- 去掉 `@RequiredArgsConstructor`
- 手写 `Logger`
- 手写构造器

### 4. `VoucherOrderMessage`

处理内容：

- 去掉 `@Data`
- 手写 getter/setter

### 5. `VoucherOrder`

处理内容：

- 去掉 `@Data`
- 补齐关键 getter/setter，避免 Consumer 组装订单时报错

### 6. `SeckillVoucher`

处理内容：

- 去掉 `@Data`
- 补齐关键 getter/setter，避免库存预热逻辑报错

### 7. `SeckillStockPreheatRunner`

处理内容：

- 去掉 `@Slf4j`
- 手写 `Logger`

### 面试时这一步怎么讲

你可以这么说：

`当时我没有先陷在工具链问题里，而是先按启动链路优先级把关键类显式化，先恢复应用可启动性。这样做的目的是快速止血，让系统先从“完全起不来”恢复到“可以继续验证主链路”。`

这句话会显得你有工程优先级意识。

## 7.27 第三阶段：分清“真正主因”和“伴随异常”

项目修到能继续往下启动之后，又出现了一波异常：

- `RejectedExecutionException: event executor terminated`
- `RedisConnectionException: Unable to connect to Redis server`
- `OutOfMemoryError: Unable to allocate 16777216 bytes`

如果经验不足，很容易把 `RejectedExecutionException` 当主因。

但我当时做了一个判断：

`RejectedExecutionException 只是 Netty/Redisson 在线程池收尾阶段抛出的伴随异常，真正的主因还是 Redisson 创建阶段连 Redis 失败，并且伴随直接内存分配压力。`

也就是说，要学会区分：

- 表面上最先刷出来的异常
- 真正导致 Spring 容器启动失败的根因

### 这一步怎么判断

我主要看了异常链的落点：

- Spring Bean 创建失败最终落到了 `redissonClient`
- `redissonClient` 创建失败最终落到了 `Unable to connect to Redis server`
- 同时异常底层还有 `OutOfMemoryError: Unable to allocate 16777216 bytes`

所以这一阶段的结论不是“Kafka 有问题”，而是：

- Redisson 配置是刚性写死的
- Redis 连接创建阶段存在资源压力
- Netty 报错只是连带现象

## 7.28 第四阶段：优化 Redisson 配置，降低资源压力

当时 `RedissonConfig` 里是硬编码：

- `127.0.0.1:6379`
- 固定密码

而且连接参数没有做更细粒度控制。

所以我做了两个调整：

### 1. Redisson 改成读取 `application.yaml`

这样做的目的：

- 避免配置散落两处
- 避免 Spring Redis 和 Redisson 配置不一致
- 后续环境切换更方便

### 2. 缩小连接池并调低启动期资源占用

例如控制：

- 连接池大小
- 最小空闲连接数
- 订阅连接池大小
- 超时和重试参数

另外还把：

- `logging.level.com.hmdp`

从 `debug` 降到了 `info`，减少启动阶段日志和资源消耗。

### 面试时怎么讲

你可以这么说：

`我没有只停留在“Redis 连不上”这个表层，而是继续看异常链，发现 Redisson 创建阶段存在连接失败和直接内存压力，所以我把配置收口到 application.yaml，并且缩小了连接池规模，降低了启动期资源占用。`

## 7.29 第五阶段：验证 Kafka 链路是否真正生效

项目能启动之后，还不能直接说 Kafka 改造成功。

因为“应用启动成功”和“Kafka 链路真的打通”是两回事。

所以我继续做了链路验证。

### 验证点 1：看 Kafka Producer 初始化日志

启动日志里出现了类似信息：

- `Instantiated an idempotent producer`
- `Cluster ID ...`
- `ProducerId set ...`

这说明：

- Producer 已经成功初始化
- 已经连上 Kafka Broker
- 幂等 Producer 也生效了

### 验证点 2：看 Kafka Consumer 是否成功订阅 Topic

日志里能看到：

- 成功加入 consumer group
- 分区分配成功
- 成功订阅 `seckill-order`

这说明消费侧也已经起来了。

### 验证点 3：看 Redis Stream Relay 是否正常启动

日志里出现：

- `Stream group already exists, stream=stream.orders, group=relay-g1`

说明 Relay 线程也正常启动并接入了 Redis Stream 消费组。

### 验证点 4：看库存预热是否正常

日志里出现：

- `Seckill stock preheat finished, loaded 2 records to Redis.`

说明预热逻辑也打通了。

### 这一阶段可以怎么总结

你可以面试里说：

`我不是只看应用起没起来，而是把 Producer、Consumer、Relay、库存预热四个关键节点都验证了一遍，确认 Redis -> Relay -> Kafka -> Consumer 这条链路已经真实打通。`

## 7.30 第六阶段：从“能启动”继续推进到“可讲可靠性”

在项目恢复运行后，我没有停在“系统能跑”这个状态，而是继续往可靠性层面思考。

后续重点规划了几件事：

1. 把 Kafka Consumer 从自动提交 offset 改成手动确认
2. 为订单表增加 `unique(user_id, voucher_id)` 唯一索引
3. 增加死信 Topic
4. 增加消费失败补偿和库存回滚补偿
5. 补充 traceId 和链路日志

这部分很适合面试时表达成：

`我做的不是一次简单的功能接入，而是先把项目从编译失败和启动失败里救起来，再继续往消息可靠性和最终一致性方向补强。`

## 7.31 这次排障过程的面试表达模板

### 简版回答

`这次改 Kafka 秒杀链路时，项目一开始其实并不是业务逻辑错了，而是启动链上多个类依赖 Lombok 自动生成代码，但当前构建产物里这些代码没有生效，导致 log、构造器、getter/setter 大量缺失。我先按启动优先级把这些关键类改成显式代码，让项目恢复可启动。之后又继续排查 Redisson 初始化阶段的 Redis 连接和内存压力问题，把配置收口并缩小连接池，最终把 Redis 预热、Redis Stream Relay、Kafka Producer、Kafka Consumer 整条链路都跑通了。`

### 进阶版回答

`这次排障我分了三个层次。第一层是识别问题类型，我发现报错集中在 log cannot be resolved、final field not initialized、getter/setter undefined，这些都是 Lombok 失效的典型特征，所以我先判断这是构建产物问题，不是业务逻辑问题。第二层是按启动链路止血，我把 RedisStreamToKafkaRelay、Producer、Consumer、消息模型和关键实体改成显式构造器、显式 logger、显式 getter/setter，让应用先恢复启动。第三层是继续往下排真正运行期问题，我定位到 Redisson 创建阶段的 Redis 连接失败和直接内存压力，调整了 Redisson 配置和日志级别。最后我没有只停在应用启动成功，而是进一步验证了 Kafka Producer、Consumer、Redis Stream Relay 和库存预热都已经生效，并继续规划手动 ack、唯一索引、死信和补偿，把链路往可靠性方向再推进。`

## 7.32 这次排障能体现哪些能力

如果面试官愿意深挖，你还可以顺手点出自己体现的能力：

### 1. 能区分工具链问题和业务问题

不是一看到 MQ 报错就认为 Kafka 有问题，而是能从异常特征判断这是 Lombok 代码生成失效。

### 2. 有启动优先级意识

不是一上来大改所有代码，而是先修启动链上直接阻塞的类，让系统尽快恢复可运行。

### 3. 能区分根因和伴随异常

没有被 `RejectedExecutionException` 这种表象异常带偏，而是继续顺着异常链找到 Redisson 初始化失败和资源压力。

### 4. 有链路验证意识

项目启动成功之后，还继续验证 Producer、Consumer、Relay、预热是否都真正生效。

### 5. 有工程补强意识

不是“跑通就结束”，而是继续规划手动 ack、死信、唯一索引和补偿方案。

## 9. 10 分钟长版面试讲稿

下面这版适合你在面试里系统讲述这个 Kafka 秒杀链路改造。

它不是单纯背八股，而是按照一个比较自然的项目表达顺序展开：

- 先讲业务背景
- 再讲原始问题
- 再讲技术方案
- 再讲链路细节
- 再讲可靠性
- 最后讲排障与优化

你可以把它理解成一版“项目复盘式表达模板”。

---

如果让我系统介绍这次 Kafka 秒杀链路改造，我一般会从业务背景开始讲。

我这个项目本质上是一个类似黑马点评的本地生活服务后端，其中有一个比较典型的高并发场景，就是优惠券秒杀。秒杀的核心难点其实不是普通 CRUD，而是要同时解决几个问题：

第一，高并发下库存不能超卖；
第二，同一个用户不能重复下单；
第三，接口不能把所有压力直接打到数据库；
第四，整个链路要尽量做到异步化、可恢复和可扩展。

所以我在这个项目里做的，不是简单把一个同步下单接口改成“发个 MQ”而已，而是把整条秒杀链路升级成了一套 Redis + Kafka + MySQL 分工明确的架构。

我通常会先用一句话概括这套方案：

`Redis 负责秒杀入口的原子校验，Kafka 负责异步削峰和消费恢复，MySQL 负责最终订单落库和业务事实。`

具体来说，请求入口是在 `POST /voucher-order/seckill/{id}`。用户发起秒杀之后，接口线程不会直接去做数据库事务，而是先进入 Redis Lua 脚本。Lua 里主要做三件事：

第一，判断库存是否充足；
第二，判断用户是不是已经下过单，也就是一人一单；
第三，如果校验通过，就在 Redis 里预扣库存，并且把这次请求写入 Redis Stream。

这里 Redis 的作用非常关键，因为秒杀这种场景如果把库存判断和一人一单校验放在数据库层，会导致数据库在高并发下很快成为瓶颈。而 Lua 的好处是它能把库存校验、一人一单校验和消息写入 Redis Stream 这几个操作做成一个原子过程。也就是说，在入口阶段，我就可以先用 Redis 快速拦掉大量无效请求，把真正有资格下单的请求保留下来。

不过我这里并没有停留在 Redis Stream 本身，而是继续往后做了一层扩展。我新增了一个 Relay 组件，专门负责从 Redis Stream 里读取消息，再转发到 Kafka Topic。这个设计是我觉得这个项目里比较值得讲的一点。

因为如果只做到 Redis Stream，其实更多像是“本地异步化”；而我引入 Kafka 之后，整条链路就升级成了一个更适合讲削峰、积压、扩展和消费恢复的消息架构。

所以现在的链路变成了：

用户请求 -> Redis Lua 原子校验 -> Redis Stream 落受理消息 -> Relay 转发 Kafka -> Kafka Consumer 异步消费 -> MySQL 扣减库存并创建订单。

其中 Relay 的职责是这样的：

它会作为 Redis Stream 消费组消费者持续读取 `stream.orders`，把消息转换成统一的 `VoucherOrderMessage`，然后调用 Kafka Producer 发到 Topic `seckill-order`。这一层我专门做成了“Kafka 发送成功后再 ACK Redis Stream 消息”。

这个点面试里很好讲，因为它体现的是中间桥接层的可靠性设计。也就是说，不是从 Redis Stream 读到了消息就立刻确认，而是要等 Kafka 真正收到了消息之后，才去 ACK Redis Stream，尽量降低 Redis 到 Kafka 这一段的丢消息风险。

再往后就是 Kafka Consumer。Consumer 监听 `seckill-order` Topic，拿到消息后会先把 JSON 反序列化成 `VoucherOrderMessage`，然后组装成 `VoucherOrder` 领域对象，再调用订单服务去执行业务落库逻辑。

这里真正的业务落库不是“直接插一条订单”这么简单，而是还要做消费侧的业务兜底。主要包括几个点：

第一，按用户维度加分布式锁，避免同一用户并发重复创建订单；
第二，再查一次数据库，确认用户是不是已经下过单；
第三，扣减数据库库存时必须带 `stock > 0` 条件，防止并发下库存扣成负数；
第四，库存扣减成功后才真正保存订单。

所以整条链路里，Redis 是入口原子校验，Kafka 是异步削峰和可恢复消费，MySQL 才是最终业务结果。这个职责边界我在面试里一定会讲清楚，因为很多面试官就爱问一句：“既然用了 Kafka，为什么还要 Redis？”

我的回答一般是：

`Kafka 擅长异步传输和削峰，但不擅长做高并发库存校验和一人一单这种入口原子逻辑。秒杀场景最关键的是在请求入口快速完成资格判断，所以 Redis 不能去掉。`

接下来我一般会继续讲，为什么这个项目要引入 Kafka，而不是继续停留在本地线程池或者纯 Redis Stream。

我觉得主要有四个原因：

第一，Kafka 更适合承接秒杀这种高峰流量，把瞬时洪峰先接住，再让消费端慢慢处理；
第二，Kafka 对消息积压和消费恢复能力更强，服务重启后也更容易继续消费；
第三，Kafka 的 Consumer Group 模型更适合后续做横向扩容；
第四，从面试表达上，它能让我进一步讲到手动 ack、重试、死信、补偿和最终一致性，而不是只停留在“我用了异步线程池”。

当然，我也不会把 Kafka 讲成唯一正确答案。如果面试官追问为什么不用 RabbitMQ 或 RocketMQ，我会说：

RabbitMQ 更适合灵活路由和传统业务模块解耦；RocketMQ 更适合讲顺序消息、延迟消息、事务消息这些偏交易型业务能力；而我这个项目当前更想强调的是高吞吐削峰、消息积压和消费恢复，所以我选 Kafka 会更贴合我要表达的架构重点。

然后我会继续往可靠性上讲，因为秒杀项目里只把消息发出去还远远不够。

我现在这个版本虽然已经把主链路打通了，但从工程可靠性角度，我后续还会继续补几件事。

第一件事，是把 Kafka Consumer 从自动提交 offset 升级成手动确认。

因为当前如果配置的是自动提交，那么消息只要被 Consumer 拉到了，offset 就可能先提交，但这并不代表订单已经真正落库成功。最危险的情况是：Kafka 这边 offset 提交了，但 MySQL 订单还没创建成功，或者业务执行到一半服务挂了，那这条消息重启后就不会再消费，最终会出现 Redis 已经预扣库存，但数据库没有订单的丢单问题。

所以更合理的做法是把确认时机后移到业务成功之后。也就是 Consumer 处理成功后再 `ack.acknowledge()`，如果业务失败就不确认，让 Kafka 后续重试。这个点我会明确表达成：

`对于秒杀订单这种场景，我更关注业务成功和消息确认时机的强绑定，所以会把 Consumer 改成手动 ack，只有订单真正落库成功后才提交 offset。`

第二件事，是数据库唯一索引兜底。

虽然入口已经有 Redis Lua 做一人一单，消费侧也有 Redisson 锁和业务判重，但 Kafka 消费模型本质还是至少一次投递，极端情况下仍然可能重复消费。所以我建议在 `tb_voucher_order` 上增加 `unique(user_id, voucher_id)` 联合唯一索引，作为最终防线。

这样我在面试里就能讲成“三层幂等”：

- Redis Lua：入口防重复
- Consumer 业务逻辑：消费侧防重复
- DB 唯一索引：最终兜底

第三件事，是补死信队列和补偿方案。

因为秒杀链路里最麻烦的场景其实不是普通失败，而是 Redis 这边已经预扣库存并记录了购买标记，但 Kafka Consumer 最终落库失败了。这个时候如果不补偿，就会出现 Redis 库存变少了，用户也被标记成买过，但数据库里根本没有订单。

对于这类情况，我的思路是分两步：

先尽量通过有限次重试和死信补偿，让订单最终落库成功；
如果确认不可恢复，再执行 Redis 侧的回滚补偿，也就是把库存加回去，把用户购买标记删除掉。

这里我会强调一个观点：

`补偿不是简单重试一下，而是为了恢复 Redis 和 MySQL 之间的业务一致性。`

如果面试官继续问“为什么不直接回滚 Redis”，我也会说：

因为很多失败其实是暂时性的，比如数据库抖动、网络闪断，如果一失败就立刻回滚，反而会让链路来回震荡。更稳妥的做法是先重试，重试超限后再补偿。

接下来，我通常会把这次项目里的真实排障过程也讲出来，因为这个部分很容易体现工程能力。

我这次改造过程中，项目一开始其实不是 Kafka 逻辑写坏了，而是启动阶段连续报 Bean 创建异常。最开始的报错看起来很乱，有 `log cannot be resolved`、`final field may not have been initialized`、`getter/setter undefined` 之类。

我当时判断，这类错误不是普通业务逻辑报错，而是 Lombok 自动生成代码没有进入当前编译产物导致的。因为缺失的正好都是 `@Slf4j` 生成的 log、`@RequiredArgsConstructor` 生成的构造器、`@Data` 生成的 getter/setter。

所以我第一步不是去怀疑 Kafka 或 Redis 本身，而是先按启动链路优先级，把 `RedisStreamToKafkaRelay`、`VoucherOrderProducerImpl`、`VoucherOrderConsumer`、`VoucherOrderMessage`、`VoucherOrder`、`SeckillVoucher` 这些直接阻塞启动的类改成显式代码，先让项目恢复可启动。

项目继续往下启动后，又出现了 Redisson 初始化失败、Redis 连接异常和直接内存压力相关的问题。这里我没有被表面的 Netty 线程池异常带偏，而是顺着异常链继续看，发现真正落点是在 `redissonClient` 创建阶段，所以又去调整了 Redisson 配置，把配置统一收口到 `application.yaml`，并且缩小连接池、降低日志级别，减少启动期资源占用。

最后项目能启动之后，我也没有直接宣布“改造完成”，而是继续验证了四个关键节点：

第一，Kafka Producer 是否已经正常初始化；
第二，Kafka Consumer 是否已经成功加入消费组并订阅分区；
第三，Redis Stream Relay 是否已经正常启动；
第四，秒杀库存预热是否已经成功写入 Redis。

这些都验证通过之后，我才认为这条 Redis -> Relay -> Kafka -> Consumer -> MySQL 的链路已经真正打通。

所以如果让我总结这次项目改造，我不会只说“我在项目里用了 Kafka”，我会说：

`我把原来的秒杀异步下单链路升级成了 Redis 前置原子校验 + Kafka 异步削峰 + MySQL 最终落库的架构。在实现上，我做了 Redis Stream 到 Kafka 的桥接、Kafka Producer/Consumer 的接入，以及消费侧业务幂等和库存安全控制；在可靠性上，我进一步规划了手动 ack、数据库唯一索引、死信和补偿方案；在落地过程中，我还处理了 Lombok 构建产物失效、Redisson 启动资源压力等问题，最终把整条链路从“起不来”修到“能跑通、能验证、能继续补强”。`

如果面试时间足够，我最后还会补一句：

`我觉得这次改造最大的价值，不只是把 Kafka 接进来了，而是把这个秒杀系统从“会跑”往“可恢复、可解释、可扩展”的方向推进了一步。`

---

这就是一版 10 分钟左右、比较完整的项目讲法。

## 11. 本次实际落地改造摘要

这一节可以理解成对当前项目真实改造状态的一次收口总结。

和前面偏“设计思路、面试表达、方案规划”不同，这一节强调的是：

- 这次到底改了什么
- 哪些已经真正落地了
- 哪些已经验证过了
- 哪些还可以继续增强

如果面试官问你：

`你这个 Kafka 秒杀链路，最后真正做到了什么程度？`

你就可以直接按这一节回答。

### 11.1 当前已经真实落地的改造点

#### 1. 秒杀入口仍然保留 Redis Lua 原子校验

当前接口仍然是：

- `POST /voucher-order/seckill/{id}`

入口逻辑仍然保留：

- 库存判断
- 一人一单判断
- Redis 预扣库存
- `XADD stream.orders`

这保证了秒杀最核心的原子资格校验仍然在 Redis 里完成。

#### 2. Redis Stream 到 Kafka 的桥接已经完成

我新增了 Relay 组件，把 Redis Stream 中的秒杀订单消息继续转发到 Kafka。

这一步把原来“只在 Redis 体系内异步”的链路，升级成了：

- Redis 前置原子校验
- Kafka 异步削峰
- MySQL 最终落库

#### 3. Kafka Producer 已经改成“真正发送成功才算成功”

这一点是本次改造里很关键的一个修正。

我一开始发现一个问题：

- 即使 Kafka Broker 不可用
- Producer 也可能因为只是把发送任务提交给客户端线程而提前返回 success
- 这会导致 Relay 误以为消息已经进 Kafka，从而错误 ACK Redis Stream

后来我把 Producer 改成了：

- 调用 `kafkaTemplate.send(...).get(...)`
- 同步等待 broker 返回确认
- 只有真正拿到 Kafka send result 才返回 success

这一步解决的是：

`避免“客户端已提交发送任务”被误判成“Kafka 已成功接收消息”。`

#### 4. Relay 已经做到“Kafka 成功后再 ACK Redis Stream”

当前 Relay 的关键保证是：

- 只有 Producer 返回 true
- 才会 ACK Redis Stream

因为 Producer 现在已经改成同步等待 broker ack，所以这一步的语义也就更可靠了。

你可以把这句话直接拿去面试说：

`我不是简单调用 KafkaTemplate.send 就算成功，而是等 broker 真正确认成功后，才 ACK Redis Stream，从而降低 Redis 到 Kafka 之间的丢消息风险。`

#### 5. Kafka Consumer 已经改成手动 ack

当前 Consumer 已经不是自动提交 offset，而是：

- 业务处理成功后才 `ack.acknowledge()`
- 业务失败则不确认 offset

这一步已经实际落地，不是停留在方案层。

#### 6. Kafka Consumer 已经接入有限次重试

当前使用的是：

- `SeekToCurrentErrorHandler`
- `FixedBackOff(1000ms, 3次)`

也就是消费失败时会按 offset 重试，而不是直接把消息吞掉。

#### 7. Redis Stream Relay 已经支持 pending-list 恢复

当前 Relay 在启动时会先执行 pending-list 恢复逻辑。

它的作用是：

- 如果之前有 Redis Stream 消息已经被读到，但还没成功转发 Kafka
- 那么应用重启后还能继续捞出这批 pending 消息再处理

这一步是当前补偿链路里的一个最小恢复能力。

#### 8. 订单表唯一索引兜底已经落地

我已经补了唯一索引脚本：

- `src/main/resources/db/voucher_order_unique_index.sql`

对应索引是：

- `unique(user_id, voucher_id)`

并且数据库里也已经执行成功。

这意味着现在一人一单已经形成了三层兜底：

1. Redis Lua 入口拦截
2. Consumer 业务判重
3. DB 唯一索引最终兜底

#### 9. 业务日志已经补了 traceId

当前链路日志已经能串起：

- Relay 转发成功/失败
- Kafka send 成功/失败
- Kafka consume 成功/失败
- 订单创建成功

并且日志里已经带：

- `traceId`
- `orderId`
- `userId`
- `voucherId`

所以现在已经具备比较完整的链路追踪能力。

---

### 11.2 当前已经验证过的结果

#### 1. traceId 全链路已经打通

我已经实际验证过同一个 traceId 会依次出现在：

- `Relay forward success`
- `Kafka send success`
- `Kafka consume success`

这说明：

- Redis Stream -> Relay
- Relay -> Kafka
- Kafka -> Consumer

三段链路都已经打通。

#### 2. 手动 ack 已经验证生效

我做过一次故意消费失败验证：

- Consumer 第一次消费时人为抛异常
- 这时消息不会 ack
- Kafka 会 seek 回原 offset
- 然后同一条消息再次被消费并成功

这说明：

- 当前确实不是自动提交 offset
- 而是业务失败不确认，业务成功才确认

#### 3. Kafka 不可用时，接口“受理成功”语义已经明确

当前接口返回成功，不代表订单已经同步落库。

它代表的是：

- Redis Lua 校验成功
- Redis Stream 写入成功
- 请求已经被系统受理

所以 Kafka 挂掉时，接口依然可能返回成功，这是符合当前架构设计的。

这个点也已经在实际测试里验证和澄清过了。

#### 4. 订单创建成功日志已经验证可见

当前日志中已经可以看到：

- `Create voucher order success`

说明 Consumer -> Service -> MySQL 这一步也已经真实落地，而不是只停留在消息投递层。

#### 5. 基线压测已经做过

我已经做过几轮本地基线测试，包括：

- RedisIdWorker 吞吐测试
- 单用户高并发秒杀测试
- 多用户高并发秒杀测试
- **HTTP 层 JMeter 压测**（`秒杀抢购.jmx` + 多 token CSV + 响应断言白名单，见 `秒杀链路测试结果记录.md` **4.4**）

结果已经整理在：

- `秒杀链路测试结果记录.md`

其中已经验证：

- 单用户并发下，一人一单拦截生效
- 多用户并发下，当前服务层入口具备千级 QPS 的基础能力
- **HTTP 接口**在本地一轮 **3000 请求** 的聚合报告中，**Throughput 约 1900/s 量级、断言失败率 0%**（单机环境，用于面试与相对对比，非生产承诺）

---

### 11.3 当前这版方案的“接口成功”到底是什么意思

这个问题非常重要，因为它关系到你能不能把秒杀链路讲清楚。

当前这版接口成功的含义是：

`请求已经通过 Redis 原子校验，并成功写入 Redis Stream，属于“已受理成功”。`

它不等价于：

- Kafka 已经完成消费
- MySQL 订单已经创建成功
- 用户最终一定已经拿到订单

真正的最终成功，要看后续异步链路是否完成。

这个设计的好处是：

- 不把 Kafka 可用性直接绑死在接口响应上
- 让秒杀入口可以更快返回
- 提高高并发场景下的入口承载能力

你在面试里可以这么表达：

`我的秒杀接口返回成功，表示请求已经被受理，不代表订单同步落库成功；最终订单创建由 Redis Stream、Kafka、Consumer 这条异步链路继续完成。`

---

### 11.4 当前还没有完全做完，但已经明确下一步方向的内容

虽然这次已经把主链路和基础可靠性补强了不少，但还没有把所有增强都做完。

后续仍然可以继续补：

#### 1. 死信 Topic

当前已经有：

- 手动 ack
- 有限次重试

但重试超限后还没有单独落到死信 Topic。

#### 2. 失败任务表

当前失败日志已经有了，但还没有把失败消息统一落库做运营化管理。

#### 3. 库存与购买标记回滚补偿

当前已经具备最小恢复能力：

- Kafka 重试
- Redis Stream pending-list 恢复

但如果最终确认订单无法落库，Redis 的预扣库存和购买标记回滚还可以继续做得更完整。

#### 4. Redis 与 MySQL 对账任务

后续还可以增加库存对账任务，进一步补最终一致性治理能力。

---

### 11.5 如果面试官问“你现在这套方案做到哪一步了”

你可以直接用下面这段回答：

`我现在已经把秒杀链路从 Redis Lua + 本地异步，升级成了 Redis Lua + Redis Stream + Kafka + MySQL 的异步下单架构。入口仍然由 Redis Lua 做库存判断和一人一单原子校验，校验通过后写入 Redis Stream。后面我新增了 Relay，把 Stream 消息转发到 Kafka，并且 Producer 已经改成同步等待 broker ack，只有 Kafka 真正确认成功后，Relay 才会 ACK Redis Stream。Consumer 这边我已经改成手动 ack，业务成功后才提交 offset，并接入了有限次重试。数据库层我也补了 unique(user_id, voucher_id) 唯一索引做最终幂等兜底。日志层面已经补了 traceId，可以串起 Relay、Producer、Consumer 和订单创建日志。实际验证上，我已经验证过 traceId 全链路打通、手动 ack 生效、Consumer 失败后会按 offset 重试、订单可以正常落库，以及 Kafka 不可用时接口成功语义是“已受理”而不是“已完成”。目前还可以继续补死信 Topic、失败任务表和更完整的补偿任务。`

---

### 11.6 一句话总结当前状态

当前这版 Kafka 秒杀链路，已经从“能跑”升级到了：

- 主链路打通
- 消息确认时机更合理
- 幂等更完整
- 日志更可追踪
- 具备最小恢复能力

也就是说，现在已经不是一个“只会发 MQ”的 demo 了，而是一条具备一定工程可靠性的秒杀异步链路。

### 8.1 为什么要在秒杀项目里引入 Kafka

答：

`因为秒杀流量是突发高并发，数据库不适合直接扛住所有写请求。我保留 Redis + Lua 做前置原子校验，再把真正的订单创建异步化，通过 Kafka 做削峰、解耦和更可靠的消息消费恢复，这样秒杀接口可以更快返回，后端也更稳。`

### 8.2 为什么不是直接用 Kafka，而是 Redis + Kafka 一起用

答：

`Kafka 更擅长异步传输和削峰，不擅长做库存判断和一人一单这种原子校验。秒杀最核心的问题必须在入口阶段快速、原子地解决，所以我保留 Redis Lua 做前置校验，再用 Kafka 承接后续异步订单处理。`

### 8.3 Redis Stream 和 Kafka 在这里分别做什么

答：

`Redis Stream 更像本地事务消息桥，保证 Lua 校验通过后的请求先落到 Redis 里；Kafka 负责后续更标准的消息队列能力，比如异步削峰、消费扩展、消息积压和重启恢复。`

### 8.4 为什么要加 Relay，而不是 Lua 直接发 Kafka

答：

`Lua 运行在 Redis 内部，不能直接可靠地调用 Kafka。我的做法是先在 Lua 里原子地写入 Redis Stream，再由 Java 侧 Relay 组件读取 Stream 转发 Kafka，只有 Kafka 发送成功后才 ACK Stream，这样能把秒杀入口和 Kafka 解耦，也降低中间断点带来的丢消息风险。`

### 8.5 Kafka message key 为什么选 userId

答：

`我用 userId 作为 key，主要是为了让同一用户相关消息尽量进入同一分区，减少同一用户请求乱序的影响，也便于从业务维度解释分区设计。`

### 8.6 Kafka 能保证不丢消息吗

答：

`严格说不能只靠一句“Kafka 不丢消息”来回答。我的项目里做了几层保障：Producer 开启 acks=all、重试和幂等；Redis Stream 到 Kafka 之间通过 Relay 做“发送成功再 ACK”；Consumer 侧通过幂等和数据库约束兜底。真正工程上追求的是尽量不丢、可恢复、可补偿。`

### 8.7 Kafka 会不会重复消费

答：

`会，所以 Consumer 不能假设消息只来一次。我的处理思路是业务幂等：先做重复下单校验，再通过数据库唯一索引和条件扣库存做最终兜底。`

### 8.8 你怎么保证一人一单

答：

`我做了三层控制。第一层，Redis Lua 在入口阶段做一人一单校验；第二层，Consumer 处理时按用户维度加分布式锁；第三层，数据库层建议加 user_id 和 voucher_id 的唯一索引作为最终兜底。`

### 8.9 你怎么防止超卖

答：

`入口层在 Redis Lua 里先校验库存并预扣减，Consumer 真正落库时又通过 stock > 0 条件更新扣减库存，所以是 Redis 预扣减加数据库最终扣减双重控制。`

### 8.10 如果 Kafka 消费失败怎么办

答：

`短期可以依靠 Kafka 重试和 Consumer 再消费；更完整的方案是有限次重试后进入死信 Topic，再配合补偿任务人工或自动处理失败订单。`

### 8.11 如果 Redis 已经预扣库存，但订单没有落库成功怎么办

答：

`这是最终一致性问题。标准做法是通过死信和补偿机制回滚 Redis 库存和用户购买标记，或者重新投递订单消息，并通过定时对账保证 Redis 和 MySQL 最终一致。`

### 8.12 这套方案是强一致吗

答：

`不是强一致，是最终一致。秒杀入口为了吞吐和响应速度，先在 Redis 层做资格校验和预扣减，再异步落库，所以我接受短时间内的状态不一致，但会通过幂等、重试、死信和补偿把最终结果修正回来。`

### 8.13 为什么 Consumer 还要再做业务校验，不是 Lua 校验过了吗

答：

`因为消息系统天然要考虑重复消费、服务重启和异常重试。Lua 只负责入口阶段挡住大部分无效请求，但最终订单创建仍然必须在 Consumer 侧做幂等和库存安全控制。`

### 8.14 Kafka 比你原来线程池异步方案好在哪里

答：

`线程池异步只能解决当前实例内的削峰，服务重启后任务容易丢，也不方便横向扩展。Kafka 方案更适合高并发链路，因为它有更成熟的消息持久化、消费恢复、扩展性和监控能力。`

### 8.15 这套 Kafka 方案还有什么风险

答：

`我认为主要风险是三类：Redis Stream 到 Kafka 的桥接断点、Consumer 重复消费、Redis 预扣和 DB 最终结果不一致。所以我会把重点放在 Relay 成功后再 ACK、Consumer 幂等、唯一索引、死信和补偿机制上。`

## 9. 一段适合面试直接说的表述

`我在这个项目里没有简单地把秒杀改成“Kafka 下单”，而是设计成 Redis + Kafka + MySQL 分层协作。秒杀请求先经过 Redis Lua 做库存校验和一人一单校验，校验通过后原子写入 Redis Stream。然后由一个 Relay 组件消费 Redis Stream，把消息转发到 Kafka，并且只有 Kafka 发送成功后才 ACK Stream。Kafka Consumer 再异步消费消息，执行真正的订单创建、数据库库存扣减和订单落库。这样做的好处是：Redis 负责高并发原子校验，Kafka 负责异步削峰和消费恢复，MySQL 负责最终业务事实。我同时考虑了重复消费、超卖、幂等和最终一致性问题，所以这套方案比单纯线程池异步更有工程可讲性。`

## 10. 一段适合被追问时的补充回答

`如果继续增强这套方案，我会补三件事。第一，订单表增加 unique(user_id, voucher_id) 唯一索引，让数据库成为最终幂等兜底。第二，Consumer 改为手动提交 offset，并增加有限次重试和死信 Topic。第三，增加 Redis 和 DB 的库存对账与补偿任务，解决预扣库存成功但最终订单失败的问题。这样整条秒杀链路会更完整。`

---

## 11. 进展追加记录（2026-04-20）

本节为 append-only 追加，不覆盖原有面试稿，仅补充当前已落地能力，面试可直接引用。

### 11.1 当前已落地的失败治理闭环

- 主链路失败：有限次重试（`SeekToCurrentErrorHandler + FixedBackOff`）
- 重试超限：进入死信 Topic `seckill-order-dlt`
- 死信落库：写入 `tb_voucher_order_fail_task`
- 自动补偿：定时重试 `RETRY_PENDING` 任务并回投主 Topic
- 最终状态：
  - 成功 -> `DONE`
  - 超阈值仍失败 -> `MANUAL_HANDLE_REQUIRED`

### 11.2 当前人工处理入口

- 查询任务：`GET /voucher-order-fail-task/page`
- 手动重试：`POST /voucher-order-fail-task/{id}/retry`
- 人工忽略：`POST /voucher-order-fail-task/{id}/ignore`

### 11.3 面试一句话表达（可直接背）

`我现在把失败治理做成了闭环：主消费失败先有限重试，超限进 DLT，死信落失败任务表，再由定时任务自动重试，最终失败才转人工处理。这样既不阻塞主链路，又保证失败消息可追踪、可恢复、可治理。`

# Kafka 秒杀模拟面试 20 问 20 答

## 使用方式

- 第一轮：先只看“问题”，自己口述答案。
- 第二轮：对照“标准答法”，补齐关键词。
- 第三轮：每题控制在 30-60 秒，练面试节奏。

---

## 1）你这个项目为什么要引入 Kafka？

**标准答法：**  
核心原因是削峰和可靠异步。秒杀流量是突发的，DB 不适合直接承接峰值写入。  
我把链路升级为 `Redis Lua + Kafka + MySQL`：Redis 做入口原子校验，Kafka 做异步削峰和恢复消费，MySQL 做最终落库。这样吞吐更稳、故障恢复能力更强。

---

## 2）你是把 Redis Stream 替换成 Kafka 吗？

**标准答法：**  
不是替换，而是分层协作。入口仍由 Lua 写 `stream.orders`，然后通过 Relay 转 Kafka。  
这样保留了 Redis 在入口原子校验上的优势，同时利用 Kafka 做更成熟的消费恢复与重试管理。

---

## 3）完整链路说一下？

**标准答法：**  
`请求 -> Redis Lua 校验并预扣库存 -> XADD stream.orders -> Relay 转发 Kafka -> Consumer 消费落库 -> 成功 ack / 失败重试 / 超限死信`。  
最终由消费侧幂等和数据库约束保证正确性。

---

## 4）为什么不直接请求进来就发 Kafka？

**标准答法：**  
因为秒杀最关键的是入口阶段快速且原子的资格校验（库存和一人一单），这类判断更适合 Redis + Lua。  
Kafka 适合异步传输，不适合做高并发入口资格判定。

直接发Kafka的问题

```
用户请求 → Kafka → Consumer → DB
```



问题1：Lua脚本的原子性没了

秒杀的核心逻辑在Lua里：

```
-- 原子操作：校验库存 + 预扣 + 记录用户
if (库存充足 and 未下单) then
    预扣库存 + 记录用户 + 发消息
end
```



如果不用Lua：

- Java代码需要多次Redis调用
- 并发下无法保证原子性 → **超卖**

问题2：Kafka不可用时，接口直接失败

```
用户请求 → Kafka发送 → 失败 → 返回"系统繁忙"
```



- 瞬间高并发，Kafka可能扛不住
- 接口失败率飙升
- 用户体验差

问题3：没有入口缓冲

```
10000个请求同时到
    │
    ├─ 9000个通过校验 → 发Kafka
    └─ 1000个库存不足 → 直接返回

如果直接发Kafka：
10000个请求 → Kafka（瞬间压力）
```



------

用Redis Stream的好处

```
用户请求 → Lua（原子校验）→ Redis Stream（写入成功就返回）
                                        │
                                        ▼
                              Kafka → Consumer → DB
```



| 优势         | 说明                                         |
| :----------- | :------------------------------------------- |
| **原子校验** | Lua保证库存和一人一单原子操作                |
| **快速响应** | Stream写入成功就返回，不等Kafka              |
| **入口缓冲** | 10000请求瞬间到，Stream先扛住，Kafka慢慢消费 |
| **解耦**     | 入口和落库独立，Kafka挂了不影响入口          |

------

一句话

> **Lua + Redis = 入口快、校验准** **Kafka = 异步稳、落库可靠**

两者结合，既保证秒杀的核心逻辑（原子性），又保证高可用（入口不依赖Kafka）。

---

## 5）接口返回成功，代表订单一定成功了吗？

**标准答法：**  
不代表。我的成功语义是“已受理”，即 Lua 校验成功并写入 Stream。  
最终订单成功要看后续异步链路是否完成落库。这个语义设计能把入口可用性和后端落库解耦。

---

## 6）那 Kafka 挂了是不是就丢单？

**标准答法：**  
不会直接丢。关键在 Relay 的 ACK 时序：只有 Kafka 发送成功才 ACK Redis Stream。  
Kafka 不可用时消息会留在 Stream pending，服务恢复后可继续转发，避免链路中间丢失。



---

## 7）你为什么强调“Kafka 成功后再 ACK Stream”？

**标准答法：**  
这是防断点丢消息的关键。  
如果先 ACK 再发 Kafka，一旦发送失败，消息既不在 Stream，也不在 Kafka，形成永久丢失。  
现在这个顺序至少保证失败可恢复。

---

## 8）Producer 做了哪些可靠性配置？

**标准答法：**  
我设置了 `acks=all`、发送重试、幂等开启，并且发送端同步等待 broker 回执后才认为成功。  
避免“只是交给客户端缓冲就误判发送成功”。

---

## 9）Consumer 为什么用手动 ack？

**标准答法：**  
因为“拉到消息”不等于“业务成功”。  
自动提交 offset 可能导致订单落库失败但位点已提交，造成消息丢失。  
手动 ack 可以把位点提交和业务成功绑定，失败就不确认，让重试机制接管。

---

## 10）Consumer 失败后会怎样？

**标准答法：**  
抛异常后进入错误处理器，先做有限次重试（固定退避）。  
重试耗尽后写入死信 Topic，再由死信消费者入失败任务表，后续重试或人工补偿。

---

## 11）Kafka 会重复消费，怎么保证不重复下单？

**标准答法：**  
我做了三层幂等：  

1. Redis Lua 入口判重；  
2. Consumer 业务判重；  
3. DB 唯一索引 `user_id + voucher_id` 兜底。  
   所以即使重复投递，也不会生成重复订单。

---

## 12）怎么防超卖？

**标准答法：**  
Redis 侧先预扣库存拦截高并发，DB 落库时更新语句带 `stock > 0` 条件二次保护。  
双层控制下，最终以 DB 成功结果为准，避免超卖。

---

## 13）既然 Redis 预扣了，为什么还要 DB 再扣一次？

**标准答法：**  
Redis 是高并发优化层，不是最终业务事实层。  
MySQL 才是最终账本，必须在 DB 层再做库存扣减和订单落库，才能保证业务闭环与可审计。

---

## 14）如果 Redis 预扣成功，但最终落库失败怎么办？

**标准答法：**  
会进入补偿链路：失败任务重试、必要时回滚 Redis 库存与购买标记，并做对账修复。  
我接受短暂不一致，但必须保证可追踪、可重试、可修复。

---

## 15）Topic 分区和 key 你怎么设计？

**标准答法：**  
Topic 用 `seckill-order`，分区先按目标吞吐和消费者并发给初值（比如 3/6），后续按 lag 调优。  
消息 key 用 `userId`，让同用户请求尽量落同分区，减少局部乱序影响。

---

## 16）你如何观测这条链路健康？

**标准答法：**  
我重点看四类指标：  

1. Kafka lag/堆积；  
2. Relay 转发成功率和 pending 积压；  
3. 消费成功率、重试次数、死信量；  
4. 业务指标：下单成功率、重复下单率、超卖率。  
   日志统一带 `traceId/orderId/userId/voucherId` 做全链路排查。

---

## 17）为什么还要死信，不无限重试吗？

**标准答法：**  
无限重试会拖垮消费能力，也会卡住后续正常消息。  
死信把“短暂故障”和“脏数据/逻辑异常”分开处理，保证主消费通道持续可用。

---

## 18）这套改造的最大代价是什么？

**标准答法：**  
代价是系统复杂度上升：组件更多、排障难度更高。  
我通过标准化日志、失败任务表、死信处理、监控告警来控制复杂度。  
用复杂度换取高峰稳定性和恢复能力，这是值得的 trade-off。

---

## 19）你如何证明这套方案确实更好？

**标准答法：**  
做三组压测对比：同步下单、Redis Stream 异步、Kafka 异步。  
对比 QPS、TP99、成功率、超卖率、重复率、lag、DB CPU/慢 SQL。  
Kafka 方案通常在峰值稳定性和故障恢复上优势明显。

---

## 20）如果让你继续优化，下一步做什么？

**标准答法：**  
我会优先做三件事：  

1. 完善自动补偿（失败任务分级重试 + 回滚策略）；  
2. 建立库存/订单对账任务；  
3. 增加告警和压测回归基线，形成工程闭环。  
   目标是把“可运行”升级到“可长期稳定运营”。

---

## 结尾 15 秒（可背诵）

这次改造的重点不是“上了 Kafka”，而是把秒杀链路做成了分层协作：  
Redis 保证入口原子正确，Kafka 保证异步削峰和可恢复，MySQL 保证最终业务事实。  
我重点解决了 ACK 时序、重复消费、超卖控制和失败补偿，所以这套方案既能扛流量，也能扛故障。

# 秒杀并发与 Kafka 面试说明（项目扫描版）

## 1. 30 秒项目概述（可直接背）

这个项目是一个 Spring Boot + MyBatis-Plus + Redis + Kafka 的本地生活服务后端。  
秒杀链路上，我把请求处理拆成三层：入口用 Redis Lua 做库存和一人一单的原子校验，消息通过 Redis Stream + Kafka 异步削峰，最终由 Consumer 落 MySQL 并做幂等兜底。  
同时我把失败治理做成闭环：主消费有限次重试，超限进 DLT，死信落失败任务表并回滚 Redis 预扣，支持人工重试和人工忽略，系统还有库存对账和 Kafka lag 巡检。

---

## 2. 项目全局技术栈（面试开场 10 秒）

- Java 8, Spring Boot 2.3.12, MyBatis-Plus 3.4.3
- Redis（String/Set/Stream/Geo/Bitmap）、Redisson、Caffeine 本地缓存
- Kafka（Spring Kafka）
- MySQL

核心目录分层：

- `controller`：HTTP 接口层
- `service`/`service.impl`：业务编排
- `mapper` + `entity`：数据访问
- `config`：Kafka/Redis/调度/监控配置
- `mq`：Stream-Kafka 中继、生产消费、死信处理
- `utils`：缓存、ID 生成、拦截器、常量

---

## 3. 秒杀高并发链路（重点）

### 3.1 请求入口为什么快

入口是 `POST /voucher-order/seckill/{id}`，核心在 `VoucherOrderServiceImpl.seckillVoucher()`：

1. 先生成全局订单号（`RedisIdWorker`）
2. 执行 `seckill.lua` 原子脚本
3. 成功就快速返回 `orderId`，失败立即返回原因（库存不足/重复下单）

关键点：**不在入口做 DB 落单**，所以接口在高并发下更稳。

### 3.2 Lua 脚本做了什么

`seckill.lua` 一次性完成：

- 检查 `seckill:stock:{voucherId}` 库存
- 检查 `seckill:order:{voucherId}` 是否已下单（Set）
- 扣 Redis 库存、写用户下单标记
- `XADD stream.orders` 写入受理消息

这保证了“库存校验 + 一人一单 + 入队”在 Redis 侧是原子动作。

### 3.3 异步化为什么是 Stream + Kafka

链路是：

`Lua -> Redis Stream(stream.orders) -> Relay -> Kafka(seckill-order) -> Consumer -> MySQL`

`RedisStreamToKafkaRelay` 的关键语义是：

- Kafka 发送成功才 `XACK` Stream
- 发送失败不 ack，消息留在 pending-list
- 启动时会补扫 pending-list

这解决了“Redis 已受理但中继异常导致消息丢失”的风险。

### 3.4 最终落库如何防并发与重复

`VoucherOrderServiceImpl.createVoucherOrder()` 里做了 3 层保护：

- Redisson 用户锁：`lock:order:{userId}`
- 业务判重：按 `user_id + voucher_id` 查重复
- DB 约束兜底：唯一索引 `uk_user_voucher(user_id, voucher_id)`

库存扣减用条件更新：`stock = stock - 1 and stock > 0`，防超卖。

---

## 4. Kafka 在项目中的职责与可靠性

### 4.1 Producer 侧

配置（`application.yaml`）：

- `acks=all`
- `retries=3`
- `enable.idempotence=true`

实现（`VoucherOrderProducerImpl`）：

- `send(...).get(5s)` 同步等待 broker 回执
- 成功日志输出 `traceId/orderId/partition/offset`

### 4.2 Consumer 侧

配置（`KafkaConfig` + `application.yaml`）：

- 手动 ack：`manual_immediate`
- 自动提交关闭：`enable-auto-commit=false`
- 错误处理：`SeekToCurrentErrorHandler + FixedBackOff(1000,3)`
- 超限进入 DLT：`{topic}-dlt`

实现（`VoucherOrderConsumer`）：

- 业务成功才 `acknowledgment.acknowledge()`
- 可恢复异常交给重试链路
- 不可恢复异常会先回滚 Redis 预扣，再 ack，避免毒消息无限重试

---

## 5. 死信与失败任务治理（你现在已落地）

### 5.1 DLT 闭环

`VoucherOrderDeadLetterConsumer` 监听 `seckill-order-dlt`，收到消息后：

1. 解析消息
2. 调用 `saveDeadLetterTaskAndRollback`
3. 落表 `tb_voucher_order_fail_task`
4. 同步执行 Redis 预扣回滚（`seckill_rollback.lua`）

### 5.2 失败任务表用途

这张表把“日志事件”转成“可治理任务”，支持：

- 状态追踪（如 `ROLLBACK_DONE`、`MANUAL_HANDLE_REQUIRED`、`DONE`）
- 错误归因（`error_msg`、`failure_type`）
- 人工介入与二次处理

### 5.3 已有处理入口

- 查询：`GET /voucher-order-fail-task/page`
- 手动重试：`POST /voucher-order-fail-task/{id}/retry`
- 人工忽略：`POST /voucher-order-fail-task/{id}/ignore`

另外有自动任务 `VoucherOrderFailTaskRetryScheduler` 定时扫描待重试任务。

---

## 6. 运维与可观测（面试加分点）

- Kafka 堆积巡检：`KafkaLagMonitorScheduler`
- 秒杀库存对账：`SeckillStockReconcileScheduler`（Redis vs DB）
- 启动预热库存：`SeckillStockPreheatRunner`
- 全链路 trace 字段：`traceId/orderId/userId/voucherId`

---

## 7. 高频面试追问（直接用）

### Q1：为什么不用“接口直接写 DB”，而要异步？

A：秒杀场景峰值高，接口层最怕慢 SQL 和锁竞争。我把入口收敛成 Redis 原子校验 + 入流，先保证“快速受理”，再异步落库，这样吞吐和稳定性都更好。

### Q2：Redis 已扣库存但 DB 失败怎么办？

A：我有失败治理闭环。可恢复异常走 Kafka 重试；不可恢复异常或重试超限进入 DLT，落失败任务表并回滚 Redis 预扣，避免长期不一致。

### Q3：Kafka 至少一次投递会重复消费，怎么防？

A：业务层查重 + DB 唯一索引双保险。就算重复消息到达，最终也不会产生重复订单。

### Q4：为什么要手动 ack？

A：自动提交会出现“消息已提交但业务未成功”风险。手动 ack 可以把位点提交和业务成功强绑定，失败就不确认，让重试机制接管。

### Q5：DLT 的价值是什么？

A：不是把坏消息丢掉，而是把问题消息隔离出来，避免阻塞主链路，同时给出可追踪、可修复、可人工处理的入口。

---

## 8. 你可以主动说的改进路线（下一步）

1. 异常分级更细：可恢复/不可恢复按异常类型路由
2. 重试策略升级：固定间隔改退避（带 `next_retry_time`）
3. 失败任务看板：状态统计、重试成功率、人工积压量
4. Redis-DB 对账后自动补偿策略完善

---

## 9. 关键代码定位（面试时可引用）

- 秒杀入口：`src/main/java/com/hmdp/controller/VoucherOrderController.java`
- 秒杀服务：`src/main/java/com/hmdp/service/impl/VoucherOrderServiceImpl.java`
- Lua 原子脚本：`src/main/resources/seckill.lua`
- Stream 中继：`src/main/java/com/hmdp/mq/RedisStreamToKafkaRelay.java`
- Kafka 配置：`src/main/java/com/hmdp/config/KafkaConfig.java`
- 主消费者：`src/main/java/com/hmdp/mq/VoucherOrderConsumer.java`
- 死信消费者：`src/main/java/com/hmdp/mq/VoucherOrderDeadLetterConsumer.java`
- 失败任务服务：`src/main/java/com/hmdp/service/impl/VoucherOrderFailTaskServiceImpl.java`
- 失败任务接口：`src/main/java/com/hmdp/controller/VoucherOrderFailTaskController.java`
- 失败任务表：`src/main/resources/db/voucher_order_fail_task.sql`



# Kafka 秒杀链路面试场景与追问作答手册

## 1. 一句话总述（开场 10 秒）

我的秒杀系统不是把 Redis Stream 简单替换成 Kafka，而是升级成了 `Redis 前置原子校验 + Kafka 异步削峰 + MySQL 最终落库`。  
Redis 保证高并发入口正确性，Kafka 保证异步传输与恢复能力，MySQL 保证最终业务一致性。

---

## 2. 30 秒版本（面试常用主回答）

秒杀请求进来后，我先在 Redis Lua 里做库存判断和一人一单判断，校验通过后预扣 Redis 库存，并写入 `stream.orders`。  
然后通过一个 Relay 组件把 Redis Stream 消息转发到 Kafka Topic。这里我做了关键约束：**只有 Kafka 发送成功才 ACK Redis Stream**，避免中间链路丢消息。  
Kafka Consumer 收到消息后执行业务下单，成功才手动提交 offset；失败走重试，重试耗尽进死信，再由失败任务链路做补偿。  
最终通过消费侧判重 + 数据库唯一索引兜底，保证幂等和一致性。

---

## 3. 3 分钟版本（结构化展开）

### 3.1 为什么引入 Kafka

- 高并发秒杀下，数据库不适合直接承接突发写流量，需要异步削峰。
- 仅靠同步链路会把接口 RT 和后端落库强绑定，系统脆弱。
- Kafka 在消息堆积、重试恢复、消费位点管理方面更适合做异步主干。

### 3.2 为什么不直接用 Kafka 取代 Redis

- 秒杀最核心是“库存判断 + 一人一单”，要求入口阶段原子完成。
- Redis + Lua 适合高并发原子校验，Kafka 不适合作资格校验。
- 所以职责分工是：Redis 负责“快且准”的入口校验，Kafka 负责“稳且可恢复”的异步传输。

### 3.3 具体链路

`请求 -> Lua 校验/预扣 -> XADD stream.orders -> Relay 转 Kafka -> Consumer 落库 -> ACK/重试/死信`

### 3.4 可靠性关键点

- Producer 侧：`acks=all`、重试、幂等开启。
- Relay：Kafka 成功后才 ACK Stream，且支持 pending-list 恢复。
- Consumer：手动 ack，业务成功才提交 offset。
- ErrorHandler：有限次重试，超限进入 DLT。

### 3.5 幂等与一致性

- Redis 层：Lua 原子判断避免入口重复下单。
- 消费层：业务判重，不能假设“只消费一次”。
- DB 层：`user_id + voucher_id` 唯一索引兜底。
- 最终一致性：失败消息通过重试/死信/失败任务补偿闭环。

---

## 4. 面试高频追问与标准作答

## 4.1 追问：接口为什么能快速返回成功？Kafka 挂了怎么办？

**答法：**
我定义的接口成功语义是“请求已受理”，即 Lua 校验成功并写入 Redis Stream，不代表订单已经最终落库。  
如果 Kafka 不可用，Relay 不会误 ACK Stream，消息会留在 pending 或待恢复链路中，后续恢复后继续转发。  
这样可以把接口可用性和 Kafka 瞬时可用性解耦。

**加分点：**
主动补一句“我在日志和监控上会重点看 relay-forward-fail、pending 积压、consumer lag”。

---

## 4.2 追问：为什么要“Kafka 成功后再 ACK Redis Stream”？

**答法：**
这是为了避免“Redis 侧消息先确认，但 Kafka 实际没收到”的链路断点。  
如果先 ACK 再发 Kafka，一旦发失败就可能永久丢单。  
我现在的顺序是：先发 Kafka，拿到成功确认后再 ACK Stream，至少能保证失败消息留在 Redis 可恢复。

---

## 4.3 追问：手动 ack 和自动提交 offset 有什么区别？

**答法：**
自动提交只表示“消息被拉取过”，不代表“业务处理成功”。  
秒杀场景里如果自动提交了 offset，但订单落库失败，就会出现消息已确认、订单没落库的丢单风险。  
所以我使用手动 ack，把 offset 提交时机后移到业务成功之后。

---

## 4.4 追问：Kafka 会重复投递，怎么避免重复下单？

**答法：**
我按“三层幂等”做的：

- Redis Lua：入口一人一单校验；
- Consumer 业务：消费侧判重；
- MySQL：唯一索引最终兜底。  
  就算消息重复到达，也不会生成重复订单。

---

## 4.5 追问：如何避免超卖？

**答法：**

- 入口 Redis Lua 先预扣库存，快速拦截大部分无效请求；
- 落库时 SQL 带 `stock > 0` 条件更新，防止并发穿透；
- Redis 与 DB 双层控制，最终以 DB 结果为准。

---

## 4.6 追问：Consumer 失败后具体怎么走？

**答法：**
业务异常时不手动 ack，抛出异常交给 Kafka Listener 错误处理器。  
我配置了固定退避重试，重试耗尽后转入死信 Topic。  
死信消息会记录到失败任务表，由定时重试或人工干预补偿，确保最终可追可修。

---

## 4.7 追问：为什么链路里既有 Redis Stream 又有 Kafka，不会重复吗？

**答法：**
Redis Stream 在我的方案里是“入口接收缓冲层”，Kafka 是“异步主干总线”。  
二者定位不同：前者贴近 Lua 原子写入、便于入口快速落消息；后者更擅长消费组扩展、重平衡、长链路可靠投递。  
Relay 把两者连接起来并做 ACK 时序控制。

---

## 4.8 追问：你这套方案最大的 trade-off 是什么？

**答法：**
核心 trade-off 是系统复杂度提升：链路更长、组件更多、排障要求更高。  
我通过标准化日志字段（orderId/userId/voucherId/traceId）、失败任务表、死信和监控告警来控制复杂度。  
换来的是高峰期吞吐与稳定性显著提升，以及更好的故障恢复能力。

---

## 5. 面试官可能继续深挖的“进阶追问”

## 5.1 追问：Kafka 分区数怎么定？

**答法：**
先按目标吞吐和消费者并发估算一个初始值（例如 3 或 6），后续根据 lag 和 CPU 调整。  
消息 key 用 `userId`，让同一用户请求尽量落同分区，降低同用户乱序影响。  
我不强依赖全局顺序，依赖幂等和约束保证正确性。

## 5.2 追问：如果 Redis 预扣成功但最终落库失败怎么办？

**答法：**
会进入补偿链路：失败任务重试、必要时回滚 Redis 库存和购买标记，并做对账修复。  
原则是承认分布式场景会有短暂不一致，但必须提供可追踪、可重放、可修复机制。

## 5.3 追问：如何证明方案有效？

**答法：**
做三组压测对比：同步直写 DB、Redis Stream 异步、Kafka 异步。  
看 QPS、TP99、成功率、超卖率、重复下单率、consumer lag、DB CPU/慢 SQL。  
在我的实践中，Kafka 链路在峰值稳定性和恢复能力上明显更好。

## 5.4 追问：你怎么观测 Kafka 堆积和消费是否健康？

**答法：**
我会盯 **consumer lag**：用 Kafka 管理接口读出每个分区 end offset 和消费组已提交 offset，**lag = end − committed**，再按分区汇总。  
工程里用定时任务拉一次 lag，超过阈值打 **WARN** 日志，先把「堆积恶化」暴露出来，后续可以再接到 Grafana。  
这和只看「Consumer 有没有在跑」不一样，lag 直接对应「欠了多少条消息没消化」。

## 5.5 追问：Redis 和 MySQL 库存会不会不一致，你怎么发现？

**答法：**
秒杀里 Redis 是预扣、MySQL 是最终扣减，中间又有异步消息，不能假设永远毫秒级一致。  
我做的是**定时对账**：按券把 `seckill:stock:{voucherId}` 和 `tb_seckill_voucher.stock` 比对，不一致就 **WARN**，必要时再结合失败任务/死信去修。  
更严格的「一人一单/订单事实对账」可以作为下一步演进。

---

## 6. 容易扣分的说法（避坑）

- 不要说“我把 Redis Stream 替换成 Kafka”。
- 不要把“接口返回成功”说成“订单已落库成功”。
- 不要说“Kafka 保证绝对不重复消费”。
- 不要只讲组件名，不讲 ACK 时机、重试、死信、幂等。
- 不要只讲吞吐，不讲一致性和补偿。

---

## 7. 可直接背诵的收尾话术（20 秒）

这次改造的重点不是“用了 Kafka”，而是把秒杀链路拆成了职责清晰的三层：  
Redis 做前置原子校验，Kafka 做异步削峰和可恢复消费，MySQL 做最终业务事实。  
我重点解决了 ACK 时序、重复消费、超卖控制和失败补偿，所以方案既能扛高并发，也能在异常下保证最终一致性。

---

## 8. 面试前 1 分钟速记卡

- 关键词：`Lua 原子校验`、`Relay`、`Kafka 成功后 ACK Stream`、`手动 ack`、`重试+DLT`、`三层幂等`、`最终一致性补偿`、`lag 巡检`、`库存对账`
- 主链路：`Lua -> Stream -> Relay -> Kafka -> Consumer -> MySQL`
- 成功语义：接口成功 = 已受理，不等于已完成
- 风险控制：失败可重试、可追踪、可补偿



