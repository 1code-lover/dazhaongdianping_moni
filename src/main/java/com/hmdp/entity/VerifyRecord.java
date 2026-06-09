package com.hmdp.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 核销记录实体类
 * 对应数据库表：tb_verify_record
 * 记录每次核销操作的详细信息
 */
@Data
@TableName("tb_verify_record")
public class VerifyRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    
    /**
     * 订单ID
     */
    private Long orderId;
    
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
     * 操作人ID（核销人）
     */
    private Long operatorId;
    
    /**
     * 备注
     */
    private String remark;
    
    /**
     * 核销时间
     */
    private LocalDateTime verifyTime;
}
