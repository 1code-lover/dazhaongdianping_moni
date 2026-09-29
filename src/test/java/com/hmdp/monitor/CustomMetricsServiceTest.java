package com.hmdp.monitor;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CustomMetricsServiceTest {

    @Test
    void shouldExposeSeckillCountersAndRedisHitRatio() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        CustomMetricsService metrics = new CustomMetricsService(registry);

        metrics.incrementSeckillSuccess();
        metrics.incrementSeckillFail();
        metrics.recordRedisHit();
        metrics.recordRedisMiss();

        assertEquals(1.0, registry.get("seckill.success.count").counter().count());
        assertEquals(1.0, registry.get("seckill.fail.count").counter().count());
        assertEquals(0.5, registry.get("redis.hit.ratio").gauge().value());
    }
}
