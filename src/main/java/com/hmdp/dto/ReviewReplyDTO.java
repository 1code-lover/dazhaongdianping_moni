package com.hmdp.dto;

import lombok.Data;

/**
 * 商家回复DTO
 */
@Data
public class ReviewReplyDTO {

    /** 评价ID */
    private Long reviewId;

    /** 回复内容（不超过200字） */
    private String reply;
}
