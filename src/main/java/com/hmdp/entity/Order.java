package com.hmdp.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 统一订单实体类
 * 对应数据库表：tb_order
 * 支持优惠券订单和套餐订单
 */
@Data
@TableName("tb_order")
public class Order implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID（雪花算法生成）
     */
    @TableId(value = "id", type = IdType.INPUT)
    private Long id;
    
    /**
     * 订单号
     */
    private String orderNo;
    
    /**
     * 用户ID
     */
    private Long userId;
    
    /**
     * 商户ID
     */
    private Long shopId;
    
    /**
     * 订单类型：1优惠券 2套餐
     */
    private Integer orderType;
    
    /**
     * 业务ID（优惠券ID或套餐ID）
     */
    private Long bizId;
    
    /**
     * 商品标题
     */
    private String title;
    
    /**
     * 支付金额（单位：分）
     */
    private Long amount;
    
    /**
     * 购买数量
     */
    private Integer quantity;
    
    /**
     * 订单状态：0待支付 1待使用 2已核销 3已取消 4退款中 5已退款
     */
    private Integer status;
    
    /**
     * 核销码（6位数字）
     */
    private String verifyCode;
    
    /**
     * 核销时间
     */
    private LocalDateTime verifyTime;
    
    /**
     * 支付时间
     */
    private LocalDateTime payTime;
    
    /**
     * 取消时间
     */
    private LocalDateTime cancelTime;
    
    /**
     * 退款时间
     */
    private LocalDateTime refundTime;
    
    /**
     * 逻辑删除：0未删除 1已删除
     */
    private Integer isDeleted;
    
    /**
     * 创建时间
     */
    private LocalDateTime createTime;
    
    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}
