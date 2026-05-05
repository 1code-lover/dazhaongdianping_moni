package com.hmdp.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.redisson.config.SingleServerConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RedissonConfig {

    @Value("${spring.redis.host:127.0.0.1}")
    private String host;

    @Value("${spring.redis.port:6379}")
    private int port;

    @Value("${spring.redis.password:}")
    private String password;

    @Bean
    public RedissonClient redissonClient() {
        Config config = new Config();
        SingleServerConfig serverConfig = config.useSingleServer()
                .setAddress("redis://" + host + ":" + port)
                .setConnectionPoolSize(8)
                .setConnectionMinimumIdleSize(1)
                .setSubscriptionConnectionPoolSize(2)
                .setSubscriptionConnectionMinimumIdleSize(1)
                .setConnectTimeout(10000)
                .setTimeout(3000)
                .setRetryAttempts(2)
                .setRetryInterval(1500)
                .setPingConnectionInterval(0)
                .setKeepAlive(true)
                .setTcpNoDelay(true);
        if (password != null && !password.trim().isEmpty()) {
            serverConfig.setPassword(password);
        }
        return Redisson.create(config);
    }
}
