package com.hmdp.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.Collections;

/**
 * 回滚 Lua 预扣的 Redis 库存与一人一单标记（幂等）。
 */
@Service
public class SeckillRedisRollbackService {

    private static final Logger log = LoggerFactory.getLogger(SeckillRedisRollbackService.class);

    private static final DefaultRedisScript<Long> ROLLBACK_SCRIPT;

    static {
        ROLLBACK_SCRIPT = new DefaultRedisScript<>();
        ROLLBACK_SCRIPT.setLocation(new ClassPathResource("seckill_rollback.lua"));
        ROLLBACK_SCRIPT.setResultType(Long.class);
    }

    private final StringRedisTemplate stringRedisTemplate;

    public SeckillRedisRollbackService(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     * @return true 表示执行了回滚或已是幂等空操作（可认为 Redis 侧已对齐）
     */
    public boolean rollback(Long voucherId, Long userId, String reason) {
        if (voucherId == null || userId == null) {
            log.warn("Seckill redis rollback skipped, missing ids, voucherId={}, userId={}, reason={}",
                    voucherId, userId, reason);
            return false;
        }
        try {
            Long r = stringRedisTemplate.execute(
                    ROLLBACK_SCRIPT,
                    Collections.emptyList(),
                    voucherId.toString(),
                    userId.toString()
            );
            log.info("Seckill redis rollback done, voucherId={}, userId={}, scriptResult={}, reason={}",
                    voucherId, userId, r, reason);
            return true;
        } catch (Exception e) {
            log.error("Seckill redis rollback failed, voucherId={}, userId={}, reason={}",
                    voucherId, userId, reason, e);
            return false;
        }
    }
}
