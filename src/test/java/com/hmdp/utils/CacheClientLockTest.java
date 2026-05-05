package com.hmdp.utils;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CacheClientLockTest {

    @Test
    void tryLockShouldStoreCallerTokenInsteadOfFixedValue() {
        StringRedisTemplate stringRedisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent("lock:test:1", "owner-1", 10L, TimeUnit.SECONDS)).thenReturn(Boolean.TRUE);

        CacheClient cacheClient = new CacheClient(stringRedisTemplate);

        assertTrue(cacheClient.tryLock("lock:test:1", "owner-1", 10L));
        verify(valueOperations).setIfAbsent("lock:test:1", "owner-1", 10L, TimeUnit.SECONDS);
    }

    @Test
    void unlockShouldUseLuaScriptInsteadOfBlindDelete() {
        StringRedisTemplate stringRedisTemplate = mock(StringRedisTemplate.class);
        CacheClient cacheClient = new CacheClient(stringRedisTemplate);

        cacheClient.unlock("lock:test:1", "owner-1");

        verify(stringRedisTemplate).execute(any(), eq(Collections.singletonList("lock:test:1")), eq("owner-1"));
        verify(stringRedisTemplate, never()).delete("lock:test:1");
    }
}
