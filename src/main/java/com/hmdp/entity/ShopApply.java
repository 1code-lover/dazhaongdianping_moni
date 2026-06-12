package com.hmdp.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 商家入驻申请实体类
 * 对应数据库表：tb_shop_apply
 */
@Data
@TableName("tb_shop_apply")
public class ShopApply implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    
    private Long userId;
    private String shopName;
    private Long shopTypeId;
    private String shopImg;
    private String contactName;
    private String contactPhone;
    private String address;
    private BigDecimal x;
    private BigDecimal y;
    private String licenseNo;
    private String licenseImg;
    private String description;
    private Integer status;
    private String rejectReason;
    private Integer isDeleted;
    private LocalDateTime auditTime;
    private Long auditorId;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
