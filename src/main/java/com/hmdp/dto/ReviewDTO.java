package com.hmdp.dto;

import lombok.Data;

/**
 * 评价请求DTO
 */
@Data
public class ReviewDTO {

    /** 订单ID */
    private Long orderId;

    /** 订单类型：1优惠券 2套餐 */
    private Integer orderType;

    /** 评分：1-5星 */
    private Integer score;

    /** 评价内容（不超过500字） */
    private String content;

    /** 评价图片（最多9张，逗号分隔） */
    private String images;
}
