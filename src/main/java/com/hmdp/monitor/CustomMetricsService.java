package com.hmdp.monitor;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicInteger;

@Service
public class CustomMetricsService {

    private final Counter seckillSuccessCounter;
    private final Counter seckillFailCounter;
    private final AtomicInteger redisHitCount = new AtomicInteger(0);
    private final AtomicInteger redisMissCount = new AtomicInteger(0);

    public CustomMetricsService(MeterRegistry registry) {
        seckillSuccessCounter = Counter.builder("seckill.success.count")
                .description("秒杀成功次数")
                .register(registry);
        
        seckillFailCounter = Counter.builder("seckill.fail.count")
                .description("秒杀失败次数")
                .register(registry);
        
        Gauge.builder("redis.hit.ratio", this, CustomMetricsService::getRedisHitRatio)
                .description("Redis缓存命中率")
                .register(registry);
    }
    
    public void incrementSeckillSuccess() {
        seckillSuccessCounter.increment();
    }
    
    public void incrementSeckillFail() {
        seckillFailCounter.increment();
    }
    
    public void recordRedisHit() {
        redisHitCount.incrementAndGet();
    }
    
    public void recordRedisMiss() {
        redisMissCount.incrementAndGet();
    }
    
    public double getRedisHitRatio() {
        int hits = redisHitCount.get();
        int misses = redisMissCount.get();
        if (hits + misses == 0) {
            return 0.0;
        }
        return (double) hits / (hits + misses);
    }
}
