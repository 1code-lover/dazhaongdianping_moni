package com.hmdp.controller;


import com.hmdp.dto.Result;
import com.hmdp.entity.Voucher;
import com.hmdp.service.IVoucherService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

/**
 * <p>
 * 优惠券控制器
 * 处理优惠券相关的HTTP请求，如新增优惠券、查询优惠券列表等
 * </p>
 *
 * @author 虎哥
 */
@RestController  // 标记为REST风格的控制器，返回JSON数据
@RequestMapping("/voucher")  // 基础路径，所有请求都以/voucher开头
public class VoucherController {

    @Resource  // 自动注入优惠券服务
    private IVoucherService voucherService;

    /**
     * 新增秒杀券
     * @param voucher 优惠券信息，包含秒杀信息
     * @return 优惠券id
     */
    @PostMapping("seckill")  // POST请求，路径为/voucher/seckill
    public Result addSeckillVoucher(@RequestBody Voucher voucher) {
        // 调用服务添加秒杀券
        voucherService.addSeckillVoucher(voucher);
        // 返回优惠券id
        return Result.ok(voucher.getId());
    }

    /**
     * 新增普通券
     * @param voucher 优惠券信息
     * @return 优惠券id
     */
    @PostMapping  // POST请求，路径为/voucher
    public Result addVoucher(@RequestBody Voucher voucher) {
        // 调用服务保存普通券
        voucherService.save(voucher);
        // 返回优惠券id
        return Result.ok(voucher.getId());
    }

    /**
     * 查询店铺的优惠券列表
     * @param shopId 店铺id
     * @return 优惠券列表
     */
    @GetMapping("/list/{shopId}")  // GET请求，路径为/voucher/list/{shopId}
    public Result queryVoucherOfShop(@PathVariable("shopId") Long shopId) {
        // 调用服务查询店铺的优惠券列表
        return voucherService.queryVoucherOfShop(shopId);
    }
}
