package com.hmdp.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hmdp.dto.ReviewDTO;
import com.hmdp.dto.ReviewReplyDTO;
import com.hmdp.dto.Result;
import com.hmdp.entity.Review;

/**
 * 评价服务接口
 */
public interface IReviewService extends IService<Review> {

    /**
     * 提交评价
     *
     * @param reviewDTO 评价信息
     * @return 评价ID
     */
    Result submitReview(ReviewDTO reviewDTO);

    /**
     * 查看单条评价详情
     *
     * @param id 评价ID
     * @return 评价详情
     */
    Result getReviewById(Long id);

    /**
     * 我的评价列表
     *
     * @param current 当前页
     * @param size 每页大小
     * @return 评价列表
     */
    Result getMyReviews(Integer current, Integer size);

    /**
     * 商户评价列表（用户端）
     *
     * @param shopId 商户ID
     * @param current 当前页
     * @param size 每页大小
     * @return 评价列表
     */
    Result getShopReviews(Long shopId, Integer current, Integer size);

    /**
     * 商家查看评价列表
     *
     * @param current 当前页
     * @param size 每页大小
     * @return 评价列表
     */
    Result getShopReviewList(Integer current, Integer size);

    /**
     * 商家回复评价
     *
     * @param replyDTO 回复信息
     * @return 操作结果
     */
    Result replyReview(ReviewReplyDTO replyDTO);
}
