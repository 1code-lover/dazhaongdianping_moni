package com.hmdp.controller;

import com.hmdp.dto.Result;
import com.hmdp.entity.ShopApply;
import com.hmdp.service.IShopApplyService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

/**
 * 商家入驻控制器（商家端）
 */
@RestController
@RequestMapping("/shop/apply")
public class ShopApplyController {

    @Resource
    private IShopApplyService shopApplyService;

    /**
     * 提交入驻申请
     */
    @PostMapping
    public Result submitApply(@RequestBody ShopApply apply) {
        return shopApplyService.submitApply(apply);
    }

    /**
     * 查看申请状态
     */
    @GetMapping("/status")
    public Result getApplyStatus() {
        return shopApplyService.getApplyStatus();
    }

    /**
     * 重新申请
     */
    @PostMapping("/{id}/reapply")
    public Result reapply(@PathVariable Long id, @RequestBody ShopApply apply) {
        return shopApplyService.reapply(id, apply);
    }
}
