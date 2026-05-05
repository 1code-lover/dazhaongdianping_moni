package com.hmdp.mq;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.PreDestroy;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Component
public class RedisStreamToKafkaRelay implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(RedisStreamToKafkaRelay.class);

    private static final String STREAM_KEY = "stream.orders";
    private static final String GROUP = "relay-g1";

    private final StringRedisTemplate stringRedisTemplate;
    private final VoucherOrderProducer voucherOrderProducer;
    private final String consumerName;

    private final ExecutorService relayExecutor = Executors.newSingleThreadExecutor();
    private volatile boolean running = true;

    public RedisStreamToKafkaRelay(StringRedisTemplate stringRedisTemplate,
                                   VoucherOrderProducer voucherOrderProducer,
                                   @Value("${app.kafka.relay.consumer-name:}") String configuredConsumerName) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.voucherOrderProducer = voucherOrderProducer;
        this.consumerName = resolveConsumerName(configuredConsumerName);
    }

    @Override
    public void run(String... args) {
        initGroupIfNeeded();
        relayExecutor.submit(this::relayLoop);
    }

    @PreDestroy
    public void destroy() {
        running = false;
        relayExecutor.shutdownNow();
    }

    private void initGroupIfNeeded() {
        RecordId initRecordId = null;
        try {
            if (Boolean.FALSE.equals(stringRedisTemplate.hasKey(STREAM_KEY))) {
                initRecordId = stringRedisTemplate.opsForStream()
                        .add(STREAM_KEY, Collections.singletonMap("bootstrap", "1"));
            }
            stringRedisTemplate.opsForStream().createGroup(STREAM_KEY, ReadOffset.latest(), GROUP);
            log.info("Created stream group, stream={}, group={}", STREAM_KEY, GROUP);
        } catch (Exception e) {
            String msg = e.getMessage();
            if (msg != null && msg.contains("BUSYGROUP")) {
                log.info("Stream group already exists, stream={}, group={}", STREAM_KEY, GROUP);
            } else {
                log.warn("Init stream group failed, stream={}, group={}", STREAM_KEY, GROUP, e);
            }
        } finally {
            if (initRecordId != null) {
                try {
                    stringRedisTemplate.opsForStream().delete(STREAM_KEY, initRecordId);
                } catch (Exception ignore) {
                    log.debug("Delete bootstrap stream record failed, recordId={}", initRecordId.getValue());
                }
            }
        }
    }

    private void relayLoop() {
        recoverPendingList();
        while (running && !Thread.currentThread().isInterrupted()) {
            try {
                List<MapRecord<String, Object, Object>> records = stringRedisTemplate.opsForStream().read(
                        Consumer.from(GROUP, consumerName),
                        StreamReadOptions.empty().count(1).block(Duration.ofSeconds(2)),
                        StreamOffset.create(STREAM_KEY, ReadOffset.lastConsumed())
                );
                if (records == null || records.isEmpty()) {
                    continue;
                }
                handleRecord(records.get(0), false);
            } catch (Exception e) {
                if (!running || Thread.currentThread().isInterrupted() || isInterruptedError(e)) {
                    log.info("Relay loop stopped.");
                    break;
                }
                String msg = e.getMessage();
                if (msg != null && msg.contains("NOGROUP")) {
                    initGroupIfNeeded();
                } else {
                    log.error("Relay stream message failed", e);
                }
            }
        }
    }

    private void recoverPendingList() {
        while (running && !Thread.currentThread().isInterrupted()) {
            try {
                List<MapRecord<String, Object, Object>> records = stringRedisTemplate.opsForStream().read(
                        Consumer.from(GROUP, consumerName),
                        StreamReadOptions.empty().count(10),
                        StreamOffset.create(STREAM_KEY, ReadOffset.from("0"))
                );
                if (records == null || records.isEmpty()) {
                    return;
                }
                for (MapRecord<String, Object, Object> record : records) {
                    handleRecord(record, true);
                }
            } catch (Exception e) {
                if (!running || Thread.currentThread().isInterrupted() || isInterruptedError(e)) {
                    return;
                }
                log.error("Recover relay pending-list failed, e={}", e);
                return;
            }
        }
    }

    private void handleRecord(MapRecord<String, Object, Object> record, boolean pendingReplay) {
        boolean success = forwardToKafka(record, pendingReplay);
        if (success) {
            stringRedisTemplate.opsForStream().acknowledge(STREAM_KEY, GROUP, record.getId());
        }
    }

    private boolean isInterruptedError(Throwable t) {
        Throwable current = t;
        while (current != null) {
            if (current instanceof InterruptedException) {
                return true;
            }
            String msg = current.getMessage();
            if (msg != null && msg.contains("interrupted")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private boolean forwardToKafka(MapRecord<String, Object, Object> record, boolean pendingReplay) {
        try {
            Map<Object, Object> value = record.getValue();
            Object orderIdObj = value.get("orderId");
            if (orderIdObj == null) {
                orderIdObj = value.get("id");
            }
            if (orderIdObj == null || value.get("userId") == null || value.get("voucherId") == null) {
                log.warn("Illegal stream message, recordId={}, value={}", record.getId().getValue(), value);
                return true;
            }

            VoucherOrderMessage msg = new VoucherOrderMessage();
            msg.setOrderId(Long.valueOf(orderIdObj.toString()));
            msg.setUserId(Long.valueOf(value.get("userId").toString()));
            msg.setVoucherId(Long.valueOf(value.get("voucherId").toString()));
            msg.setCreateTime(System.currentTimeMillis());
            msg.setTraceId(record.getId().getValue());

            boolean sent = voucherOrderProducer.send(msg);
            if (sent) {
                log.info("Relay forward success, traceId={}, orderId={}, userId={}, voucherId={}, pendingReplay={}",
                        msg.getTraceId(), msg.getOrderId(), msg.getUserId(), msg.getVoucherId(), pendingReplay);
            } else {
                log.error("Relay forward failed, traceId={}, orderId={}, userId={}, voucherId={}, pendingReplay={}",
                        msg.getTraceId(), msg.getOrderId(), msg.getUserId(), msg.getVoucherId(), pendingReplay);
            }
            return sent;
        } catch (Exception e) {
            log.error("Convert stream message failed, recordId={}, pendingReplay={}", record.getId().getValue(), pendingReplay, e);
            return false;
        }
    }

    String getConsumerName() {
        return consumerName;
    }

    private String resolveConsumerName(String configuredConsumerName) {
        if (configuredConsumerName != null) {
            String trimmed = configuredConsumerName.trim();
            if (!trimmed.isEmpty()) {
                return trimmed;
            }
        }
        return "relay-" + UUID.randomUUID();
    }
}
