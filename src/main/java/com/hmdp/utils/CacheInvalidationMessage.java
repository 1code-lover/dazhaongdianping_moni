package com.hmdp.utils;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 缓存失效消息体 —— 通过Redis Pub/Sub在多节点间传递缓存失效信息
 *
 * 【作用】
 *   当某个节点更新了数据，需要通知所有节点（包括自己）删除对应的本地缓存。
 *   这个类就是广播消息的载体，告诉其他节点"哪种缓存、哪个key失效了"。
 *
 * 【消息流转过程】
 *
 *   发布端（CacheInvalidationPublisher）：
 *     publishShopInvalidation(123)
 *       → new CacheInvalidationMessage("shop", "123")
 *       → 序列化为JSON: {"cacheName":"shop", "key":"123"}
 *       → Redis PUBLISH cache:invalidate '{"cacheName":"shop", "key":"123"}'
 *
 *   接收端（CacheInvalidationListener）：
 *     收到消息 → 反序列化为 CacheInvalidationMessage 对象
 *       → 根据 cacheName 判断缓存类型
 *       → 根据 key 定位具体缓存条目
 *       → 调用 cacheClient.deleteXxxLocalCache(key) 删除本地缓存
 *
 * 【字段说明】
 *
 *   cacheName：缓存类型名称，标识这是哪一类缓存
 *     - "shop"     → 商户缓存，对应 CacheClient.shopLocalCache
 *     - "shop-type" → 商户类型缓存，对应 CacheClient.shopTypeLocalCache
 *     定义在 RedisConstants 中：CACHE_NAME_SHOP / CACHE_NAME_SHOP_TYPE
 *
 *   key：缓存条目的具体标识
 *     - 商户缓存：商户ID，如 "123"
 *     - 商户类型缓存：固定字符串，如 "shop-type:list"
 *
 * 【JSON示例】
 *
 *   商户缓存失效：  {"cacheName":"shop", "key":"123"}
 *   商户类型缓存失效：{"cacheName":"shop-type", "key":"shop-type:list"}
 *
 * 【为什么用JSON序列化】
 *   Redis Pub/Sub 传输的是字符串，所以消息需要序列化。
 *   项目使用 Jackson ObjectMapper 进行 JSON 序列化/反序列化：
 *   - 发布时：objectMapper.writeValueAsString(message)
 *   - 接收时：objectMapper.readValue(body, CacheInvalidationMessage.class)
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CacheInvalidationMessage {

    /**
     * 缓存类型名称
     * 取值：CACHE_NAME_SHOP("shop") 或 CACHE_NAME_SHOP_TYPE("shop-type")
     * 用于接收端判断应该删除哪个本地缓存
     */
    private String cacheName;

    /**
     * 缓存条目标识
     * 商户缓存：商户ID（如 "123"）
     * 商户类型缓存：本地缓存的key（如 "shop-type:list"）
     * 用于接收端定位具体要删除的缓存条目
     */
    private String key;
}
