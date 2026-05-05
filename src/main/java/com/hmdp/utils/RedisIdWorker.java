package com.hmdp.utils;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * <p>
 * Redis ID生成器
 * 基于时间戳和序列号的组合生成唯一ID
 * </p>
 */
@Component  // 标记为组件，由Spring容器管理
public class RedisIdWorker {
    /**
     * 开始时间戳
     * 2022-01-01 00:00:00 UTC
     */
    private static final long BEGIN_TIMESTAMP = 1640995200L;
    /**
     * 序列号的位数
     * 用于控制ID的精度和范围
     */
    private static final int COUNT_BITS = 32;

    // Redis操作模板，用于执行自增操作
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 构造方法，注入StringRedisTemplate
     * @param stringRedisTemplate Redis操作模板
     */
    public RedisIdWorker(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     * 生成唯一ID
     * @param keyPrefix 业务前缀，用于区分不同业务的ID
     * @return 唯一ID
     */
    public long nextId(String keyPrefix) {
        // 1.生成时间戳
        LocalDateTime now = LocalDateTime.now();  // 获取当前时间
        long nowSecond = now.toEpochSecond(ZoneOffset.UTC);  // 转换为UTC时间戳（秒）
        long timestamp = nowSecond - BEGIN_TIMESTAMP;  // 计算与起始时间的差值

        // 2.生成序列号
        // 2.1.获取当前日期，精确到天
        String date = now.format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));
        // 2.2.使用Redis自增生成序列号，每天重置
        long count = stringRedisTemplate.opsForValue().increment("icr:" + keyPrefix + ":" + date);

        // 3.拼接并返回
        // 将时间戳左移32位，与序列号进行或操作，组合成64位ID
        return timestamp << COUNT_BITS | count;
    }
}
