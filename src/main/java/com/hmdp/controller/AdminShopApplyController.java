package com.hmdp.controller;

import com.hmdp.dto.AuditApplyDTO;
import com.hmdp.dto.Result;
import com.hmdp.service.IShopApplyService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

/**
 * 商家入驻控制器（平台端）
 */
@RestController
@RequestMapping("/admin/shop/apply")
public class AdminShopApplyController {

    @Resource
    private IShopApplyService shopApplyService;

    /**
     * 获取申请列表
     */
    @GetMapping("/list")
    public Result getApplyList(
            @RequestParam(value = "status", required = false) Integer status,
            @RequestParam(value = "current", defaultValue = "1") Integer current,
            @RequestParam(value = "size", defaultValue = "10") Integer size
    ) {
        return shopApplyService.getApplyList(status, current, size);
    }

    /**
     * 查看申请详情
     */
    @GetMapping("/{id}")
    public Result getApplyDetail(@PathVariable Long id) {
        return shopApplyService.getApplyDetail(id);
    }

    /**
     * 审核申请
     */
    @PostMapping("/audit")
    public Result auditApply(@RequestBody AuditApplyDTO dto) {
        return shopApplyService.auditApply(dto.getApplyId(), dto.getStatus(), dto.getRejectReason());
    }
}
