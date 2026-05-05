# Kafka秒杀链路面试题

## 一、基础概念篇

1. Kafka中的Broker、Topic、Partition、Consumer Group分别是什么？它们之间的关系是怎样的？

   **Broker（经纪人）**：Kafka的一个服务实例，就是一个Broker。可以理解为一台Kafka服务器。多个Broker组成Kafka集群，实现高可用。生产者把消息发给Broker，消费者从Broker拉取消息。

   **Topic（主题）**：消息的逻辑分类，类似数据库的表。生产者往Topic发消息，消费者从Topic读消息。项目中 `seckill-order` 就是秒杀订单的Topic。

   **Partition（分区）**：Topic的物理存储单元，一个Topic可以分成多个Partition。每个Partition是一个有序队列，内部消息有序。Partition是Kafka并行度的基本单位。同一Key的消息会进入同一Partition（项目用userId做Key，保证同一用户的消息有序）。

   ```
   seckill-order Topic
   ├── Partition-0  [msg1, msg4, msg7, ...]   ← userId%3==0 的消息
   ├── Partition-1  [msg2, msg5, msg8, ...]   ← userId%3==1 的消息
   └── Partition-2  [msg3, msg6, msg9, ...]   ← userId%3==2 的消息
   ```

   **Consumer Group（消费者组）**：一组消费者共同消费一个Topic。核心规则：一个Partition同一时刻只能被组内一个Consumer消费。

   ```
   seckill-order Topic（3个分区）
   Consumer Group: seckill-order-g1
   ├── Consumer-1 → 消费 Partition-0
   ├── Consumer-2 → 消费 Partition-1
   └── Consumer-3 → 消费 Partition-2
   ```

   **关系总结**：Producer把消息发到Broker的Topic的某个Partition（根据Key路由），Consumer Group中的Consumer各自消费不同的Partition，实现并行消费。

2. Kafka为什么比Redis Stream更适合做秒杀的消息队列？Redis Stream有哪些局限性？

   **Redis Stream的局限性**：

   | 维度 | Redis Stream | Kafka |
   |-----|-------------|-------|
   | 持久化 | 内存优先，RDB/AOF不保证实时 | 磁盘持久化，消息不丢失 |
   | 吞吐量 | 单机约10万/秒 | 百万级/秒 |
   | 分区 | 不支持，单机瓶颈 | 支持多Partition，水平扩展 |
   | 消费者组 | 基本支持，功能有限 | 成熟，支持Rebalance |
   | 消息回溯 | 弱，仅Pending List | 强，从任意offset消费 |
   | 副本 | 依赖Redis主从 | ISR副本机制，更可靠 |
   | 生态集成 | 无 | Flink/Spark等大数据生态 |

   **项目为什么选择Kafka**：

   1. **秒杀瞬时高并发**：10万/秒的请求量，Kafka的吞吐量远超Redis Stream
   2. **消息不能丢**：订单是钱，Redis Stream内存存储有丢失风险，Kafka磁盘持久化更可靠
   3. **削峰填谷**：Kafka的Partition可以水平扩展，Consumer可以并行消费
   4. **消费者组Rebalance**：消费者挂了，Kafka自动将Partition分配给其他消费者
   5. **死信处理**：Kafka有成熟的DLT机制，Redis Stream没有
   6. **可观测性**：Kafka有Lag监控、AdminClient等运维工具

   **但项目没有直接去掉Redis Stream**，而是加了Relay中转层，原因：
   - Lua脚本中的XADD已经写好了，改动成本低
   - Redis Stream作为入口缓冲，即使Kafka短暂不可用，消息也不会丢
   - 渐进式架构演进，不需要一次性改完

2-1. 项目里面用了Redis Stream为什么还要有Kafka呢？只用一个可以吗？

   **能不能只用一个**：
   | 方案 | 优点 | 缺点 |
   |-----|------|------|
   | 只用Redis Stream | 简单，链路短 | 吞吐量低、消息可能丢、无DLT机制 |
   | 只用Kafka | 高吞吐、可靠 | Lua脚本需要改、入口无缓冲 |

   **项目为什么两个都要**：

   **1. 历史原因：渐进式改造**
   ```
   最初版本：Lua → Redis Stream → (当时没有后续)
   升级版本：Lua → Redis Stream → Relay → Kafka
   ```
   - Lua脚本中的XADD已经写好了
   - 不想改业务代码，加了个Relay中转层
   - 渐进式演进，不用一次性大改

   **2. 职责不同：各司其职**
   | 组件 | 职责 | 特点 |
   |-----|------|------|
   | Redis Stream | 秒杀的入口缓冲 | Lua脚本直接写，延迟低 |
   | Kafka | 异步削峰落库 | 高吞吐、可靠消费 |

   ```
   用户点击秒杀
       │
       ▼
   Lua脚本 → 快速返回（Redis Stream写入成功）
              │
              ▼
   Redis Stream → Relay → Kafka → Consumer → DB
   (入口缓冲)   (中转)   (削峰)    (落库)
   ```

   **3. 互补优势**
   | Redis Stream | Kafka |
   |-------------|-------|
   | 低延迟（毫秒级） | 高吞吐（百万级） |
   | 内存存储 | 磁盘持久化 |
   | 单机瓶颈 | 水平扩展 |
   | 无DLT机制 | 有DLT死信 |

   **如果去掉Redis Stream，直接用Kafka**：
   ```java
   // 问题：Lua脚本里不能直接调Kafka
   // 改Java代码：Kafka不可用时，秒杀接口直接失败
   public Result seckillVoucher(Long voucherId) {
       // 改成直接发Kafka
       // 问题：Kafka短暂不可用怎么办？→ 接口直接失败
   }
   ```
   - 原来Redis Stream可以扛一下缓冲流量

   **一句话总结**：Redis Stream是入口缓冲（快、挡流量），Kafka是异步落库（稳、可靠）。两个都要是为了不解耦、互补、低延迟+高可靠。

3. 你的项目中Kafka的Topic叫什么？分区数是多少？消息的Key是怎么选择的？为什么用userId做Key？

   **Topic名称**：`seckill-order`（可在application.yaml中配置 `spring.kafka.template.default-topic`）

   **分区数**：取决于Kafka集群配置，一般设为Broker数量的2-3倍。项目中使用默认配置。

   **消息Key**：`userId`（用户ID）

   **为什么用userId做Key**：

   1. **保证同一用户消息有序**：同一用户的秒杀请求都会发送到同一个Partition，确保先下请求先处理，避免乱序

   2. **负载均衡**：不同用户的请求分散到不同Partition，充分利用并行消费能力

   3. **业务语义匹配**：秒杀场景中，同一用户对同一商品的多次请求应该有因果关系，用userId做Key天然匹配业务逻辑

   ```
   // VoucherOrderProducerImpl.send() 中的实现
   kafkaTemplate.send(topic, String.valueOf(msg.getUserId()), payload)
                │            │
                │            └─ message（消息体）
                └─ key = userId（决定消息路由到哪个Partition）
   ```

   **Key选择原则**：
   - 选择有业务意义的字段
   - 保证相关消息进入同一Partition
   - 避免数据倾斜（热点Key）

4. 什么是Kafka的offset？Consumer Group的offset有什么作用？

   **offset（位移）**：Kafka中每条消息在Partition内都有一个递增的序号，叫做offset。它标记了消息在Partition中的位置。

   ```
   Partition-0
   [msg0@offset=0] [msg1@offset=1] [msg2@offset=2] [msg3@offset=3] [msg4@offset=4]
   ```

   **Consumer Group的offset作用**：

   1. **追踪消费进度**：Consumer Group记录每个Partition消费到哪个offset，下次启动从该位置继续消费

   2. **消息不丢失不重复**：只有offset被提交的消息才是"已消费"的，提交前的消息重启后会重新消费

   3. **Rebalance恢复**：Consumer挂了，Partition重新分配给其他Consumer时，通过offset恢复进度

   4. **指定位置消费**：可以通过`seek()`方法指定从某个offset开始消费，用于回溯数据

   **提交offset的时机**：
   - 自动提交：`enable.auto.commit=true`，poll时自动提交（可能丢失未处理的消息）
   - 手动提交：`enable.auto.commit=false`，处理完后手动提交（本项目采用）

5. Kafka的消息有三种语义：at-most-once、at-least-once、exactly-once，你的项目用的是哪种？为什么？

   **三种语义对比**：

   | 语义 | 含义 | 可能发生 | 适用场景 |
   |-----|------|---------|---------|
   | at-most-once | 消息不丢失，但可能重复 | 重复消费 | 日志收集 |
   | at-least-once | 消息不重复，但可能丢失 | 消息丢失 | 大多数业务 |
   | exactly-once | 消息既不丢失也不重复 | 无 | 金融交易 |

   **项目用的是at-least-once**：

   1. **实现方式**：手动提交offset，消息处理完后ack，失败则重试

   2. **为什么不用at-most-once**：订单消息不能丢，丢了用户钱付了但没订单

   3. **为什么不用exactly-once**：实现复杂，性能损耗大，at-least-once+幂等基本能满足需求

   4. **幂等保证**：通过DB唯一索引+DuplicateKeyException处理，保证最终一致性

## 二、项目链路篇

6. 画一下你项目中秒杀下单的完整数据流，从用户点击到订单落库。

   **完整数据流**：

   ```
   用户点击秒杀
        │
        ▼
   ┌─────────────────────────────────────────┐
   │ 1. Controller层                          │
   │    POST /voucher-order/seckill/{id}      │
   └─────────────────────────────────────────┘
        │
        ▼
   ┌─────────────────────────────────────────┐
   │ 2. 秒杀入口（VoucherOrderServiceImpl）   │
   │    - 生成订单ID（RedisIdWorker）          │
   │    - 执行seckill.lua                     │
   └─────────────────────────────────────────┘
        │
        ▼
   ┌─────────────────────────────────────────┐
   │ 3. Redis Lua脚本（seckill.lua）          │
   │    - 校验库存（GET seckill:stock）      │
   │    - 校验一人一单（SISMEMBER）           │
   │    - 预扣库存（INCRBY -1）              │
   │    - 记录用户（SADD）                   │
   │    - 发Stream消息（XADD stream.orders） │
   └─────────────────────────────────────────┘
        │
        ▼
   ┌─────────────────────────────────────────┐
   │ 4. Redis Stream → Kafka Relay          │
   │    RedisStreamToKafkaRelay              │
   │    - 消费Stream消息                     │
   │    - 转发到Kafka                       │
   │    - Kafka发送成功后ACK                 │
   └─────────────────────────────────────────┘
        │
        ▼
   ┌─────────────────────────────────────────┐
   │ 5. Kafka（Topic: seckill-order）       │
   │    - VoucherOrderProducer发送            │
   │    - VoucherOrderConsumer消费            │
   └─────────────────────────────────────────┘
        │
        ▼
   ┌─────────────────────────────────────────┐
   │ 6. 订单落库（VoucherOrderServiceImpl）   │
   │    - 分布式锁（Redisson）              │
   │    - 二次校验一人一单                   │
   │    - 扣减DB库存（UPDATE）              │
   │    - 创建订单（INSERT）                │
   └─────────────────────────────────────────┘
        │
        ▼
   订单创建完成
   ```

7. 为什么要在Redis Stream和Kafka之间加一个Relay中转层？直接用Kafka不行吗？

   **直接用Kafka的方案**：

   ```
   seckill.lua → Kafka → Consumer
   ```

   **问题**：
   - Lua脚本执行完后立即返回，消息已经在Kafka了
   - 如果Kafka发送失败，Redis预扣已经做了，但消息丢了
   - 没有确认机制，消息可靠性无法保证

   **Relay中转层的价值**：

   ```
   seckill.lua → Redis Stream → Relay → Kafka → Consumer
                  │                            │
                  │ 可靠存储                   │ 可靠发送
                  │（Redis持久化）             │（ACK确认）
   ```

   1. **可靠性保障**：Kafka发送成功后才ACK Stream，消息不丢
   2. **解耦**：Lua脚本不需要关心Kafka的可用性
   3. **缓冲**：即使Kafka短暂不可用，消息还在Stream里
   4. **渐进式改造**：不用改已有的Lua脚本和业务逻辑

8. Relay中转层怎么保证消息不丢？如果Kafka发送失败怎么办？

   **保证不丢的关键**：

   1. **先发后ACK**：Relay先调用Kafka Producer发送，等Kafka返回成功后才ACK Redis Stream

   2. **失败不ACK**：如果Kafka发送失败，不ACK，消息留在Stream的Pending List中

   3. **重启恢复**：应用重启后，先处理Pending List中的消息（recoverPendingList）

   ```
   // RedisStreamToKafkaRelay.handleRecord()
   boolean success = forwardToKafka(record);  // 先发Kafka
   if (success) {
       stringRedisTemplate.opsForStream().acknowledge(...);  // 成功才ACK
   }
   ```

   **Kafka发送失败怎么办**：

   - 不ACK，消息留在Pending List
   - 下次循环重新处理这条消息
   - 不断重试直到成功
   - 有兜底：本地缓存有TTL，最终会被清理

9. 你项目中的手动ACK是怎么实现的？为什么不用自动提交？

   **手动ACK实现**：

   ```java
   // KafkaConfig.java
   factory.getContainerProperties().setAckMode(AckMode.MANUAL_IMMEDIATE);

   // VoucherOrderConsumer.onMessage()
   voucherOrderService.handleVoucherOrderFromMQ(order);  // 处理消息
   acknowledgment.acknowledge();  // 手动提交offset
   ```

   **流程**：
   ```
   收到消息 → 处理业务 → acknowledge() → offset提交
                     │
                     └─ 处理失败 → 不ack → 重试
   ```

   **为什么不用自动提交**：

   | 模式 | 机制 | 问题 |
   |-----|------|------|
   | 自动提交 | poll时自动提交offset | 消息可能未处理完就提交了，失败会丢消息 |
   | 手动立即提交 | 处理完立即提交 | 消息处理完才提交，不丢消息 |

   **项目选择手动ACK的原因**：
   - 订单消息不能丢，必须处理完才能提交
   - 自动提交无法保证"处理完再提交"
   - 手动提交更精确控制消费语义

10. 你的Consumer消费失败后，重试机制是怎样的？重试几次？间隔多久？

    **重试机制**：

    ```
    消费失败
        │
        ▼
    Kafka重试（SeekToCurrentErrorHandler）
        │
        ├─ 第1次重试：间隔1秒
        │
        ├─ 第2次重试：间隔1秒
        │
        └─ 第3次重试：间隔1秒
            │
            ▼
        仍失败 → 发送DLT（死信队列）
    ```

    **配置**：
    ```java
    // KafkaConfig.java
    // 重试3次，间隔1秒
    factory.setErrorHandler(new SeekToCurrentErrorHandler(
        recoverer,
        new FixedBackOff(1000L, 3L)
    ));
    ```

    **为什么这样设计**：
    - 间隔1秒：给故障恢复留出时间
    - 重试3次：覆盖大多数临时故障（网络抖动、DB慢查询）
    - 还失败就进DLT：避免无限重试，浪费资源

## 三、死信与补偿篇

11. 什么是死信队列？你的项目中死信队列的Topic命名规则是什么？

    **死信队列（DLT - Dead Letter Topic）**：消息经过最大重试次数仍失败后，进入的特殊队列。用于存放"无法处理但不能丢弃"的消息。

    **项目中的死信Topic**：`seckill-order-dlt`

    **命名规则**：原Topic + `-dlt`后缀

    ```
    原Topic: seckill-order
    DLT:    seckill-order-dlt
    ```

    **消息什么时候进DLT**：
    - Consumer消费失败
    - 重试3次仍失败
    - SeekToCurrentErrorHandler自动发送到DLT

12. 消息进入死信队列后，你是怎么处理的？Redis预扣的库存怎么回滚？

    **DLT消费者处理流程**：

    ```
    VoucherOrderDeadLetterConsumer.onDeadLetterMessage()
        │
        ▼
    SeckillRedisRollbackService.rollback()
        │  执行seckill_rollback.lua
        │  - SREM移除用户标记
        │  - INCRBY恢复库存
        │
        ▼
    VoucherOrderFailTaskServiceImpl.saveDeadLetterTaskAndRollback()
        │  保存失败任务到数据库
        │
        ▼
    tb_voucher_order_fail_task表
    ```

    **回滚Lua脚本**（seckill_rollback.lua）：
    ```lua
    -- 检查用户是否在订单集合中
    if redis.call('sismember', orderKey, userId) == 0 then
        return 1  -- 已回滚过，幂等返回
    end
    -- 执行回滚
    redis.call('srem', orderKey, userId)    -- 移除用户标记
    redis.call('incrby', stockKey, 1)      -- 恢复库存
    return 0
    ```

13. 你的回滚Lua脚本怎么保证幂等性？如果回滚执行两次会怎样？

    **幂等性保证**：

    ```lua
    if redis.call('sismember', orderKey, userId) == 0 then
        return 1  -- 用户不在集合中，说明已回滚
    end
    -- 执行回滚
    redis.call('srem', orderKey, userId)
    redis.call('incrby', stockKey, 1)
    return 0
    ```

    **执行两次会怎样**：

    | 执行次数 | 用户在集合中 | srem结果 | incrby结果 | 返回值 |
    |---------|-------------|----------|------------|-------|
    | 第1次 | 是 | 删除成功 | 库存+1 | 0 |
    | 第2次 | 否（已删除） | 删除失败（但命令执行成功） | 库存再+1 | 1 |

    **结论**：
    - 第1次：正常回滚，库存恢复，用户标记移除
    - 第2次：返回1（幂等标识），但库存又多+1了
    - **潜在问题**：多次回滚会导致库存多还

    **实际影响**：
    - 库存多还1个，对秒杀来说可接受（宁可多不能少）
    - 不会导致超卖（多还比少卖好）
    - 极端场景：用户重新秒杀，可能多买1个

14. 失败任务表的状态有哪些？分别代表什么含义？

    **失败任务表**：`tb_voucher_order_fail_task`

    **状态定义**：

    | 状态 | 含义 | 触发场景 |
    |-----|------|---------|
    | `RETRY_PENDING` | 待重试 | 首次进入失败表 |
    | `RETRYING` | 重试中 | 定时调度器正在重试 |
    | `DONE` | 重试成功 | 重新发送Kafka后成功消费 |
    | `ROLLBACK_DONE` | 已回滚 | DLT处理完，Redis已回滚 |
    | `MANUAL_HANDLE_REQUIRED` | 需人工处理 | 超过最大重试次数 |

    **状态流转**：

    ```
    RETRY_PENDING → RETRYING → DONE（成功）
                            ↘ MANUAL_HANDLE_REQUIRED（重试失败）
                            ↘ ROLLBACK_DONE（DLT处理）
    ```

15. 定时重试调度器多久执行一次？最多重试几次？超过次数怎么办？

    **配置参数**（application.yaml）：

    ```yaml
    app:
      kafka:
        fail-task:
          auto-retry-enabled: true     # 是否开启自动重试
          retry-interval-ms: 15000   # 重试间隔：15秒
          batch-size: 20              # 每批处理数量
          max-retry-count: 5        # 最大重试次数
    ```

    **处理流程**：

    ```
    每15秒执行一次
        │
        ▼
    查询RETRY_PENDING状态任务（最多20条）
        │
        ▼
    逐个重试：
        - 发送消息到Kafka
        - 成功 → DONE
        - 失败 → retryCount+1
        │        - retryCount >= 5 → MANUAL_HANDLE_REQUIRED
        │        - retryCount < 5 → RETRY_PENDING
        │
        ▼
    返回成功数量
    ```

    **超过次数怎么办**：
    - 标记为 `MANUAL_HANDLE_REQUIRED`
    - 需要人工介入处理
    - 可以通过管理后台查看和手动处理

## 四、异常与一致性篇

16. 如果Kafka消费者处理消息时数据库暂时不可用，你的系统怎么处理？

    **处理流程**：

    ```
    Consumer处理消息
        │
        ▼
    调用DB创建订单
        │
        ├─ DB正常 → 成功 → ACK
        │
        └─ DB不可用
            │
            ▼
        判断异常类型
            │
            ├─ 可恢复异常（网络超时、连接超时）
            │   → 抛出异常 → Kafka重试
            │
            └─ 不可恢复异常（数据错误、唯一键冲突）
                → 回滚Redis → ACK
    ```

    **可恢复异常**（会重试）：
    - `SocketTimeoutException`：网络超时
    - `QueryTimeoutException`：查询超时
    - `CannotCreateTransactionException`：无法创建连接
    - `TransientDataAccessException`：临时数据访问异常

    **重试3次后仍失败** → 进DLT → 回滚Redis

17. 如果同一条消息被消费了两次（重复消费），你的系统怎么保证幂等？

    **重复消费产生的原因**：

    ```
    消息处理成功 → ACK失败 → Kafka认为未消费 → 重新投递
    ```

    **两层判重机制**：

    **第1层：Redis Lua 入口判重**
    ```lua
    -- seckill.lua
    local orderKey = 'seckill:order:' .. voucherId

    -- 检查用户是否已经在订单集合中
    if(redis.call('sismember', orderKey, userId) == 1) then
        return 2  -- 返回2，表示重复下单
    end

    -- 记录用户到集合
    redis.call('sadd', orderKey, userId)
    ```
    - Key: `seckill:order:{voucherId}` (Set集合)
    - 检查：`SISMEMBER orderKey userId`
    - 返回1 = 已存在 → 重复
    - 返回0 = 不存在 → 通过

    **作用**：入口快速拦截，毫秒级判断

    **第2层：Consumer 业务判重（三层保险）**
    ```java
    // VoucherOrderServiceImpl.createVoucherOrder()

    // 1. 分布式锁
    RLock lock = redissonClient.getLock("lock:order:" + userId);
    boolean isLock = redisLock.tryLock();
    if (!isLock) {
        throw new SeckillOrderException(true, "LOCK_FAILED");
    }

    // 2. 数据库查询
    int count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
    if (count > 0) {
        return;  // 已存在，跳过
    }

    // 3. 插入订单（唯一索引兜底）
    try {
        save(voucherOrder);
    } catch (DuplicateKeyException e) {
        // 唯一键冲突，忽略
    }
    ```

    **三层判重**：

    | 层级 | 机制 | 位置 | 作用 |
    |-----|------|------|------|
    | 第1层 | Redis Set | Lua层 | 入口快速拦截，毫秒级 |
    | 第2层 | Redisson分布式锁 | Consumer层 | 同一用户并发防重 |
    | 第3层 | DB唯一索引 | Consumer层 | 兜底防极端情况 |

    **为什么要两层判重**：
    ```
    第一层（Lua）：入口快速拦截
    - 优点：毫秒级，不走数据库
    - 缺点：可能因Redis重启丢失

    第二层（Consumer）：落库最终保证
    - 优点：数据库可靠
    - 缺点：有延迟（异步）
    ```
    **双重保障**：第一层挡住大多数，第二层兜底防极端情况。

18. Redis预扣库存和DB扣减库存之间不一致怎么办？你有什么对账机制？

    **为什么会不一致**：

    | 场景 | Redis | DB | 原因 |
    |-----|-------|-----|------|
    | 应用重启 | 预扣丢失 | 未扣 | Redis数据未持久化 |
    | Redis故障 | 数据丢失 | 已扣 | Redis宕机 |
    | 网络分区 | 预扣成功 | 未扣 | 消息未到Kafka |

    **对账机制**：`SeckillStockReconcileScheduler`

    ```java
    // 每5分钟执行一次
    for (每个秒杀券) {
        redisStock = GET seckill:stock:{voucherId}
        dbStock = SELECT stock FROM tb_seckill_voucher

        if (redisStock != dbStock) {
            log.warn("库存不一致！redis={}, db={}", redisStock, dbStock);
        }
    }
    ```

    **发现不一致怎么办**：
    - 仅告警，不自动处理
    - 人工介入排查
    - 考虑补偿策略

19. 如果Relay中转过程中Redis Stream的ACK和Kafka发送不是原子的，会不会丢消息？

    **分析场景**：

    ```
    1. Relay发送Kafka成功
    2. Kafka返回成功
    3. Relay准备ACK Redis Stream
       ↓ 此时宕机
    4. Redis Stream未ACK
    ```

    **结果**：
    - 消息在Redis Stream的Pending List中
    - 应用重启后，recoverPendingList()会重新处理
    - 重新发送Kafka

    **会丢消息吗**：不会

    **因为**：
    - Kafka已经发送成功了（消息在Kafka里）
    - Redis Stream的ACK失败，消息不会丢失
    - 重启后会从Pending List恢复

    **但有重复风险**：
    - 消息被Kafka和Pending List各存一份
    - 重试时可能重复发送
    - 通过幂等性保证最终一致

20. 你的项目中异常是怎么分类的？可恢复和不可恢复的区别是什么？分别怎么处理？

    **异常分类器**：`MqExceptionClassifier.isRecoverable()`

    **可恢复异常**（会重试）：
    - 网络超时：`SocketTimeoutException`、`TimeoutException`
    - 数据库临时故障：`QueryTimeoutException`、`TransientDataAccessException`
    - 连接问题：`CannotCreateTransactionException`
    - IO异常：`IOException`

    **不可恢复异常**（不回滚，直接ACK跳过）：
    - 业务异常：`IllegalArgumentException`
    - 自定义业务异常：`SeckillOrderException(recoverable=false)`

    **处理策略**：

    ```java
    if (!MqExceptionClassifier.isRecoverable(e)) {
        // 不可恢复：回滚Redis，ACK跳过
        seckillRedisRollbackService.rollback(...);
        acknowledgment.acknowledge();
        return;
    }
    // 可恢复：抛出异常，Kafka重试
    throw new RuntimeException(e);
    ```

## 五、生产与进阶篇

21. Kafka的Lag是什么？你的项目怎么监控Lag？Lag过大说明什么问题？

    **Lag（消费延迟）**：消费组已提交位点与日志末端之间的差值

    ```
    Partition-0: [msg1, msg2, msg3, msg4, msg5]
                                ↑ 已提交=3
                                         ↑ 末端=5
                                         Lag=2
    ```

    **项目监控**：`KafkaLagMonitorScheduler`

    ```java
    // 每1分钟执行一次
    totalLag = Σ(分区末端offset - 已提交offset)
    if (totalLag > 500) {
        log.warn("Lag过大！totalLag={}", totalLag);
    }
    ```

    **Lag过大说明的问题**：

    | 原因 | 表现 | 解决方案 |
    |-----|------|---------|
    | Consumer实例减少 | Lag突然增大 | 检查实例是否存活 |
    | 消费逻辑变慢 | Lag缓慢增长 | 优化DB查询 |
    | 外部依赖故障 | Lag突然增大 | 检查Redis/DB连接 |
    | 消费者挂了 | Lag持续不变 | 重启Consumer |

22. 如果秒杀瞬间产生10万条消息，你的Kafka消费者能扛住吗？怎么扩容？

    **瓶颈分析**：

    ```
    10万消息 / 秒
        │
        ├─ 如果1个Partition → 1个Consumer消费 → 10万/秒（瓶颈在Consumer）
        │
        └─ 如果10个Partition → 10个Consumer消费 → 1万/秒/Consumer（能扛住）
    ```

    **扩容方案**：

    1. **增加Partition数量**：
       - Partition数量决定最大并行度
       - 增加Partition后，新消息可以并行消费
       - 已有消息不会自动重新分配

    2. **增加Consumer实例**：
       - 同一Consumer Group内，Partition数量决定最大Consumer数量
       - 最多增加到Partition数量个Consumer
       - 多出来的Consumer会空闲

    3. **提高Consumer处理速度**：
       - 异步处理，减少阻塞
       - 批量处理，攒批提交
       - 优化DB操作（索引、连接池）

23. 你的项目中Producer发送消息是同步还是异步？各有什么优缺点？

    **项目实现**：同步发送

    ```java
    // VoucherOrderProducerImpl.send()
    SendResult result = kafkaTemplate.send(topic, key, payload).get(5, TimeUnit.SECONDS);
    //                              │
    //                              └─ .get() 阻塞等待结果
    ```

    **同步发送的优点**：
    - 发送结果立即知道，成功/失败明确
    - 保证消息确实发送成功
    - 便于错误处理和重试

    **同步发送的缺点**：
    - 阻塞等待，吞吐量受限
    - 等待时间内无法发送其他消息

    **异步发送的优点**：
    - 不阻塞，吞吐量大
    - 适合高并发场景

    **异步发送的缺点**：
    - 结果返回时机不确定
    - 需要回调或Future处理结果
    - 错误处理复杂

    **项目选择同步的原因**：
    - 订单消息不能丢，必须确认发送成功
    - 5秒超时足够长，能应对大多数情况
    - Relay是单线程，不追求极致吞吐量

24. Kafka的分区和Consumer Group的消费者数量是什么关系？消费者比分区多会怎样？

    **核心规则**：一个Partition同时只能被一个Consumer消费

    **关系分析**：

    | 场景 | Partition数 | Consumer数 | 结果 |
    |-----|------------|------------|------|
    | 正常 | 3 | 3 | 每个Consumer消费1个Partition |
    | Consumer少 | 3 | 1 | 1个Consumer消费3个Partition（串行） |
    | Consumer多 | 3 | 5 | 2个Consumer空闲 |

    **消费者比分区多的后果**：

    ```
    Partition-0 → Consumer-1
    Partition-1 → Consumer-2
    Partition-2 → Consumer-3
                    ↑
                Consumer-4（空闲）
                Consumer-5（空闲）
    ```

    - 多余的Consumer不消费任何消息
    - 资源浪费
    - 建议：Consumer数量 ≤ Partition数量

25. 如果让你从0设计这个秒杀系统，你会去掉Redis Stream这层吗？为什么？

    **两种方案对比**：

    **方案A（当前方案）**：
    ```
    Lua → Redis Stream → Relay → Kafka → Consumer
    ```

    **方案B（去掉Stream）**：
    ```
    Lua → Kafka → Consumer
    ```

    **方案B的优点**：
    - 链路更短，延迟更低
    - 少一个组件，少一点复杂度
    - 少一次消息复制

    **方案B的问题**：
    - Lua脚本需要直接调用Kafka，生产者代码侵入Lua或业务代码
    - Kafka不可用时，Lua脚本也会失败
    - 无法利用Redis作为入口缓冲

    **我的选择**：根据场景选择

    | 场景 | 推荐方案 |
    |-----|---------|
    | Kafka稳定性有保障 | 方案B，直接用Kafka |
    | 需要Kafka和Lua脚本解耦 | 方案A，Stream作为缓冲 |
    | 追求最低延迟 | 方案B |
    | 需要渐进式改造 | 方案A，逐步迁移 |

    **实际项目中选择方案A的原因**：
    - 已有业务依赖Redis Stream，改动成本高
    - Redis Stream作为入口缓冲，Kafka故障不影响入口
    - 渐进式演进，不是非此即彼
