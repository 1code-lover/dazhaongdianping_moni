package com.hmdp.service;

public interface RateLimitService {

    boolean isAllowed(String key, int timeWindowSeconds, int maxRequests);

    boolean tryAcquireCooldown(String key, int cooldownSeconds);

    Long getCurrentCount(String key);
}
