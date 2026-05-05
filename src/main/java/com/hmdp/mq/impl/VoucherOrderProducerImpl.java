package com.hmdp.mq.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hmdp.mq.VoucherOrderMessage;
import com.hmdp.mq.VoucherOrderProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Kafka 生产者实现 —— 将订单消息发送到 Kafka
 *
 * 【在链路中的位置】
 *
 *   RedisStreamToKafkaRelay
 *        │
 *        │ forwardToKafka()
 *        ▼
 *   ★ 本类：VoucherOrderProducerImpl ★
 *        │  send()
 *        │  将消息发送到 Kafka
 *        ▼
 *   Kafka Cluster
 *        │
 *        ▼
 *   VoucherOrderConsumer 消费
 *
 * 【消息发送流程】
 *   1. 接收 VoucherOrderMessage 对象
 *   2. 序列化为 JSON 字符串
 *   3. 调用 KafkaTemplate.send() 发送到 Kafka
 *   4. 等待发送结果（同步等待，最多5秒）
 *   5. 返回发送是否成功
 *
 * 【分区策略】
 *   send(topic, key, message)
 *   - topic: seckill-order（秒杀订单主题）
 *   - key: userId（用户ID）
 *   - message: JSON序列化的消息体
 *
 *   用 userId 作为 key 的好处：
 *   - 同一用户的订单消息会发送到同一个分区
 *   - 保证同一用户的订单有序处理
 *
 * 【发送结果】
 *   - 成功：打印日志，包含 topic、partition、offset
 *   - 失败：返回 false，由调用方决定是否重试
 *
 * 【错误处理】
 *   - JsonProcessingException：序列化失败（不应该发生）
 *   - 其他异常：发送超时或网络问题
 *
 * 【注意】
 *   send() 是同步等待的，最长等待5秒。
 *   如果5秒内没有收到ACK，抛异常。
 *   这是为了确保消息确实发送成功。
 */
@Component
public class VoucherOrderProducerImpl implements VoucherOrderProducer {

    private static final Logger log = LoggerFactory.getLogger(VoucherOrderProducerImpl.class);

    /** Kafka模板，用于发送消息 */
    private final KafkaTemplate<String, String> kafkaTemplate;
    /** JSON序列化器 */
    private final ObjectMapper objectMapper;

    /** Kafka Topic名称，默认 seckill-order */
    @Value("${spring.kafka.template.default-topic:seckill-order}")
    private String topic;

    public VoucherOrderProducerImpl(KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * 发送订单消息到Kafka
     *
     * @param msg 订单消息对象
     * @return true=发送成功，false=发送失败
     */
    @Override
    public boolean send(VoucherOrderMessage msg) {
        try {
            // 1. 序列化为JSON
            String payload = objectMapper.writeValueAsString(msg);

            // 2. 发送到Kafka
            // 参数：topic, key, message
            // key=userId，保证同一用户的消息发送到同一分区，有序处理
            SendResult<String, String> sendResult = kafkaTemplate
                    .send(topic, String.valueOf(msg.getUserId()), payload)
                    .get(5, TimeUnit.SECONDS);  // 同步等待最多5秒

            // 3. 打印成功日志
            log.info("Kafka send success, traceId={}, orderId={}, userId={}, voucherId={}, topic={}, partition={}, offset={}",
                    msg.getTraceId(),
                    msg.getOrderId(),
                    msg.getUserId(),
                    msg.getVoucherId(),
                    topic,
                    sendResult.getRecordMetadata().partition(),
                    sendResult.getRecordMetadata().offset());
            return true;
        } catch (JsonProcessingException e) {
            // JSON序列化失败
            log.error("Serialize voucher order message failed, traceId={}, orderId={}", msg.getTraceId(), msg.getOrderId(), e);
            return false;
        } catch (Exception e) {
            // Kafka发送失败（超时、网络等）
            log.error("Send voucher order message to kafka failed, traceId={}, orderId={}, topic={}",
                    msg.getTraceId(), msg.getOrderId(), topic, e);
            return false;
        }
    }
}