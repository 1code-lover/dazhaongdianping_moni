package com.hmdp.controller;

import com.hmdp.dto.Result;
import com.hmdp.service.IVerifyService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

/**
 * 核销控制器
 * 提供核销相关接口
 */
@RestController
@RequestMapping("/verify")
public class VerifyController {

    @Resource
    private IVerifyService verifyService;

    /**
     * 核销订单
     * @param verifyCode 核销码
     * @return 核销结果
     */
    @PostMapping
    public Result verify(@RequestParam("verifyCode") String verifyCode) {
        return verifyService.verify(verifyCode);
    }

    /**
     * 验证核销码有效性
     * @param verifyCode 核销码
     * @return 是否有效
     */
    @GetMapping("/check/{verifyCode}")
    public Result checkVerifyCode(@PathVariable String verifyCode) {
        return verifyService.checkVerifyCode(verifyCode);
    }

    /**
     * 查询核销记录
     * @param shopId 商户ID
     * @param current 页码
     * @param size 每页大小
     * @return 核销记录列表
     */
    @GetMapping("/record")
    public Result queryVerifyRecords(
            @RequestParam("shopId") Long shopId,
            @RequestParam(value = "current", defaultValue = "1") Integer current,
            @RequestParam(value = "size", defaultValue = "10") Integer size
    ) {
        return verifyService.queryVerifyRecords(shopId, current, size);
    }
}
