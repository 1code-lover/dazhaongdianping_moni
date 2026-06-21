package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.ReviewDTO;
import com.hmdp.dto.ReviewReplyDTO;
import com.hmdp.dto.Result;
import com.hmdp.entity.Order;
import com.hmdp.entity.Review;
import com.hmdp.entity.ShopApply;
import com.hmdp.mapper.OrderMapper;
import com.hmdp.mapper.ReviewMapper;
import com.hmdp.mapper.ShopApplyMapper;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IReviewService;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;

/**
 * 评价服务实现类
 * 处理评价提交、查询、回复等业务逻辑
 */
@Slf4j
@Service
public class ReviewServiceImpl extends ServiceImpl<ReviewMapper, Review> implements IReviewService {

    @Resource
    private OrderMapper orderMapper;

    @Resource
    private ShopMapper shopMapper;

    @Resource
    private ShopApplyMapper shopApplyMapper;

    @Override
    @Transactional
    public Result submitReview(ReviewDTO reviewDTO) {
        // 1. 校验订单是否存在且已核销
        Order order = orderMapper.selectById(reviewDTO.getOrderId());
        if (order == null) {
            return Result.fail("订单不存在");
        }
        if (order.getStatus() == null || order.getStatus() != 2) {
            return Result.fail("订单未核销，无法评价");
        }

        // 2. 校验是否已评价
        long count = lambdaQuery()
                .eq(Review::getOrderId, reviewDTO.getOrderId())
                .eq(Review::getIsDeleted, 0)
                .count();
        if (count > 0) {
            return Result.fail("该订单已评价");
        }

        // 3. 校验评分范围
        if (reviewDTO.getScore() < 1 || reviewDTO.getScore() > 5) {
            return Result.fail("评分范围为1-5");
        }

        // 4. 校验内容长度
        if (StrUtil.isNotBlank(reviewDTO.getContent()) && reviewDTO.getContent().length() > 500) {
            return Result.fail("评价内容不超过500字");
        }

        // 5. 校验图片数量
        if (StrUtil.isNotBlank(reviewDTO.getImages())) {
            String[] images = reviewDTO.getImages().split(",");
            if (images.length > 9) {
                return Result.fail("图片最多9张");
            }
        }

        // 6. 保存评价
        Review review = new Review();
        review.setUserId(UserHolder.getUser().getId());
        review.setShopId(order.getShopId());
        review.setOrderId(reviewDTO.getOrderId());
        review.setOrderType(reviewDTO.getOrderType());
        review.setScore(reviewDTO.getScore());
        review.setContent(reviewDTO.getContent());
        review.setImages(reviewDTO.getImages());
        review.setStatus(1);
        review.setIsDeleted(0);
        review.setCreateTime(LocalDateTime.now());
        review.setUpdateTime(LocalDateTime.now());
        save(review);

        // 7. 更新商户评分（SQL原子更新）
        updateShopScore(order.getShopId());

        log.info("评价提交成功: reviewId={}, shopId={}, score={}", review.getId(), order.getShopId(), reviewDTO.getScore());
        return Result.ok(review.getId());
    }

    @Override
    public Result getReviewById(Long id) {
        Review review = getById(id);
        if (review == null || review.getIsDeleted() == 1) {
            return Result.fail("评价不存在");
        }
        return Result.ok(review);
    }

    @Override
    public Result getMyReviews(Integer current, Integer size) {
        Long userId = UserHolder.getUser().getId();
        Page<Review> page = lambdaQuery()
                .eq(Review::getUserId, userId)
                .eq(Review::getIsDeleted, 0)
                .orderByDesc(Review::getCreateTime)
                .page(new Page<>(current, size));
        return Result.ok(page.getRecords());
    }

    @Override
    public Result getShopReviews(Long shopId, Integer current, Integer size) {
        Page<Review> page = lambdaQuery()
                .eq(Review::getShopId, shopId)
                .eq(Review::getIsDeleted, 0)
                .eq(Review::getStatus, 1)
                .orderByDesc(Review::getCreateTime)
                .page(new Page<>(current, size));
        return Result.ok(page.getRecords());
    }

    @Override
    public Result getShopReviewList(Integer current, Integer size) {
        // 获取当前商家的店铺ID
        Long shopId = getCurrentShopId();
        if (shopId == null) {
            return Result.fail("您还没有入驻店铺");
        }

        Page<Review> page = lambdaQuery()
                .eq(Review::getShopId, shopId)
                .eq(Review::getIsDeleted, 0)
                .orderByDesc(Review::getCreateTime)
                .page(new Page<>(current, size));
        return Result.ok(page.getRecords());
    }

    @Override
    @Transactional
    public Result replyReview(ReviewReplyDTO replyDTO) {
        // 1. 校验评价是否存在
        Review review = getById(replyDTO.getReviewId());
        if (review == null || review.getIsDeleted() == 1) {
            return Result.fail("评价不存在");
        }

        // 2. 校验是否已回复
        if (review.getReply() != null) {
            return Result.fail("该评价已回复");
        }

        // 3. 校验商家身份（是否是该店铺的商家）
        Long shopId = getCurrentShopId();
        if (shopId == null || !shopId.equals(review.getShopId())) {
            return Result.fail("无权回复该评价");
        }

        // 4. 校验回复内容长度
        if (StrUtil.isNotBlank(replyDTO.getReply()) && replyDTO.getReply().length() > 200) {
            return Result.fail("回复内容不超过200字");
        }

        // 5. 保存回复
        review.setReply(replyDTO.getReply());
        review.setReplyTime(LocalDateTime.now());
        review.setUpdateTime(LocalDateTime.now());
        updateById(review);

        log.info("评价回复成功: reviewId={}, shopId={}", review.getId(), shopId);
        return Result.ok();
    }

    /**
     * 更新商户评分
     * 使用SQL原子更新，避免并发问题
     */
    private void updateShopScore(Long shopId) {
        baseMapper.updateShopScore(shopId);
    }

    /**
     * 获取当前商家的店铺ID
     * 通过用户ID查询ShopApply表获取店铺ID
     */
    private Long getCurrentShopId() {
        Long userId = UserHolder.getUser().getId();
        // 查询ShopApply表，获取已审核通过的店铺
        ShopApply shopApply = shopApplyMapper.selectOne(
                new LambdaQueryWrapper<ShopApply>()
                        .eq(ShopApply::getUserId, userId)
                        .eq(ShopApply::getStatus, 1)
                        .eq(ShopApply::getIsDeleted, 0)
        );
        return shopApply != null ? shopApply.getShopId() : null;
    }
}
