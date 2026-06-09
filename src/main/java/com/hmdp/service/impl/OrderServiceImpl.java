package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.Combo;
import com.hmdp.entity.Order;
import com.hmdp.entity.VerifyRecord;
import com.hmdp.mapper.OrderMapper;
import com.hmdp.mapper.VerifyRecordMapper;
import com.hmdp.service.IComboService;
import com.hmdp.service.IOrderService;
import com.hmdp.utils.OrderNoGenerator;
import com.hmdp.utils.UserHolder;
import com.hmdp.utils.VerifyCodeGenerator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;

/**
 * 订单服务实现类
 */
@Slf4j
@Service
public class OrderServiceImpl extends ServiceImpl<OrderMapper, Order> implements IOrderService {

    @Resource
    private IComboService comboService;
    
    @Resource
    private VerifyRecordMapper verifyRecordMapper;
    
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 创建订单
     * 1. 获取用户信息
     * 2. 校验商品信息
     * 3. 扣减库存
     * 4. 创建订单
     * 5. 生成核销码
     */
    @Override
    @Transactional
    public Result createOrder(Integer orderType, Long bizId, Integer quantity) {
        // 1. 获取用户
        Long userId = UserHolder.getUser().getId();
        
        // 2. 根据订单类型获取商品信息
        String title = null;
        Long price = null;
        Long shopId = null;
        
        if (orderType == 2) {
            // 套餐订单
            Combo combo = comboService.getById(bizId);
            if (combo == null || combo.getStatus() != 1 || combo.getIsDeleted() == 1) {
                return Result.fail("套餐不存在或已下架");
            }
            if (combo.getEndTime() != null && combo.getEndTime().isBefore(LocalDateTime.now())) {
                return Result.fail("套餐已过期");
            }
            if (combo.getStock() < quantity) {
                return Result.fail("库存不足");
            }
            title = combo.getTitle();
            price = combo.getPrice();
            shopId = combo.getShopId();
        } else {
            return Result.fail("暂不支持该订单类型");
        }
        
        // 3. 扣减库存
        if (orderType == 2) {
            boolean success = comboService.update()
                    .setSql("stock = stock - " + quantity + ", sales = sales + " + quantity)
                    .eq("id", bizId)
                    .gt("stock", 0)
                    .update();
            if (!success) {
                return Result.fail("库存不足");
            }
        }
        
        // 4. 创建订单
        Order order = new Order();
        order.setId(Long.parseLong(stringRedisTemplate.opsForValue().increment("order:id").toString()));
        order.setOrderNo(OrderNoGenerator.generate());
        order.setUserId(userId);
        order.setShopId(shopId);
        order.setOrderType(orderType);
        order.setBizId(bizId);
        order.setTitle(title);
        order.setAmount(price * quantity);
        order.setQuantity(quantity);
        order.setStatus(0); // 待支付
        order.setVerifyCode(VerifyCodeGenerator.generate());
        order.setIsDeleted(0);
        order.setCreateTime(LocalDateTime.now());
        order.setUpdateTime(LocalDateTime.now());
        
        save(order);
        
        log.info("创建订单成功: orderNo={}, userId={}, orderType={}, bizId={}", 
                order.getOrderNo(), userId, orderType, bizId);
        return Result.ok(order);
    }

    /**
     * 支付订单（模拟支付）
     * 1. 校验订单状态
     * 2. 更新订单状态为待使用
     */
    @Override
    @Transactional
    public Result payOrder(Long orderId) {
        Long userId = UserHolder.getUser().getId();
        Order order = getById(orderId);
        
        if (order == null || !order.getUserId().equals(userId)) {
            return Result.fail("订单不存在");
        }
        if (order.getStatus() != 0) {
            return Result.fail("订单状态异常");
        }
        
        order.setStatus(1); // 待使用
        order.setPayTime(LocalDateTime.now());
        order.setUpdateTime(LocalDateTime.now());
        updateById(order);
        
        log.info("支付订单成功: orderNo={}", order.getOrderNo());
        return Result.ok(order);
    }

    /**
     * 取消订单
     * 1. 校验订单状态
     * 2. 回滚库存
     * 3. 更新订单状态
     */
    @Override
    @Transactional
    public Result cancelOrder(Long orderId) {
        Long userId = UserHolder.getUser().getId();
        Order order = getById(orderId);
        
        if (order == null || !order.getUserId().equals(userId)) {
            return Result.fail("订单不存在");
        }
        if (order.getStatus() != 0) {
            return Result.fail("订单状态异常，无法取消");
        }
        
        // 回滚库存
        if (order.getOrderType() == 2) {
            comboService.update()
                    .setSql("stock = stock + " + order.getQuantity() + ", sales = sales - " + order.getQuantity())
                    .eq("id", order.getBizId())
                    .update();
        }
        
        order.setStatus(3); // 已取消
        order.setCancelTime(LocalDateTime.now());
        order.setUpdateTime(LocalDateTime.now());
        updateById(order);
        
        log.info("取消订单成功: orderNo={}", order.getOrderNo());
        return Result.ok();
    }

    /**
     * 查询我的订单
     */
    @Override
    public Result queryMyOrders(Integer status, Integer current, Integer size) {
        Long userId = UserHolder.getUser().getId();
        
        Page<Order> page = lambdaQuery()
                .eq(Order::getUserId, userId)
                .eq(Order::getIsDeleted, 0)
                .eq(status != null, Order::getStatus, status)
                .orderByDesc(Order::getCreateTime)
                .page(new Page<>(current, size));
        
        return Result.ok(page.getRecords());
    }

    /**
     * 查询订单详情
     */
    @Override
    public Result queryOrderById(Long orderId) {
        Order order = getById(orderId);
        if (order == null || order.getIsDeleted() == 1) {
            return Result.fail("订单不存在");
        }
        return Result.ok(order);
    }

    /**
     * 核销订单
     * 1. 校验核销码有效性
     * 2. 校验订单状态
     * 3. 校验套餐是否过期
     * 4. 更新订单状态
     * 5. 写入核销记录
     */
    @Override
    @Transactional
    public Result verifyOrder(String verifyCode, Long operatorId) {
        // 1. 查询订单
        Order order = lambdaQuery()
                .eq(Order::getVerifyCode, verifyCode)
                .eq(Order::getIsDeleted, 0)
                .one();
        
        if (order == null) {
            return Result.fail("核销码无效");
        }
        if (order.getStatus() != 1) {
            return Result.fail("订单状态异常，无法核销");
        }
        
        // 2. 验证套餐是否过期（如果是套餐订单）
        if (order.getOrderType() == 2) {
            Combo combo = comboService.getById(order.getBizId());
            if (combo != null && combo.getEndTime() != null 
                && combo.getEndTime().isBefore(LocalDateTime.now())) {
                return Result.fail("套餐已过期，无法核销");
            }
        }
        
        // 3. 更新订单状态
        order.setStatus(2); // 已核销
        order.setVerifyTime(LocalDateTime.now());
        order.setUpdateTime(LocalDateTime.now());
        updateById(order);
        
        // 4. 写入核销记录
        VerifyRecord record = new VerifyRecord();
        record.setOrderId(order.getId());
        record.setOrderNo(order.getOrderNo());
        record.setUserId(order.getUserId());
        record.setShopId(order.getShopId());
        record.setOperatorId(operatorId);
        record.setRemark("核销成功");
        record.setVerifyTime(LocalDateTime.now());
        verifyRecordMapper.insert(record);
        
        log.info("核销订单成功: orderNo={}, operatorId={}", order.getOrderNo(), operatorId);
        return Result.ok(order);
    }
}
