package com.hmdp.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

import static com.hmdp.utils.RedisConstants.CACHE_INVALIDATE_CHANNEL;
import static com.hmdp.utils.RedisConstants.CACHE_NAME_SHOP;
import static com.hmdp.utils.RedisConstants.CACHE_NAME_SHOP_TYPE;

/**
 * 缓存失效消息发布者 —— 负责向Redis Pub/Sub频道发布缓存失效消息
 *
 * 【在多节点部署中的角色】
 *
 * 当某个节点更新了数据（如商户信息），需要通知其他节点删除本地缓存。
 * 这个类就是"通知者"：向Redis的Pub/Sub频道发布一条消息，
 * 所有订阅了该频道的节点（包括自己）都会收到消息并删除对应的本地缓存。
 *
 * 【发布流程】
 *
 *   ShopServiceImpl.update() 更新商户
 *     │
 *     ├─ 1) updateById(shop)           → 更新数据库
 *     ├─ 2) cacheClient.delete(...)    → 删除Redis缓存（L2）
 *     ├─ 3) cacheClient.deleteShopLocalCache(id) → 删除当前节点的本地缓存（L1）
 *     └─ 4) publishShopInvalidation(id) → 【本类方法】发布缓存失效消息
 *              │
 *              ▼
 *         Redis PUBLISH cache:invalidate '{"cacheName":"shop","key":"123"}'
 *              │
 *              ▼
 *         所有订阅 cache:invalidate 频道的节点收到消息
 *              │
 *         ┌────┼────┐
 *         ▼    ▼    ▼
 *      节点A  节点B  节点C   → CacheInvalidationListener 收到消息
 *                              → 删除各自的本地缓存
 *
 * 【为什么需要广播？】
 *   本地缓存（Caffeine）是JVM进程内的，每个节点各自独立。
 *   节点A更新数据后，节点B和C的本地缓存还是旧数据。
 *   通过Redis Pub/Sub广播，可以让所有节点都知道"某个缓存失效了"，
 *   从而主动删除本地缓存，保证数据最终一致。
 *
 * 【注意】
 *   Redis Pub/Sub 是"发后即忘"模式，不保证消息一定送达。
 *   如果某个节点在发布消息时刚好断线，可能收不到这条消息。
 *   但本地缓存有TTL（5分钟/10分钟），即使收不到广播，过期后也会自动失效。
 *   所以这是一种"最终一致性"方案，而非"强一致性"。
 */
@Slf4j
@Component
public class CacheInvalidationPublisher {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private ObjectMapper objectMapper;

    /**
     * 发布商户缓存失效消息
     *
     * @param id 商户ID
     *
     * 调用时机：ShopServiceImpl.update() 更新商户后调用
     * 发布内容：{"cacheName":"shop", "key":"123"}
     */
    public void publishShopInvalidation(Long id) {
        publish(new CacheInvalidationMessage(CACHE_NAME_SHOP, String.valueOf(id)));
    }

    /**
     * 发布商户类型缓存失效消息
     *
     * @param key 本地缓存的key（如 "shop-type:list"）
     *
     * 调用时机：商户类型数据更新后调用
     * 发布内容：{"cacheName":"shop-type", "key":"shop-type:list"}
     */
    public void publishShopTypeInvalidation(String key) {
        publish(new CacheInvalidationMessage(CACHE_NAME_SHOP_TYPE, key));
    }

    /**
     * 发布缓存失效消息到Redis Pub/Sub频道
     *
     * @param message 缓存失效消息（包含cacheName和key）
     *
     * 实现原理：
     *   Redis PUBLISH channel message
     *   → 所有订阅了该channel的客户端都会收到这条消息
     *
     * 频道名：cache:invalidate（定义在RedisConstants中）
     */
    private void publish(CacheInvalidationMessage message) {
        try {
            // 将消息对象序列化为JSON字符串
            String payload = objectMapper.writeValueAsString(message);
            // 向Redis Pub/Sub频道发布消息
            stringRedisTemplate.convertAndSend(CACHE_INVALIDATE_CHANNEL, payload);
            log.info("Published cache invalidation message, channel={}, payload={}", CACHE_INVALIDATE_CHANNEL, payload);
        } catch (JsonProcessingException e) {
            log.error("Failed to publish cache invalidation message: {}", message, e);
        }
    }
}
