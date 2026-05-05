package com.hmdp.config;

import com.hmdp.entity.SeckillVoucher;
import com.hmdp.service.ISeckillVoucherService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.List;

import static com.hmdp.utils.RedisConstants.SECKILL_STOCK_KEY;

/**
 * 秒杀库存对账调度器 —— 定期比对 Redis 预扣库存 与 数据库库存
 *
 * 【为什么需要对账】
 *
 *   正常情况下：
 *     Lua预扣库存 → Kafka消费 → DB扣减库存
 *     三者应该一致。
 *
 *   但可能出现不一致的情况：
 *     1. 应用重启，导致预扣丢失（Redis数据未持久化）
 *     2. Redis故障，导致预扣数据丢失
 *     3. 网络分区，导致部分预扣未同步
 *     4. 代码bug，导致扣减逻辑错误
 *
 * 【对账逻辑】
 *
 *   每5分钟（可配置）执行一次：
 *     遍历所有秒杀券
 *       - 读取 Redis 库存：GET seckill:stock:{voucherId}
 *       - 读取 DB 库存：SELECT stock FROM tb_seckill_voucher
 *       - 比较两者是否一致
 *       - 不一致则打 WARN 日志
 *
 * 【发现不一致怎么办】
 *
 *   对账只是监控，发现问题需要人工介入：
 *     - 如果 Redis < DB：说明有预扣未落库，需要补录订单或回滚
 *     - 如果 Redis > DB：说明有预扣已落库但库存未扣，可能是数据错误
 *
 * 【配置】
 *
 *   - app.seckill.reconcile.enabled：是否开启对账
 *   - app.seckill.reconcile.interval-ms：对账间隔，默认5分钟
 */
@Component
@ConditionalOnProperty(prefix = "app.seckill.reconcile", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SeckillStockReconcileScheduler {

    private static final Logger log = LoggerFactory.getLogger(SeckillStockReconcileScheduler.class);

    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 对账任务
     *
     * 每隔5分钟（默认）执行一次，比对 Redis 和 DB 的库存
     */
    @Scheduled(fixedDelayString = "${app.seckill.reconcile.interval-ms:300000}")
    public void reconcile() {
        // 1. 查询所有秒杀券
        List<SeckillVoucher> vouchers = seckillVoucherService.list();
        if (vouchers == null || vouchers.isEmpty()) {
            return;
        }

        // 2. 逐个比对
        for (SeckillVoucher v : vouchers) {
            if (v.getVoucherId() == null || v.getStock() == null) {
                continue;
            }

            // GET seckill:stock:{voucherId}
            String key = SECKILL_STOCK_KEY + v.getVoucherId();
            String redisVal = stringRedisTemplate.opsForValue().get(key);

            // 3. Redis key 不存在的情况
            if (redisVal == null) {
                if (v.getStock() > 0) {
                    // DB有库存但Redis无key，说明预扣丢失了
                    log.warn("Seckill stock reconcile: Redis key missing, key={} dbStock={} voucherId={}",
                            key, v.getStock(), v.getVoucherId());
                }
                continue;
            }

            // 4. 解析 Redis 值
            int redisStock;
            try {
                redisStock = Integer.parseInt(redisVal.trim());
            } catch (NumberFormatException e) {
                log.warn("Seckill stock reconcile: invalid Redis value, key={} value={} voucherId={}",
                        key, redisVal, v.getVoucherId());
                continue;
            }

            // 5. 比较
            int dbStock = v.getStock();
            if (redisStock != dbStock) {
                log.warn("Seckill stock reconcile: mismatch redisStock={} dbStock={} voucherId={}",
                        redisStock, dbStock, v.getVoucherId());
            }
        }
    }
}