package com.hmdp.mq;

/**
 * 秒杀订单消息体 —— 在Kafka中传递的订单信息
 *
 * 【在链路中的位置】
 *
 *   seckill.lua → Redis Stream → RedisStreamToKafkaRelay → Kafka → VoucherOrderConsumer
 *                                                          ↑
 *                                              ★ 本消息体 ★
 *
 * 【消息流转】
 *   1. RedisStreamToKafkaRelay 从 Redis Stream 读取订单数据
 *   2. 构建 VoucherOrderMessage 对象
 *   3. 序列化后发送到 Kafka
 *   4. VoucherOrderConsumer 从 Kafka 消费消息
 *   5. 反序列化为 VoucherOrderMessage
 *   6. 调用 VoucherOrderServiceImpl.handleVoucherOrderFromMQ() 落库
 *
 * 【字段说明】
 *   - orderId：订单ID（全局唯一，由RedisIdWorker生成）
 *   - userId：用户ID
 *   - voucherId：优惠券ID
 *   - createTime：创建时间（毫秒时间戳）
 *   - traceId：追踪ID（用于日志和调试，这里用Redis Stream消息ID）
 *
 * 【序列化】
 *   使用 Jackson ObjectMapper 序列化为 JSON 字符串后在 Kafka 中传输。
 *   生产者端：objectMapper.writeValueAsString(msg)
 *   消费者端：objectMapper.readValue(payload, VoucherOrderMessage.class)
 */
public class VoucherOrderMessage {
    /** 订单ID（全局唯一，由RedisIdWorker生成） */
    private Long orderId;
    /** 用户ID */
    private Long userId;
    /** 优惠券ID */
    private Long voucherId;
    /** 创建时间（毫秒时间戳） */
    private Long createTime;
    /** 追踪ID（用于日志追踪，这里用Redis Stream消息ID） */
    private String traceId;

    public Long getOrderId() {
        return orderId;
    }

    public void setOrderId(Long orderId) {
        this.orderId = orderId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Long getVoucherId() {
        return voucherId;
    }

    public void setVoucherId(Long voucherId) {
        this.voucherId = voucherId;
    }

    public Long getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Long createTime) {
        this.createTime = createTime;
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }
}