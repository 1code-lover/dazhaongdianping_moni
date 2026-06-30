package com.hmdp.controller;


import com.hmdp.dto.Result;
import com.hmdp.entity.Voucher;
import com.hmdp.service.IVoucherService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

/**
 * 优惠券控制器
 * 处理优惠券相关的 HTTP 请求，包括新增优惠券、查询优惠券列表等功能
 * 接口路径：/voucher
 * 支持秒杀券和普通券两种类型
 *
 * @author ethan
 * @date 2026-06-14
 */
@RestController
@RequestMapping("/voucher")
public class VoucherController {

    @Resource
    private IVoucherService voucherService;

    /**
     * 新增秒杀券
     * 创建秒杀类型的优惠券，包含库存、有效期、秒杀时间等信息
     * 秒杀券会进入 Redis 进行库存预热和限流处理
     *
     * @param voucher 优惠券对象（包含秒杀信息）
     * @return 创建的优惠券ID
     */
    @PostMapping("seckill")
    public Result addSeckillVoucher(@RequestBody Voucher voucher) {
        voucherService.addSeckillVoucher(voucher);
        return Result.ok(voucher.getId());
    }

    /**
     * 新增普通优惠券
     * 创建普通类型的优惠券，不进行秒杀处理
     *
     * @param voucher 优惠券对象
     * @return 创建的优惠券ID
     */
    @PostMapping
    public Result addVoucher(@RequestBody Voucher voucher) {
        voucherService.save(voucher);
        return Result.ok(voucher.getId());
    }

    /**
     * 查询商铺的优惠券列表
     * 分页查询指定商铺的所有优惠券（秒杀券和普通券）
     *
     * @param shopId 商铺ID
     * @return 优惠券列表
     */
    @GetMapping("/list/{shopId}")
    public Result queryVoucherOfShop(@PathVariable("shopId") Long shopId) {
        return voucherService.queryVoucherOfShop(shopId);
    }
}
