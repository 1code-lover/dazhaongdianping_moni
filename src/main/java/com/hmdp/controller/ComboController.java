package com.hmdp.controller;

import com.hmdp.dto.Result;
import com.hmdp.entity.Combo;
import com.hmdp.service.IComboService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

/**
 * 套餐控制器
 * 提供套餐的CRUD接口
 */
@RestController
@RequestMapping("/combo")
public class ComboController {

    @Resource
    private IComboService comboService;

    /**
     * 发布套餐（商家端）
     * @param combo 套餐信息
     * @return 套餐ID
     */
    @PostMapping
    public Result addCombo(@RequestBody Combo combo) {
        return comboService.addCombo(combo);
    }

    /**
     * 更新套餐（商家端）
     * @param combo 套餐信息
     * @return 操作结果
     */
    @PutMapping
    public Result updateCombo(@RequestBody Combo combo) {
        return comboService.updateCombo(combo);
    }

    /**
     * 上下架套餐（商家端）
     * @param id 套餐ID
     * @param status 状态：0下架 1上架
     * @return 操作结果
     */
    @PutMapping("/{id}/status/{status}")
    public Result updateStatus(@PathVariable Long id, @PathVariable Integer status) {
        return comboService.updateStatus(id, status);
    }

    /**
     * 查询商户套餐列表（用户端）
     * @param shopId 商户ID
     * @param current 页码
     * @param size 每页大小
     * @return 套餐列表
     */
    @GetMapping("/list/{shopId}")
    public Result listByShopId(
            @PathVariable Long shopId,
            @RequestParam(value = "current", defaultValue = "1") Integer current,
            @RequestParam(value = "size", defaultValue = "10") Integer size
    ) {
        return comboService.listByShopId(shopId, current, size);
    }

    /**
     * 查询套餐详情（用户端）
     * @param id 套餐ID
     * @return 套餐详情
     */
    @GetMapping("/{id}")
    public Result queryById(@PathVariable Long id) {
        return comboService.queryById(id);
    }
}
