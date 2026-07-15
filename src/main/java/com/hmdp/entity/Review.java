package com.hmdp.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 评价实体类
 * 对应数据库表：tb_review
 */
@Data
@TableName("tb_review")
public class Review implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 用户ID */
    private Long userId;

    /** 商户ID */
    private Long shopId;

    /** 订单ID */
    private Long orderId;

    /** 订单类型：1优惠券 2套餐 */
    private Integer orderType;

    /** 评分：1-5星 */
    private Integer score;

    /** 评价内容 */
    private String content;

    /** 评价图片，多个用逗号分隔 */
    private String images;

    /** 商家回复 */
    private String reply;

    /** 商家回复时间 */
    private LocalDateTime replyTime;

    /** 状态：0隐藏 1显示 */
    private Integer status;

    /** 逻辑删除：0未删除 1已删除 */
    private Integer isDeleted;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
