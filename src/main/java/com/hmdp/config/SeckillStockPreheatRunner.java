package com.hmdp.config;

import com.hmdp.entity.SeckillVoucher;
import com.hmdp.service.ISeckillVoucherService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.List;

import static com.hmdp.utils.RedisConstants.SECKILL_STOCK_KEY;

/**
 * 秒杀库存预热器 —— 应用启动时将数据库中的秒杀库存加载到Redis
 *
 * 【在秒杀链路中的位置】
 *
 *   应用启动
 *     │
 *     ▼
 *   ★ 本类：SeckillStockPreheatRunner ★  ← 从DB读取库存 → 写入Redis
 *     │
 *     ▼
 *   Redis中已有库存数据（seckill:stock:{voucherId}）
 *     │
 *     ▼
 *   用户秒杀请求 → seckill.lua 脚本校验Redis库存
 *
 * 【为什么要预热】
 *   seckill.lua 脚本需要从 Redis 中读取库存（GET seckill:stock:{voucherId}），
 *   如果Redis中没有这个key，脚本会返回"库存不足"（stock为nil），
 *   导致所有秒杀请求都失败。
 *   所以必须在应用启动时，将数据库中的库存数据提前写入Redis。
 *
 * 【预热逻辑】
 *   1. 查询DB中所有秒杀优惠券（tb_seckill_voucher表）
 *   2. 遍历每个优惠券，将 stock 写入 Redis
 *      SET seckill:stock:{voucherId} stock
 *   3. 记录预热数量
 *
 * 【注意事项】
 *   - 实现了 ApplicationRunner，在Spring Boot应用启动完成后自动执行
 *   - 每次启动都会覆盖Redis中的库存值
 *   - 如果应用运行期间新增了秒杀券，需要手动预热或重启应用
 *   - 预热的是初始库存，运行中Redis库存会被Lua脚本预扣
 */
@Component
public class SeckillStockPreheatRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SeckillStockPreheatRunner.class);

    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 应用启动后自动执行
     *
     * 遍历所有秒杀优惠券，将库存写入Redis
     * key格式：seckill:stock:{voucherId}
     * value：库存数量（字符串）
     */
    @Override
    public void run(ApplicationArguments args) {
        // 1. 从数据库查询所有秒杀优惠券
        List<SeckillVoucher> vouchers = seckillVoucherService.list();
        if (vouchers == null || vouchers.isEmpty()) {
            log.info("No seckill vouchers found. Skip stock preheat.");
            return;
        }

        // 2. 遍历每个优惠券，将库存预热到Redis
        int loaded = 0;
        for (SeckillVoucher voucher : vouchers) {
            if (voucher.getVoucherId() == null || voucher.getStock() == null) {
                continue;
            }
            // SET seckill:stock:{voucherId} stock
            stringRedisTemplate.opsForValue().set(
                    SECKILL_STOCK_KEY + voucher.getVoucherId(),
                    voucher.getStock().toString()
            );
            loaded++;
            log.info("Preheated seckill stock, voucherId={}, stock={}", voucher.getVoucherId(), voucher.getStock());
        }
        log.info("Seckill stock preheat finished, loaded {} records to Redis.", loaded);
    }
}
