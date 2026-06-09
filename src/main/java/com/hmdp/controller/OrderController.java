package com.hmdp.controller;

import com.hmdp.dto.Result;
import com.hmdp.service.IOrderService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

/**
 * 订单控制器
 * 提供订单的CRUD接口
 */
@RestController
@RequestMapping("/order")
public class OrderController {

    @Resource
    private IOrderService orderService;

    /**
     * 创建订单
     * @param orderType 订单类型：1优惠券 2套餐
     * @param bizId 业务ID（优惠券ID或套餐ID）
     * @param quantity 购买数量
     * @return 订单信息
     */
    @PostMapping
    public Result createOrder(
            @RequestParam("orderType") Integer orderType,
            @RequestParam("bizId") Long bizId,
            @RequestParam(value = "quantity", defaultValue = "1") Integer quantity
    ) {
        return orderService.createOrder(orderType, bizId, quantity);
    }

    /**
     * 支付订单（模拟支付）
     * @param id 订单ID
     * @return 操作结果
     */
    @PostMapping("/{id}/pay")
    public Result payOrder(@PathVariable Long id) {
        return orderService.payOrder(id);
    }

    /**
     * 取消订单
     * @param id 订单ID
     * @return 操作结果
     */
    @PostMapping("/{id}/cancel")
    public Result cancelOrder(@PathVariable Long id) {
        return orderService.cancelOrder(id);
    }

    /**
     * 查询我的订单
     * @param status 订单状态（可选）
     * @param current 页码
     * @param size 每页大小
     * @return 订单列表
     */
    @GetMapping("/list")
    public Result queryMyOrders(
            @RequestParam(value = "status", required = false) Integer status,
            @RequestParam(value = "current", defaultValue = "1") Integer current,
            @RequestParam(value = "size", defaultValue = "10") Integer size
    ) {
        return orderService.queryMyOrders(status, current, size);
    }

    /**
     * 查询订单详情
     * @param id 订单ID
     * @return 订单详情
     */
    @GetMapping("/{id}")
    public Result queryOrderById(@PathVariable Long id) {
        return orderService.queryOrderById(id);
    }
}
