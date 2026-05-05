package com.hmdp.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

import static com.hmdp.utils.RedisConstants.CACHE_INVALIDATE_CHANNEL;

/**
 * Redis Pub/Sub 监听容器配置 —— 注册缓存失效消息监听器
 *
 * 【作用】
 *   将 CacheInvalidationListener 注册到Redis的消息监听容器中，
 *   使其能够接收 "cache:invalidate" 频道的消息。
 *
 * 【工作原理】
 *
 *   Spring Data Redis 提供了 RedisMessageListenerContainer，
 *   它内部维护了一个到Redis的连接，专门用于接收Pub/Sub消息。
 *
 *   当Redis收到 PUBLISH cache:invalidate "..." 命令时，
 *   所有订阅了该频道的 container 都会收到消息，
 *   然后分发给注册的 MessageListener（即 CacheInvalidationListener）。
 *
 * 【注册流程】
 *
 *   Spring启动时：
 *     1. 创建 RedisMessageListenerContainer Bean
 *     2. 注入 RedisConnectionFactory（连接Redis）
 *     3. 添加 CacheInvalidationListener 监听器
 *     4. 指定订阅频道：cache:invalidate
 *
 *   之后，每当有消息发布到 cache:invalidate 频道，
 *   CacheInvalidationListener.onMessage() 就会被调用。
 *
 * 【多节点场景】
 *
 *   每个JVM实例（节点）启动时，都会执行这个配置类，
 *   都会创建自己的 RedisMessageListenerContainer，
 *   都会订阅 cache:invalidate 频道。
 *
 *   所以当某个节点发布消息时，所有节点都能收到。
 *
 *   节点A ─── RedisMessageListenerContainer ─── 订阅 cache:invalidate
 *   节点B ─── RedisMessageListenerContainer ─── 订阅 cache:invalidate
 *   节点C ─── RedisMessageListenerContainer ─── 订阅 cache:invalidate
 *
 *   发布消息时：
 *   节点A → PUBLISH cache:invalidate "..." → Redis → 广播给节点A/B/C
 */
@Configuration
public class CacheInvalidationConfig {

    /**
     * 创建Redis消息监听容器
     *
     * @param redisConnectionFactory     Redis连接工厂
     * @param cacheInvalidationListener  缓存失效消息监听器
     * @return 配置好的RedisMessageListenerContainer
     */
    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(
            RedisConnectionFactory redisConnectionFactory,
            CacheInvalidationListener cacheInvalidationListener) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        // 设置Redis连接工厂（用于建立到Redis的连接）
        container.setConnectionFactory(redisConnectionFactory);
        // 注册监听器，指定订阅的频道：cache:invalidate
        container.addMessageListener(cacheInvalidationListener, new ChannelTopic(CACHE_INVALIDATE_CHANNEL));
        return container;
    }
}
