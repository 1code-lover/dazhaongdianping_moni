package com.hmdp.config;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.ListOffsetsResult;
import org.apache.kafka.clients.admin.OffsetSpec;
import org.apache.kafka.clients.admin.TopicDescription;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Kafka 消费堆积（Lag）监控调度器
 *
 * 【什么是 Lag】
 *
 *   Lag = 消费组已提交位点 - 日志末端位置
 *   表示有多少消息还未消费。
 *
 *   图示：
 *     分区0: [msg1, msg2, msg3, msg4, msg5]
 *                              ↑ 已提交位点=3
 *                                         ↑ 末端=5
 *                                         Lag=2
 *
 * 【Lag过大的影响】
 *
 *   - 消息处理延迟：用户下单后很久才收到确认
 *   - 内存压力：Kafka服务端存储大量未消费消息
 *   - 磁盘压力：消息堆积占用磁盘空间
 *
 * 【监控逻辑】
 *
 *   每分钟（可配置）执行一次：
 *     1. 获取 Topic 的所有分区
 *     2. 获取消费组在各分区的已提交位点
 *     3. 获取各分区的日志末端位置
 *     4. 计算每个分区的 Lag = 末端 - 已提交
 *     5. 累加所有分区的 Lag
 *     6. 如果超过阈值（默认500），打 WARN 日志
 *
 * 【配置】
 *
 *   - app.kafka.monitor.enabled：是否开启监控
 *   - app.kafka.monitor.interval-ms：监控间隔，默认1分钟
 *   - app.kafka.monitor.lag-threshold：告警阈值，默认500
 *
 * 【如何判断消费是否正常】
 *
 *   - Lag = 0：消息刚生产就被消费了，最理想
 *   - Lag 较小（<100）：消费正常
 *   - Lag 突然增大：消费变慢了，可能是：
 *       - 消费者实例减少了（宕机）
 *       - 消费逻辑变慢了（数据库慢查询）
 *       - 外部依赖故障（Redis/DB不可用）
 *   - Lag 持续增长：消费速度跟不上生产速度
 */
@Component
@ConditionalOnProperty(prefix = "app.kafka.monitor", name = "enabled", havingValue = "true", matchIfMissing = true)
public class KafkaLagMonitorScheduler {

    private static final Logger log = LoggerFactory.getLogger(KafkaLagMonitorScheduler.class);

    @Resource
    private AdminClient kafkaAdminClient;

    /** Topic名称，与 VoucherOrderConsumer 订阅的 Topic 一致 */
    @Value("${spring.kafka.template.default-topic:seckill-order}")
    private String topic;

    /** 消费组ID，与 VoucherOrderConsumer 所属的消费者组一致 */
    @Value("${spring.kafka.consumer.group-id:seckill-order-g1}")
    private String groupId;

    /** Lag 告警阈值，超过此值打 WARN */
    @Value("${app.kafka.monitor.lag-threshold:500}")
    private long lagThreshold;

    /**
     * 检查消费 Lag
     *
     * 每隔1分钟（默认）执行一次
     */
    @Scheduled(fixedDelayString = "${app.kafka.monitor.interval-ms:60000}")
    public void checkLag() {
        try {
            // ========== 第1步：获取 Topic 的所有分区 ==========
            Map<String, TopicDescription> desc = kafkaAdminClient
                    .describeTopics(Collections.singleton(topic))
                    .all()
                    .get(15, TimeUnit.SECONDS);
            TopicDescription td = desc.get(topic);
            if (td == null) {
                log.warn("Kafka lag monitor: topic {} not found", topic);
                return;
            }

            // 构建分区列表
            List<TopicPartition> partitions = td.partitions().stream()
                    .map(p -> new TopicPartition(topic, p.partition()))
                    .collect(Collectors.toList());

            if (partitions.isEmpty()) {
                log.debug("Kafka lag monitor: topic {} has no partitions", topic);
                return;
            }

            // ========== 第2步：获取消费组已提交的位点 ==========
            Map<TopicPartition, OffsetAndMetadata> committed;
            try {
                committed = kafkaAdminClient.listConsumerGroupOffsets(groupId)
                        .partitionsToOffsetAndMetadata()
                        .get(15, TimeUnit.SECONDS);
            } catch (Exception e) {
                log.debug("Kafka lag monitor: no offsets for group {} yet: {}", groupId, e.toString());
                committed = Collections.emptyMap();
            }

            // ========== 第3步：获取各分区日志末端位置 ==========
            Map<TopicPartition, OffsetSpec> specs = partitions.stream()
                    .collect(Collectors.toMap(tp -> tp, tp -> OffsetSpec.latest()));
            Map<TopicPartition, ListOffsetsResult.ListOffsetsResultInfo> endOffsets =
                    kafkaAdminClient.listOffsets(specs).all().get(15, TimeUnit.SECONDS);

            // ========== 第4步：计算总 Lag ==========
            long totalLag = 0;
            int withCommit = 0;
            for (TopicPartition tp : partitions) {
                ListOffsetsResult.ListOffsetsResultInfo endInfo = endOffsets.get(tp);
                if (endInfo == null) {
                    continue;
                }
                long end = endInfo.offset();

                OffsetAndMetadata meta = committed.get(tp);
                if (meta == null) {
                    continue;
                }
                long pos = meta.offset();

                // Lag = 末端 - 已提交
                totalLag += Math.max(0L, end - pos);
                withCommit++;
            }

            if (withCommit == 0) {
                log.debug("Kafka lag monitor: no committed offsets for group {} on topic {} yet", groupId, topic);
                return;
            }

            // ========== 第5步：判断是否超过阈值 ==========
            if (totalLag > lagThreshold) {
                log.warn(
                        "Kafka consumer lag above threshold: totalLag={} threshold={} topic={} groupId={} partitionsWithCommit={}",
                        totalLag, lagThreshold, topic, groupId, withCommit);
            } else {
                log.debug("Kafka lag ok: totalLag={} topic={} groupId={}", totalLag, topic, groupId);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Kafka lag monitor interrupted", e);
        } catch (Exception ex) {
            log.error("Kafka lag monitor failed: {}", ex.toString(), ex);
        }
    }
}