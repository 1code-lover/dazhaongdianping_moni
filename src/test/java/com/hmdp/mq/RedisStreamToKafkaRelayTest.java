package com.hmdp.mq;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class RedisStreamToKafkaRelayTest {

    @Test
    void shouldUseConfiguredConsumerNameWhenProvided() {
        RedisStreamToKafkaRelay relay = new RedisStreamToKafkaRelay(
                mock(StringRedisTemplate.class),
                mock(VoucherOrderProducer.class),
                "relay-custom"
        );

        assertEquals("relay-custom", relay.getConsumerName());
    }

    @Test
    void shouldGenerateStableNonBlankConsumerNamePerInstance() {
        RedisStreamToKafkaRelay relay1 = new RedisStreamToKafkaRelay(
                mock(StringRedisTemplate.class),
                mock(VoucherOrderProducer.class),
                null
        );
        RedisStreamToKafkaRelay relay2 = new RedisStreamToKafkaRelay(
                mock(StringRedisTemplate.class),
                mock(VoucherOrderProducer.class),
                null
        );

        assertNotNull(relay1.getConsumerName());
        assertTrue(relay1.getConsumerName().startsWith("relay-"));
        assertNotEquals(relay1.getConsumerName(), relay2.getConsumerName());
    }
}
