package com.hmdp.service.impl;

import com.hmdp.service.RateLimitService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.concurrent.TimeUnit;

@Service
public class RateLimitServiceImpl implements RateLimitService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public boolean isAllowed(String key, int timeWindowSeconds, int maxRequests) {
        Long count = stringRedisTemplate.opsForValue().increment(key);
        if (count != null && count == 1L) {
            stringRedisTemplate.expire(key, timeWindowSeconds, TimeUnit.SECONDS);
        }
        return count != null && count <= maxRequests;
    }

    @Override
    public boolean tryAcquireCooldown(String key, int cooldownSeconds) {
        Boolean success = stringRedisTemplate.opsForValue()
                .setIfAbsent(key, "1", cooldownSeconds, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(success);
    }

    @Override
    public Long getCurrentCount(String key) {
        String count = stringRedisTemplate.opsForValue().get(key);
        return count == null ? 0L : Long.valueOf(count);
    }
}
