package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.hmdp.entity.ShopType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Supplier;

import static com.hmdp.utils.RedisConstants.CACHE_NULL_TTL;
import static com.hmdp.utils.RedisConstants.LOCK_SHOP_KEY;
import static com.hmdp.utils.RedisConstants.LOCK_SHOP_TTL;

@Slf4j
@Component
public class CacheClient {

    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT;

    static {
        UNLOCK_SCRIPT = new DefaultRedisScript<>();
        UNLOCK_SCRIPT.setLocation(new ClassPathResource("unlock.lua"));
        UNLOCK_SCRIPT.setResultType(Long.class);
    }

    private final StringRedisTemplate stringRedisTemplate;

    @Resource
    private ObjectMapper objectMapper;

    @Resource
    @Qualifier("shopLocalCache")
    private Cache<Long, Object> shopLocalCache;

    @Resource
    @Qualifier("shopTypeLocalCache")
    private Cache<String, List<ShopType>> shopTypeLocalCache;

    public CacheClient(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public void set(String key, Object value, Long time, TimeUnit unit) {
        Long randomTtl = time + ThreadLocalRandom.current().nextLong(time / 5);
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), randomTtl, unit);
    }

    public void delete(String key) {
        stringRedisTemplate.delete(key);
    }

    public Cache<Long, Object> getShopLocalCache() {
        return shopLocalCache;
    }

    public Cache<String, List<ShopType>> getShopTypeLocalCache() {
        return shopTypeLocalCache;
    }

    public void deleteShopLocalCache(Long id) {
        shopLocalCache.invalidate(id);
    }

    public void deleteShopTypeLocalCache(String key) {
        shopTypeLocalCache.invalidate(key);
    }

    public <R> R queryWithLocalCache(
            Cache<Long, Object> localCache, Long id, String keyPrefix, Class<R> type,
            Function<Long, R> dbFallback, Long time, TimeUnit unit) {

        Object localValue = localCache.getIfPresent(id);
        if (localValue != null) {
            log.info("Local cache hit, key={}{}", keyPrefix, id);
            return type.cast(localValue);
        }
        log.info("Local cache miss, key={}{}", keyPrefix, id);

        R r = queryWithPassThrough(keyPrefix, id, type, dbFallback, time, unit);
        if (r != null) {
            localCache.put(id, r);
            log.info("Local cache put, key={}{}", keyPrefix, id);
        }
        return r;
    }

    public <R> R queryListWithLocalCache(
            Cache<String, List<ShopType>> localCache, String localKey, String redisKey,
            TypeReference<R> typeReference, Supplier<R> dbFallback, Long time, TimeUnit unit) {

        List<ShopType> localValue = localCache.getIfPresent(localKey);
        if (localValue != null) {
            log.info("Local cache hit, key={}", localKey);
            return objectMapper.convertValue(localValue, typeReference);
        }
        log.info("Local cache miss, key={}", localKey);

        String json = stringRedisTemplate.opsForValue().get(redisKey);
        if (StrUtil.isNotBlank(json)) {
            try {
                R r = objectMapper.readValue(json, typeReference);
                localCache.put(localKey, objectMapper.convertValue(r, new TypeReference<List<ShopType>>() {}));
                log.info("Redis cache hit, key={}, local cache refreshed", redisKey);
                return r;
            } catch (JsonProcessingException e) {
                log.warn("Failed to deserialize redis list cache, key={}", redisKey, e);
            }
        }

        R r = dbFallback.get();
        if (r != null) {
            try {
                stringRedisTemplate.opsForValue().set(redisKey, objectMapper.writeValueAsString(r), time, unit);
                log.info("Redis cache put, key={}", redisKey);
            } catch (JsonProcessingException e) {
                log.warn("Failed to serialize redis list cache, key={}", redisKey, e);
            }
            localCache.put(localKey, objectMapper.convertValue(r, new TypeReference<List<ShopType>>() {}));
            log.info("Local cache put, key={}", localKey);
        }
        return r;
    }

    public void setWithLogicalExpire(String key, Object value, Long time, TimeUnit unit) {
        Long randomTtl = time + ThreadLocalRandom.current().nextLong(time / 5);
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(randomTtl)));
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }

    public <R, ID> R queryWithPassThrough(
            String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFallback, Long time, TimeUnit unit) {
        String key = keyPrefix + id;

        String json = stringRedisTemplate.opsForValue().get(key);
        if (StrUtil.isNotBlank(json)) {
            log.info("Redis cache hit, key={}", key);
            return JSONUtil.toBean(json, type);
        }
        if (json != null) {
            log.info("Redis cache hit empty marker, key={}", key);
            return null;
        }

        log.info("Redis cache miss, key={}, fallback to db", key);
        R r = dbFallback.apply(id);
        if (r == null) {
            stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
            log.info("Redis cache put empty marker, key={}", key);
            return null;
        }
        this.set(key, r, time, unit);
        log.info("Redis cache put, key={}", key);
        return r;
    }

    public <R, ID> R queryWithLogicalExpire(
            String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFallback, Long time, TimeUnit unit) {
        String key = keyPrefix + id;
        String json = stringRedisTemplate.opsForValue().get(key);
        if (StrUtil.isBlank(json)) {
            return null;
        }

        RedisData redisData = JSONUtil.toBean(json, RedisData.class);
        R r = JSONUtil.toBean((JSONObject) redisData.getData(), type);
        LocalDateTime expireTime = redisData.getExpireTime();

        if (expireTime.isAfter(LocalDateTime.now())) {
            return r;
        }

        String lockKey = LOCK_SHOP_KEY + id;
        String lockValue = newLockValue();
        boolean isLock = tryLock(lockKey, lockValue, LOCK_SHOP_TTL);
        if (isLock) {
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                try {
                    R newR = dbFallback.apply(id);
                    this.setWithLogicalExpire(key, newR, time, unit);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    unlock(lockKey, lockValue);
                }
            });
        }
        return r;
    }

    public <R, ID> R queryWithMutex(
            String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFallback, Long time, TimeUnit unit) {
        String key = keyPrefix + id;
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        if (StrUtil.isNotBlank(shopJson)) {
            return JSONUtil.toBean(shopJson, type);
        }
        if (shopJson != null) {
            return null;
        }

        String lockKey = LOCK_SHOP_KEY + id;
        String lockValue = newLockValue();
        boolean isLock = false;
        R r = null;
        try {
            isLock = tryLock(lockKey, lockValue, LOCK_SHOP_TTL);
            if (!isLock) {
                Thread.sleep(50);
                return queryWithMutex(keyPrefix, id, type, dbFallback, time, unit);
            }

            r = dbFallback.apply(id);
            if (r == null) {
                stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
                return null;
            }
            this.set(key, r, time, unit);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        } finally {
            if (isLock) {
                unlock(lockKey, lockValue);
            }
        }
        return r;
    }

    boolean tryLock(String key, String token, long timeoutSec) {
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, token, timeoutSec, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    void unlock(String key, String token) {
        stringRedisTemplate.execute(UNLOCK_SCRIPT, Collections.singletonList(key), token);
    }

    private String newLockValue() {
        return Thread.currentThread().getId() + "-" + UUID.randomUUID();
    }
}
