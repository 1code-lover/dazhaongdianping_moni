package com.hmdp.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hmdp.dto.Result;
import com.hmdp.entity.Order;

/**
 * 订单服务接口
 */
public interface IOrderService extends IService<Order> {
    
    /**
     * 创建订单
     * @param orderType 订单类型：1优惠券 2套餐
     * @param bizId 业务ID（优惠券ID或套餐ID）
     * @param quantity 购买数量
     * @return 订单信息
     */
    Result createOrder(Integer orderType, Long bizId, Integer quantity);
    
    /**
     * 支付订单（模拟支付）
     * @param orderId 订单ID
     * @return 操作结果
     */
    Result payOrder(Long orderId);
    
    /**
     * 取消订单
     * @param orderId 订单ID
     * @return 操作结果
     */
    Result cancelOrder(Long orderId);
    
    /**
     * 查询我的订单
     * @param status 订单状态（可选）
     * @param current 页码
     * @param size 每页大小
     * @return 订单列表
     */
    Result queryMyOrders(Integer status, Integer current, Integer size);
    
    /**
     * 查询订单详情
     * @param orderId 订单ID
     * @return 订单详情
     */
    Result queryOrderById(Long orderId);
    
    /**
     * 核销订单
     * @param verifyCode 核销码
     * @param operatorId 操作人ID
     * @return 操作结果
     */
    Result verifyOrder(String verifyCode, Long operatorId);
}
