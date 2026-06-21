package com.hmdp.controller;

import com.hmdp.dto.ReviewDTO;
import com.hmdp.dto.Result;
import com.hmdp.service.IReviewService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

/**
 * 评价控制器（用户端）
 * 处理评价提交、查询等接口
 */
@RestController
@RequestMapping("/review")
public class ReviewController {

    @Resource
    private IReviewService reviewService;

    /**
     * 提交评价
     *
     * @param reviewDTO 评价信息
     * @return 评价ID
     */
    @PostMapping
    public Result submitReview(@RequestBody ReviewDTO reviewDTO) {
        return reviewService.submitReview(reviewDTO);
    }

    /**
     * 查看单条评价详情
     *
     * @param id 评价ID
     * @return 评价详情
     */
    @GetMapping("/{id}")
    public Result getReviewById(@PathVariable Long id) {
        return reviewService.getReviewById(id);
    }

    /**
     * 我的评价列表
     *
     * @param current 当前页
     * @param size 每页大小
     * @return 评价列表
     */
    @GetMapping("/my")
    public Result getMyReviews(
            @RequestParam(value = "current", defaultValue = "1") Integer current,
            @RequestParam(value = "size", defaultValue = "10") Integer size) {
        return reviewService.getMyReviews(current, size);
    }

    /**
     * 商户评价列表
     *
     * @param shopId 商户ID
     * @param current 当前页
     * @param size 每页大小
     * @return 评价列表
     */
    @GetMapping("/shop/{shopId}")
    public Result getShopReviews(
            @PathVariable Long shopId,
            @RequestParam(value = "current", defaultValue = "1") Integer current,
            @RequestParam(value = "size", defaultValue = "10") Integer size) {
        return reviewService.getShopReviews(shopId, current, size);
    }
}
