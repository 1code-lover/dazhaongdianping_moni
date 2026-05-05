package com.hmdp.integration;

import com.hmdp.service.SeckillRedisRollbackService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 需本地 Redis 与主工程配置一致（见 application.yaml），且会启动完整 Spring 上下文（含 MySQL/Kafka 等）。
 * 运行：{@code mvn test -Dtest=SeckillRedisRollbackIntegrationTest}
 */
@SpringBootTest
@Tag("integration")
class SeckillRedisRollbackIntegrationTest {

    private static final long VOUCHER_ID = 999997L;
    private static final long USER_ID = 777776L;

    @Autowired
    private SeckillRedisRollbackService seckillRedisRollbackService;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @AfterEach
    void cleanup() {
        stringRedisTemplate.delete(stockKey());
        stringRedisTemplate.delete(orderKey());
    }

    @Test
    void rollbackRestoresStockAndOrderSet() {
        String sk = stockKey();
        String ok = orderKey();
        stringRedisTemplate.opsForValue().set(sk, "4");
        stringRedisTemplate.opsForSet().add(ok, String.valueOf(USER_ID));

        assertTrue(seckillRedisRollbackService.rollback(VOUCHER_ID, USER_ID, "integration-test"));
        assertEquals("5", stringRedisTemplate.opsForValue().get(sk));
        assertTrue(Boolean.FALSE.equals(stringRedisTemplate.opsForSet().isMember(ok, String.valueOf(USER_ID))));
    }

    @Test
    void secondRollbackIsIdempotentOnRedis() {
        String sk = stockKey();
        String ok = orderKey();
        stringRedisTemplate.opsForValue().set(sk, "2");
        stringRedisTemplate.opsForSet().add(ok, String.valueOf(USER_ID));

        assertTrue(seckillRedisRollbackService.rollback(VOUCHER_ID, USER_ID, "first"));
        assertEquals("3", stringRedisTemplate.opsForValue().get(sk));
        assertTrue(seckillRedisRollbackService.rollback(VOUCHER_ID, USER_ID, "second"));
        assertEquals("3", stringRedisTemplate.opsForValue().get(sk));
    }

    private String stockKey() {
        return "seckill:stock:" + VOUCHER_ID;
    }

    private String orderKey() {
        return "seckill:order:" + VOUCHER_ID;
    }
}
