package com.hmdp.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.service.SeckillRedisRollbackService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Kafka 消费者 —— 消费秒杀订单消息，完成数据库落库
 *
 * 【在链路中的位置】
 *
 *   RedisStreamToKafkaRelay → Kafka → ★ 本类 ★
 *                                    │
 *                            onMessage()
 *                                    │
 *                            VoucherOrderServiceImpl
 *                            .handleVoucherOrderFromMQ()
 *                                    │
 *                            createVoucherOrder()
 *                                    │
 *                                    ▼
 *                              数据库落库
 *
 * 【消费流程】
 *   1. @KafkaListener 收到消息
 *   2. 反序列化为 VoucherOrderMessage
 *   3. 构建 VoucherOrder 对象
 *   4. 调用 handleVoucherOrderFromMQ() 完成落库
 *   5. 手动 ACK 确认消息
 *
 * 【ACK机制】
 *   - 使用 MANUAL_IMMEDIATE 模式
 *   - 消息处理完成后立即调用 acknowledgment.acknowledge()
 *   - 如果处理失败，不 ACK，Kafka 会重试
 *   - 重试 3 次仍失败，发送到 DLT（死信队列）
 *
 * 【异常处理】
 *   1. 可恢复异常（网络超时、DB临时不可用）
 *      → 抛出异常，让 Kafka 重试
 *   2. 不可恢复异常（数据错误、系统错误）
 *      → 回滚 Redis 预扣，ACK 跳过
 *   3. 异常分类通过 MqExceptionClassifier 判断
 *
 * 【幂等性】
 *   - createVoucherOrder() 中有 DuplicateKeyException 处理
 *   - 重复消费不会创建重复订单
 */
@Component
public class VoucherOrderConsumer {

    private static final Logger log = LoggerFactory.getLogger(VoucherOrderConsumer.class);

    private final ObjectMapper objectMapper;
    private final IVoucherOrderService voucherOrderService;
    private final SeckillRedisRollbackService seckillRedisRollbackService;
    /** 用于测试：fail-once 触发器 */
    private final AtomicBoolean failOnceTriggered = new AtomicBoolean(false);

    /** 测试用：是否触发 fail-once */
    @Value("${app.kafka.consumer.fail-once:false}")
    private boolean failOnce;
    /** 测试用：是否总是失败 */
    @Value("${app.kafka.consumer.fail-always:false}")
    private boolean failAlways;
    /** 测试用：特定 voucherId 触发失败 */
    @Value("${app.kafka.consumer.fail-voucher-id:-1}")
    private long failVoucherId;

    public VoucherOrderConsumer(ObjectMapper objectMapper, IVoucherOrderService voucherOrderService,
                                SeckillRedisRollbackService seckillRedisRollbackService) {
        this.objectMapper = objectMapper;
        this.voucherOrderService = voucherOrderService;
        this.seckillRedisRollbackService = seckillRedisRollbackService;
    }

    /**
     * Kafka 消息监听器
     *
     * @param payload         消息体（JSON字符串）
     * @param acknowledgment   ACK控制器
     */
    @KafkaListener(
            topics = "${spring.kafka.template.default-topic:seckill-order}",
            groupId = "${spring.kafka.consumer.group-id:seckill-order-g1}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onMessage(String payload, Acknowledgment acknowledgment) {
        VoucherOrderMessage msg = null;
        try {
            // 1. 反序列化消息
            msg = objectMapper.readValue(payload, VoucherOrderMessage.class);

            // 2. 测试用：fail-always 触发
            Long voucherId = msg.getVoucherId();
            boolean failVoucherMatch = failVoucherId <= 0 || (voucherId != null && voucherId == failVoucherId);
            if (failAlways && failVoucherMatch) {
                log.warn("Kafka consume fail-always triggered, traceId={}, orderId={}, voucherId={}",
                        msg.getTraceId(), msg.getOrderId(), voucherId);
                throw new IllegalStateException("consumer fail-always for DLT verification");
            }

            // 3. 测试用：fail-once 触发（只触发一次）
            if (failOnce && failOnceTriggered.compareAndSet(false, true)) {
                log.warn("Kafka consume fail-once triggered, traceId={}, orderId={}", msg.getTraceId(), msg.getOrderId());
                throw new IllegalStateException("consumer fail-once for ack verification");
            }

            // 4. 构建订单对象
            VoucherOrder order = new VoucherOrder();
            order.setId(msg.getOrderId());
            order.setUserId(msg.getUserId());
            order.setVoucherId(msg.getVoucherId());

            // 5. 调用服务层落库
            voucherOrderService.handleVoucherOrderFromMQ(order);

            // 6. 手动 ACK
            acknowledgment.acknowledge();
            log.info("Kafka consume success, traceId={}, orderId={}, userId={}, voucherId={}",
                    msg.getTraceId(), msg.getOrderId(), msg.getUserId(), msg.getVoucherId());

        } catch (Exception e) {
            if (msg == null) {
                // 反序列化失败，无法获取订单信息，直接ACK跳过
                log.error("Kafka consume failed before deserialize, payload={}", payload, e);
                throw new RuntimeException(e);
            }

            log.error("Kafka consume failed, traceId={}, orderId={}, userId={}, voucherId={}",
                    msg.getTraceId(), msg.getOrderId(), msg.getUserId(), msg.getVoucherId(), e);

            // 判断异常是否可恢复
            if (!MqExceptionClassifier.isRecoverable(e)) {
                // 不可恢复异常：回滚 Redis 预扣，然后 ACK
                seckillRedisRollbackService.rollback(msg.getVoucherId(), msg.getUserId(),
                        "non-recoverable: " + e.getClass().getSimpleName());
                acknowledgment.acknowledge();
                log.warn("Kafka consume non-recoverable, ack after redis rollback, traceId={}, orderId={}",
                        msg.getTraceId(), msg.getOrderId());
                return;
            }

            // 可恢复异常：抛出，让 Kafka 重试
            throw new RuntimeException(e);
        }
    }
}