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

@RestController
@RequestMapping("/voucher-order")
public class VoucherOrderController {

    @Resource
    private IVoucherOrderService voucherOrderService;

    /**
     * 秒杀接口先做用户维度限流，再进入后面的 Lua 资格校验链路。
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
