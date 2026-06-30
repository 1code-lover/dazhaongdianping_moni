package com.hmdp.controller;

import com.hmdp.annotation.RateLimit;
import com.hmdp.dto.Result;
import com.hmdp.enums.LimitType;
import com.hmdp.service.IVoucherOrderService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

/**
 * 优惠券订单控制器
 * 处理优惠券秒杀相关的 HTTP 请求
 * 接口路径：/voucher-order
 * 核心链路：Redis Lua 校验 → Redis Stream 队列 → Kafka 消费 → MySQL 落库
 *
 * @author ethan
 * @date 2026-06-14
 */
@RestController
@RequestMapping("/voucher-order")
public class VoucherOrderController {

    @Resource
    private IVoucherOrderService voucherOrderService;

    /**
     * 秒杀优惠券接口
     * 秒杀接口先做用户维度限流，再进入后面的 Lua 资格校验链路
     * 限流规则：每个用户1秒内最多3次请求
     *
     * 核心流程：
     * 1. 用户维度限流校验
     * 2. Redis Lua 脚本检查库存和一人一单
     * 3. 预扣库存，写入 Redis Stream
     * 4. Relay 中转到 Kafka
     * 5. Kafka 消费者异步落库到 MySQL
     * 6. 使用唯一索引和库存校验防止超卖
     *
     * @param voucherId 优惠券ID
     * @return 秒杀结果（成功返回订单ID）
     */
    @PostMapping("seckill/{id}")
    @RateLimit(
            keyPrefix = "voucher-order:seckill:user",
            limitType = LimitType.USER_AND_PATH,
            timeWindowSeconds = 1,
            maxRequests = 3,
            pathKey = true,
            message = "秒杀请求过于频繁，请稍后再试"
    )
    public Result seckillVoucher(@PathVariable("id") Long voucherId) {
        return voucherOrderService.seckillVoucher(voucherId);
    }
}
