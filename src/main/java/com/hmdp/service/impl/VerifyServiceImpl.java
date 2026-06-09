package com.hmdp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.entity.Order;
import com.hmdp.entity.VerifyRecord;
import com.hmdp.mapper.VerifyRecordMapper;
import com.hmdp.service.IOrderService;
import com.hmdp.service.IVerifyService;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

/**
 * 核销服务实现类
 */
@Slf4j
@Service
public class VerifyServiceImpl implements IVerifyService {

    @Resource
    private IOrderService orderService;
    
    @Resource
    private VerifyRecordMapper verifyRecordMapper;

    /**
     * 核销订单
     * 1. 获取当前登录用户
     * 2. 调用订单服务进行核销
     */
    @Override
    public Result verify(String verifyCode) {
        Long operatorId = UserHolder.getUser().getId();
        return orderService.verifyOrder(verifyCode, operatorId);
    }

    /**
     * 验证核销码有效性
     * 查询订单是否存在且状态为待使用
     */
    @Override
    public Result checkVerifyCode(String verifyCode) {
        Order order = orderService.lambdaQuery()
                .eq(Order::getVerifyCode, verifyCode)
                .eq(Order::getIsDeleted, 0)
                .one();
        
        if (order == null) {
            return Result.ok(false);
        }
        
        // 只有待使用状态的订单才能核销
        return Result.ok(order.getStatus() == 1);
    }

    /**
     * 查询核销记录
     * 按核销时间倒序排列
     */
    @Override
    public Result queryVerifyRecords(Long shopId, Integer current, Integer size) {
        Page<VerifyRecord> page = new Page<>(current, size);
        LambdaQueryWrapper<VerifyRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(VerifyRecord::getShopId, shopId)
               .orderByDesc(VerifyRecord::getVerifyTime);
        
        verifyRecordMapper.selectPage(page, wrapper);
        return Result.ok(page.getRecords());
    }
}
