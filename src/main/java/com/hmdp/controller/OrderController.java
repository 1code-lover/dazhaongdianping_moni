package com.hmdp.controller;

import com.hmdp.dto.Result;
import com.hmdp.service.IOrderService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

/**
 * 订单控制器
 * 处理订单相关的 HTTP 请求，包括订单创建、支付、取消、查询等功能
 * 接口路径：/order
 * 支持优惠券订单和套餐订单的统一管理
 *
 * @author ethan
 * @date 2026-06-14
 */
@RestController
@RequestMapping("/order")
public class OrderController {

    @Resource
    private IOrderService orderService;

    /**
     * 创建订单
     * 根据订单类型（优惠券或套餐）创建新订单
     * 自动处理库存扣减、优惠券应用等业务逻辑
     *
     * @param orderType 订单类型：1-优惠券订单，2-套餐订单
     * @param bizId 业务ID（优惠券ID或套餐ID）
     * @param quantity 购买数量（默认1）
     * @return 创建的订单信息
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
     * 支付订单
     * 模拟支付流程，将订单状态从"待支付"更新为"待使用"
     *
     * @param id 订单ID
     * @return 支付结果
     */
    @PostMapping("/{id}/pay")
    public Result payOrder(@PathVariable Long id) {
        return orderService.payOrder(id);
    }

    /**
     * 取消订单
     * 取消订单并回滚库存
     * 订单状态变更为"已取消"
     *
     * @param id 订单ID
     * @return 取消结果
     */
    @PostMapping("/{id}/cancel")
    public Result cancelOrder(@PathVariable Long id) {
        return orderService.cancelOrder(id);
    }

    /**
     * 查询我的订单
     * 分页查询当前登录用户的订单列表，支持按状态筛选
     * 订单状态：待支付、待使用、已核销、已取消等
     *
     * @param status 订单状态（可选，不指定则查询所有状态）
     * @param current 页码（从1开始，默认1）
     * @param size 每页大小（默认10）
     * @return 用户的订单列表
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
     * 获取指定订单的完整信息（包含商品、价格、状态等）
     *
     * @param id 订单ID
     * @return 订单详情
     */
    @GetMapping("/{id}")
    public Result queryOrderById(@PathVariable Long id) {
        return orderService.queryOrderById(id);
    }
}
