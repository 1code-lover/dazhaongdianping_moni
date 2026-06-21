package com.hmdp.controller;

import com.hmdp.dto.ReviewReplyDTO;
import com.hmdp.dto.Result;
import com.hmdp.service.IReviewService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

/**
 * 评价控制器（商家端）
 * 处理商家查看评价、回复评价等接口
 */
@RestController
@RequestMapping("/shop/review")
public class ShopReviewController {

    @Resource
    private IReviewService reviewService;

    /**
     * 商家查看评价列表
     *
     * @param current 当前页
     * @param size 每页大小
     * @return 评价列表
     */
    @GetMapping("/list")
    public Result getShopReviewList(
            @RequestParam(value = "current", defaultValue = "1") Integer current,
            @RequestParam(value = "size", defaultValue = "10") Integer size) {
        return reviewService.getShopReviewList(current, size);
    }

    /**
     * 商家回复评价
     *
     * @param replyDTO 回复信息
     * @return 操作结果
     */
    @PutMapping("/reply")
    public Result replyReview(@RequestBody ReviewReplyDTO replyDTO) {
        return reviewService.replyReview(replyDTO);
    }
}
