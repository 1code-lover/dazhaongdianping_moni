package com.hmdp.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.CacheInvalidationMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.nio.charset.StandardCharsets;

import static com.hmdp.utils.RedisConstants.CACHE_NAME_SHOP;
import static com.hmdp.utils.RedisConstants.CACHE_NAME_SHOP_TYPE;

/**
 * 缓存失效消息监听者 —— 订阅Redis Pub/Sub频道，接收缓存失效消息并删除本地缓存
 *
 * 【在多节点部署中的角色】
 *
 * 这个类是缓存广播机制的"接收端"。当某个节点发布了缓存失效消息后，
 * 所有节点（包括发布者自己）的此监听器都会收到消息，并删除对应的本地缓存。
 *
 * 【消息流转过程】
 *
 *   节点A发布消息:
 *     CacheInvalidationPublisher.publishShopInvalidation(123)
 *       │
 *       ▼
 *     Redis PUBLISH cache:invalidate '{"cacheName":"shop","key":"123"}'
 *       │
 *       ├──────────────────────────────────────────┐
 *       ▼                                          ▼
 *     节点A的Listener                         节点B/C的Listener
 *     onMessage() 收到消息                    onMessage() 收到消息
 *       │                                        │
 *       ▼                                        ▼
 *     cacheClient.deleteShopLocalCache(123)  cacheClient.deleteShopLocalCache(123)
 *       │                                        │
 *       ▼                                        ▼
 *     节点A的Caffeine中id=123被删除          节点B/C的Caffeine中id=123被删除
 *
 * 【最终一致性】
 *   - 优点：所有节点最终都会删除本地缓存，保证数据最终一致
 *   - 缺点：有短暂延迟（毫秒级），在消息传播过程中可能有短暂的不一致
 *   - 兜底：本地缓存有TTL（5分钟/10分钟），即使收不到广播也会自动过期
 *
 * 【注册方式】
 *   此监听器在 CacheInvalidationConfig 中注册到Redis Pub/Sub容器
 *   订阅频道：cache:invalidate
 */
@Slf4j
@Component
public class CacheInvalidationListener implements MessageListener {

    @Resource
    private CacheClient cacheClient;

    @Resource
    private ObjectMapper objectMapper;

    /**
     * 收到Redis Pub/Sub消息时的回调方法
     *
     * @param message Redis消息体（包含缓存失效信息）
     * @param pattern 订阅的频道模式（本项目中未使用）
     *
     * 处理流程：
     *   1. 从消息体中提取JSON字符串
     *   2. 反序列化为 CacheInvalidationMessage 对象
     *   3. 根据 cacheName 判断是哪种缓存
     *   4. 调用对应的删除方法删除本地缓存
     */
    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            // 1. 提取消息体（JSON格式）
            //    例如：{"cacheName":"shop","key":"123"}
            String body = new String(message.getBody(), StandardCharsets.UTF_8);
            log.info("Received cache invalidation message, payload={}", body);

            // 2. 反序列化为消息对象
            CacheInvalidationMessage cacheMessage = objectMapper.readValue(body, CacheInvalidationMessage.class);

            // 3. 根据缓存名称，删除对应的本地缓存
            if (CACHE_NAME_SHOP.equals(cacheMessage.getCacheName())) {
                // 商户缓存失效 → 删除Caffeine中对应商户ID的条目
                // 调用 cacheClient.deleteShopLocalCache(id)
                // 底层是 Caffeine Cache 的 invalidate(key) 方法
                cacheClient.deleteShopLocalCache(Long.valueOf(cacheMessage.getKey()));
                log.info("Invalidated local shop cache, key={}", cacheMessage.getKey());
                return;
            }
            if (CACHE_NAME_SHOP_TYPE.equals(cacheMessage.getCacheName())) {
                // 商户类型缓存失效 → 删除Caffeine中商户类型列表的条目
                // 调用 cacheClient.deleteShopTypeLocalCache(key)
                cacheClient.deleteShopTypeLocalCache(cacheMessage.getKey());
                log.info("Invalidated local shop type cache, key={}", cacheMessage.getKey());
                return;
            }

            // 未知缓存类型，忽略
            log.warn("Ignore unknown cache invalidation message: {}", body);
        } catch (Exception e) {
            // 反序列化失败或删除缓存异常，记录日志但不影响正常运行
            // 本地缓存有TTL兜底，即使删除失败也会在过期后自动失效
            log.error("Failed to handle cache invalidation message", e);
        }
    }
}
