package com.hmdp.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hmdp.service.IVoucherOrderFailTaskService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

/**
 * 死信队列消费者 —— 处理 Kafka 重试3次仍失败的消息
 *
 * 【什么时候进入死信队列】
 *
 *   VoucherOrderConsumer 消费消息
 *        │
 *        ▼
 *   处理失败 → Kafka 重试（最多3次）
 *        │
 *        ▼
 *   仍失败 → SeekToCurrentErrorHandler
 *        │
 *        ▼
 *   发送到 DLT（Dead Letter Topic）
 *        │
 *        ▼
 *   ★ 本类：VoucherOrderDeadLetterConsumer ★
 *
 * 【死信Topic命名】
 *   原Topic：seckill-order
 *   死信Topic：seckill-order-dlt
 *
 * 【死信消费者做什么】
 *   1. 接收死信消息
 *   2. 调用 failTaskService 保存死信任务
 *   3. 同时执行 Redis 回滚（库存+用户标记）
 *
 * 【为什么死信也要回滚Redis】
 *   死信意味着消息处理彻底失败了（3次重试都没成功）。
 *   虽然主消费逻辑失败了，但 Redis 的预扣仍然有效。
 *   需要回滚 Redis，否则：
 *     - 库存被预扣但无法释放
 *     - 用户被标记但订单未创建
 *
 * 【死信处理策略】
 *   - 保存到数据库表 tb_voucher_order_fail_task
 *   - 标记状态为 ROLLBACK_DONE 或 MANUAL_HANDLE_REQUIRED
 *   - 可通过管理后台人工处理或定时重试
 */
@Component
public class VoucherOrderDeadLetterConsumer {

    private static final Logger log = LoggerFactory.getLogger(VoucherOrderDeadLetterConsumer.class);

    private final ObjectMapper objectMapper;
    private final IVoucherOrderFailTaskService failTaskService;

    public VoucherOrderDeadLetterConsumer(ObjectMapper objectMapper, IVoucherOrderFailTaskService failTaskService) {
        this.objectMapper = objectMapper;
        this.failTaskService = failTaskService;
    }

    /**
     * 死信消息监听器
     *
     * @param payload          原始消息体
     * @param topic            原Topic名称
     * @param deliveryAttempt  重试次数
     */
    @KafkaListener(topics = "${spring.kafka.template.default-topic:seckill-order}-dlt",
            groupId = "${spring.kafka.consumer.group-id:seckill-order-g1}-dlt")
    public void onDeadLetterMessage(String payload,
                                    @Header(value = KafkaHeaders.RECEIVED_TOPIC, required = false) String topic,
                                    @Header(value = KafkaHeaders.DELIVERY_ATTEMPT, required = false) Integer deliveryAttempt) {
        try {
            // 1. 反序列化消息
            VoucherOrderMessage message = objectMapper.readValue(payload, VoucherOrderMessage.class);

            // 2. 获取重试次数
            int retryCount = deliveryAttempt == null ? 0 : deliveryAttempt;

            // 3. 保存死信任务并回滚Redis
            //    内部会：落库 + 回滚Redis预扣
            failTaskService.saveDeadLetterTaskAndRollback(topic, payload, message, retryCount);

            log.error("Kafka dead-letter captured, traceId={}, orderId={}, userId={}, voucherId={}, topic={}, retryCount={}",
                    message.getTraceId(), message.getOrderId(), message.getUserId(), message.getVoucherId(), topic, retryCount);
        } catch (Exception e) {
            // 死信消费失败，只打日志，不抛异常
            // 因为已经是死信了，再失败也没意义
            log.error("Kafka dead-letter consume failed, topic={}, payload={}", topic, payload, e);
        }
    }
}