package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.mq.SeckillOrderException;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.Collections;

/**
 * 秒杀订单服务实现 —— 秒杀链路的核心入口
 *
 * 【完整秒杀链路】
 *
 *   ① 用户点击秒杀
 *     │
 *     ▼
 *   ② VoucherOrderController.createVoucherOrder()  ← Controller入口
 *     │
 *     ▼
 *   ③ VoucherOrderServiceImpl.seckillVoucher()      ← ★本类方法★
 *     │
 *     │  3.1 生成全局唯一订单ID（RedisIdWorker）
 *     │  3.2 执行seckill.lua脚本（原子操作）
 *     │      - 校验库存 → 预扣库存 → 一人一单校验 → 发Stream消息
 *     │
 *     ├─ 返回1 → 库存不足，直接返回失败
 *     ├─ 返回2 → 重复下单，直接返回失败
 *     └─ 返回0 → 成功，返回订单ID
 *              │
 *              ▼
 *   ④ RedisStreamToKafkaRelay                        ← 中转层
 *     │  从Redis Stream消费订单消息，转发到Kafka
 *     │
 *     ▼
 *   ⑤ Kafka → VoucherOrderConsumer                  ← Kafka消费者
 *     │
 *     ▼
 *   ⑥ handleVoucherOrderFromMQ() → createVoucherOrder()  ← ★本类方法★
 *     │
 *     │  6.1 获取分布式锁（Redisson）防止并发重复下单
 *     │  6.2 二次校验一人一单（DB层面兜底）
 *     │  6.3 扣减数据库库存（乐观锁：stock > 0）
 *     │  6.4 创建订单（插入tb_voucher_order表）
 *     │
 *     ▼
 *   ⑦ 订单创建完成
 *
 * 【为什么要二次校验一人一单】
 *   Lua脚本中的SISMEMBER校验是在Redis层面的快速拦截，
 *   但Redis预扣和DB落库之间有时间差，极端情况下可能绕过Redis校验。
 *   所以在DB落库时再通过唯一索引 + 分布式锁双重保障。
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    private static final Logger log = LoggerFactory.getLogger(VoucherOrderServiceImpl.class);

    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Resource
    private RedisIdWorker redisIdWorker;
    @Resource
    private RedissonClient redissonClient;
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 加载seckill.lua脚本
     * DefaultRedisScript会在首次执行时将Lua脚本发送给Redis，之后复用SHA1缓存
     */
    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;

    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }

    /**
     * 创建秒杀订单（从Kafka消费端调用）
     *
     * 这个方法在Kafka消费者线程中执行，不在用户请求线程中，
     * 所以这里是异步落库，不会阻塞用户请求。
     *
     * 执行步骤：
     *   1. 获取分布式锁（Redisson），防止同一用户并发创建订单
     *   2. 二次校验一人一单（DB层面 count 查询）
     *   3. 扣减数据库库存（乐观锁 WHERE stock > 0）
     *   4. 插入订单记录（利用唯一索引兜底防重）
     *
     * @param voucherOrder 订单对象（包含orderId, userId, voucherId）
     */
    private void createVoucherOrder(VoucherOrder voucherOrder) {
        Long userId = voucherOrder.getUserId();
        Long voucherId = voucherOrder.getVoucherId();

        // ========== 第1步：获取分布式锁 ==========
        // 锁key: "lock:order:" + userId
        // 作用：防止同一用户并发创建订单（Redis的SISMEMBER已经拦截了大部分，
        //       这里是DB层面的兜底保护）
        RLock redisLock = redissonClient.getLock("lock:order:" + userId);
        boolean isLock = redisLock.tryLock();
        if (!isLock) {
            log.error("Create voucher order blocked by lock, userId={}, voucherId={}", userId, voucherId);
            // 获取锁失败 → 抛出可恢复异常，Kafka会重试
            throw new SeckillOrderException(true, "LOCK_FAILED");
        }

        try {
            // ========== 第2步：二次校验一人一单（DB层面） ==========
            // 查询该用户是否已经下过该优惠券的订单
            // 虽然Redis层面已经用SISMEMBER校验过，但这里做最终兜底
            int count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
            if (count > 0) {
                // 已有订单 → 不抛异常，静默跳过（幂等处理）
                log.warn("Create voucher order skipped, duplicate order detected, userId={}, voucherId={}", userId, voucherId);
                return;
            }

            // ========== 第3步：扣减数据库库存（乐观锁） ==========
            // UPDATE tb_seckill_voucher SET stock = stock - 1 WHERE voucher_id = ? AND stock > 0
            // stock > 0 是乐观锁条件，防止超卖
            boolean success = seckillVoucherService.update()
                    .setSql("stock = stock - 1")
                    .eq("voucher_id", voucherId).gt("stock", 0)
                    .update();
            if (!success) {
                // 库存不足 → 抛出不可恢复异常，需要回滚Redis预扣
                log.error("Create voucher order failed, no stock, userId={}, voucherId={}", userId, voucherId);
                throw new SeckillOrderException(false, "NO_DB_STOCK");
            }

            // ========== 第4步：插入订单记录 ==========
            // INSERT INTO tb_voucher_order (id, user_id, voucher_id) VALUES (?, ?, ?)
            // 表上有唯一索引(user_id, voucher_id)兜底防重
            try {
                save(voucherOrder);
                log.info("Create voucher order success, orderId={}, userId={}, voucherId={}",
                        voucherOrder.getId(), userId, voucherId);
            } catch (DuplicateKeyException e) {
                // 唯一索引冲突 → 幂等处理，忽略
                log.warn("Create voucher order duplicate key ignored, orderId={}, userId={}, voucherId={}",
                        voucherOrder.getId(), userId, voucherId);
            }
        } finally {
            // ========== 释放分布式锁 ==========
            redisLock.unlock();
        }
    }

    /**
     * 秒杀下单入口 —— 用户请求的入口方法
     *
     * 执行流程：
     *   1. 获取当前用户ID
     *   2. 生成全局唯一订单ID（基于Redis自增）
     *   3. 执行seckill.lua脚本（原子操作）
     *      - 校验库存、一人一单、预扣库存、发Stream消息
     *   4. 根据返回值判断结果
     *
     * @param voucherId 优惠券ID
     * @return 订单ID（成功）或 错误信息（失败）
     */
    @Override
    public Result seckillVoucher(Long voucherId) {
        // 1.获取当前登录用户ID
        Long userId = UserHolder.getUser().getId();

        // 2.生成全局唯一订单ID
        // RedisIdWorker：日期 + Redis自增序列号，保证全局唯一且有序
        long orderId = redisIdWorker.nextId("order");

        // 3.执行Lua脚本（原子操作）
        // 参数：KEYS=[]  ARGV=[voucherId, userId, orderId]
        // 返回值：0=成功  1=库存不足  2=重复下单
        Long result = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(), userId.toString(), String.valueOf(orderId)
        );
        int r = result.intValue();

        // 4.判断结果
        if (r != 0) {
            // 失败：1=库存不足  2=重复下单
            return Result.fail(r == 1 ? "库存不足" : "不能重复下单");
        }

        // 成功：返回订单ID
        // 注意：此时订单还没有落库，只是Redis预扣成功了
        // 订单消息已通过Lua脚本的XADD发送到Redis Stream
        // 后续由RedisStreamToKafkaRelay转发到Kafka，再由Consumer异步落库
        return Result.ok(orderId);
    }

    /**
     * 处理从Kafka消费到的订单消息（由VoucherOrderConsumer调用）
     *
     * @param voucherOrder 订单对象
     */
    @Override
    @Transactional
    public void handleVoucherOrderFromMQ(VoucherOrder voucherOrder) {
        createVoucherOrder(voucherOrder);
    }
}
