package com.hmdp.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.hmdp.entity.Shop;
import com.hmdp.entity.ShopType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.LOCAL_CACHE_SHOP_MAXIMUM_SIZE;
import static com.hmdp.utils.RedisConstants.LOCAL_CACHE_SHOP_TTL;
import static com.hmdp.utils.RedisConstants.LOCAL_CACHE_SHOP_TYPE_MAXIMUM_SIZE;
import static com.hmdp.utils.RedisConstants.LOCAL_CACHE_SHOP_TYPE_TTL;

/**
 * 本地缓存配置。
 *
 * 项目里的商户详情和商户类型列表都采用两级缓存：
 * 1. L1 使用 Caffeine 提供单机内存缓存
 * 2. L2 使用 Redis 提供分布式共享缓存
 *
 * 这里主要负责创建两个本地缓存 Bean，缓存失效则交给
 * `CacheClient` 和 Redis Pub/Sub 广播机制处理。
 */
@Configuration
public class LocalCacheConfig {

    /**
     * 商户详情本地缓存。
     *
     * key: 商户 ID
     * value: Shop
     */
    @Bean("shopLocalCache")
    public Cache<Long, Shop> shopLocalCache() {
        return Caffeine.newBuilder()
                .maximumSize(LOCAL_CACHE_SHOP_MAXIMUM_SIZE)
                .expireAfterWrite(LOCAL_CACHE_SHOP_TTL, TimeUnit.MINUTES)
                .build();
    }

    /**
     * 商户类型列表本地缓存。
     *
     * key: 固定字符串 `shop-type:list`
     * value: List<ShopType>
     */
    @Bean("shopTypeLocalCache")
    public Cache<String, List<ShopType>> shopTypeLocalCache() {
        return Caffeine.newBuilder()
                .maximumSize(LOCAL_CACHE_SHOP_TYPE_MAXIMUM_SIZE)
                .expireAfterWrite(LOCAL_CACHE_SHOP_TYPE_TTL, TimeUnit.MINUTES)
                .build();
    }
}
