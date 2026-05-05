package com.hmdp.config;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.SeekToCurrentErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

import java.util.Properties;

/**
 * Kafka 配置类
 *
 * 【在链路中的位置】
 *
 *   VoucherOrderProducerImpl.send()
 *        │
 *        │ 发送到 Kafka
 *        ▼
 *   Kafka Cluster
 *        │
 *        ▼
 *   VoucherOrderConsumer (@KafkaListener)
 *        │
 *        │ 使用本配置类创建的 kafkaListenerContainerFactory
 *        ▼
 *   订单落库
 *
 * 【核心配置】
 *
 *   1. AdminClient
 *      - 用于运维管理（查询Topic、消费组、offset等）
 *      - 不用于业务消息发送/消费
 *
 *   2. kafkaListenerContainerFactory
 *      - @KafkaListener 实际使用的监听器工厂
 *      - 手动ACK：收到消息后立即ACK
 *      - 重试机制：失败重试3次，间隔1秒
 *      - 死信处理：重试失败后发送到 DLT（Dead Letter Topic）
 *
 * 【手动ACK模式】
 *   MANUAL_IMMEDIATE：收到消息后立即ACK，不等待批量处理
 *   这样保证：消息处理完成后再提交offset，不会丢失消息
 *
 * 【重试机制】
 *   SeekToCurrentErrorHandler：消费失败时，回到当前位点重试
 *   FixedBackOff(1000L, 3L)：重试3次，每次间隔1秒
 *
 * 【死信队列】
 *   消息重试3次仍失败后，发送到 Dead Letter Topic
 *   Topic命名：原Topic + "-dlt"
 *   例如：seckill-order → seckill-order-dlt
 *   死信消息由 VoucherOrderDeadLetterConsumer 处理
 */
@Configuration
@EnableKafka
public class KafkaConfig {

    /**
     * Kafka AdminClient
     *
     * 用于运维管理操作，不用于业务消息发送/消费。
     * 可以查询Topic列表、消费组offset、分区信息等。
     *
     * 常见用途：
     *   - KafkaLagMonitorScheduler：监控消费堆积
     *   - 查询消费组状态
     *   - 管理Topic（创建、删除）
     */
    @Bean(destroyMethod = "close")
    public AdminClient kafkaAdminClient(@Value("${spring.kafka.bootstrap-servers}") String bootstrapServers) {
        Properties props = new Properties();
        props.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, 15_000);
        return AdminClient.create(props);
    }

    /**
     * Kafka Listener 容器工厂
     *
     * @KafkaListener 注解默认使用这个工厂创建监听容器。
     * 核心配置：
     *   - 手动立即ACK（MANUAL_IMMEDIATE）
     *   - 失败重试3次（SeekToCurrentErrorHandler + FixedBackOff）
     *   - 死信队列（DeadLetterPublishingRecoverer）
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory(
            ConsumerFactory<String, String> consumerFactory,
            KafkaTemplate<String, String> kafkaTemplate) {
        ConcurrentKafkaListenerContainerFactory<String, String> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        // 并发数设为1，保证同一分区消息顺序处理
        factory.setConcurrency(1);

        // ========== 手动立即ACK ==========
        // 收到消息后立即ACK，不等待批量处理
        // 保证消息处理完成后再提交offset
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);

        // ========== 死信处理 ==========
        // 当消息处理失败重试3次仍失败时，发送到死信Topic
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                kafkaTemplate,
                // 死信Topic命名：原Topic + "-dlt"，分区与原消息相同
                (ConsumerRecord<?, ?> record, Exception ex) -> new TopicPartition(record.topic() + "-dlt", record.partition())
        );

        // ========== 错误处理器 ==========
        // SeekToCurrentErrorHandler：消费失败时，回到当前位点重试
        // FixedBackOff(1000L, 3L)：重试3次，每次间隔1秒
        factory.setErrorHandler(new SeekToCurrentErrorHandler(recoverer, new FixedBackOff(1000L, 3L)));

        return factory;
    }
}