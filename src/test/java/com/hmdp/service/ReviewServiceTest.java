package com.hmdp.service;

import java.util.Arrays;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.hmdp.dto.ReviewDTO;
import com.hmdp.dto.ReviewReplyDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Order;
import com.hmdp.entity.Review;
import com.hmdp.entity.ShopApply;
import com.hmdp.mapper.OrderMapper;
import com.hmdp.mapper.ReviewMapper;
import com.hmdp.mapper.ShopApplyMapper;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.impl.ReviewServiceImpl;
import com.hmdp.utils.UserHolder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 评价服务单元测试。
 * 使用 Mockito 隔离数据库和 Spring 容器，验证评价提交、查询和回复规则。
 *
 * @author ethan
 * @date 2026-07-16
 */
@ExtendWith(MockitoExtension.class)
class ReviewServiceTest {

    private static final long USER_ID = 1010L;
    private static final long SHOP_ID = 100L;
    private static final long REVIEW_ID = 200L;

    @Mock
    private ReviewMapper reviewMapper;

    @Mock
    private OrderMapper orderMapper;

    @Mock
    private ShopMapper shopMapper;

    @Mock
    private ShopApplyMapper shopApplyMapper;

    @InjectMocks
    private ReviewServiceImpl reviewService;

    /**
     * 初始化登录用户和通用 Mapper 行为。
     */
    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(reviewService, "baseMapper", reviewMapper);
        UserDTO user = new UserDTO();
        user.setId(USER_ID);
        UserHolder.saveUser(user);

    }

    /**
     * 清理线程中的登录用户。
     */
    @AfterEach
    void tearDown() {
        UserHolder.removeUser();
    }

    /**
     * 合法评价应保存成功并更新商户评分。
     */
    @Test
    void testSubmitReviewWhenValidThenSuccess() {
        prepareReviewSubmission();
        when(reviewMapper.insert(any(Review.class))).thenAnswer(invocation -> {
            Review review = invocation.getArgument(0);
            review.setId(REVIEW_ID);
            return 1;
        });
        ReviewDTO dto = createReviewDto(1L, 5, "非常好吃，服务态度很好！");

        Result result = reviewService.submitReview(dto);

        assertTrue(result.getSuccess());
        assertEquals(REVIEW_ID, result.getData());
        verify(reviewMapper).insert(any(Review.class));
        verify(reviewMapper).updateShopScore(SHOP_ID);
    }

    /**
     * 评分超出范围时应拒绝提交。
     */
    @Test
    void testSubmitReviewWhenScoreOutOfRangeThenFail() {
        prepareReviewSubmission();
        ReviewDTO dto = createReviewDto(2L, 6, "测试评价");

        Result result = reviewService.submitReview(dto);

        assertFalse(result.getSuccess());
        assertEquals("评分范围为1-5", result.getErrorMsg());
    }

    /**
     * 评价内容超过 500 字时应拒绝提交。
     */
    @Test
    void testSubmitReviewWhenContentTooLongThenFail() {
        prepareReviewSubmission();
        ReviewDTO dto = createReviewDto(3L, 4, repeat('a', 501));

        Result result = reviewService.submitReview(dto);

        assertFalse(result.getSuccess());
        assertEquals("评价内容不超过500字", result.getErrorMsg());
    }

    /**
     * 查询存在的评价时应返回评价详情。
     */
    @Test
    void testGetReviewByIdWhenExistsThenSuccess() {
        Review review = createReview(REVIEW_ID, SHOP_ID);
        when(reviewMapper.selectById(REVIEW_ID)).thenReturn(review);

        Result result = reviewService.getReviewById(REVIEW_ID);

        assertTrue(result.getSuccess());
        assertNotNull(result.getData());
    }

    /**
     * 查询不存在的评价时应返回失败结果。
     */
    @Test
    void testGetReviewByIdWhenNotExistsThenFail() {
        when(reviewMapper.selectById(999L)).thenReturn(null);

        Result result = reviewService.getReviewById(999L);

        assertFalse(result.getSuccess());
        assertEquals("评价不存在", result.getErrorMsg());
    }

    /**
     * 店铺所属商家提交合法回复时应更新成功。
     */
    @Test
    void testReplyReviewWhenValidThenSuccess() {
        prepareReplyScenario(null);
        when(reviewMapper.updateById(any(Review.class))).thenReturn(1);
        ReviewReplyDTO dto = createReplyDto("感谢您的好评，欢迎下次光临！");

        Result result = reviewService.replyReview(dto);

        assertTrue(result.getSuccess());
        verify(reviewMapper).updateById(any(Review.class));
    }

    /**
     * 回复内容超过 200 字时应拒绝更新。
     */
    @Test
    void testReplyReviewWhenReplyTooLongThenFail() {
        prepareReplyScenario(null);
        ReviewReplyDTO dto = createReplyDto(repeat('a', 201));

        Result result = reviewService.replyReview(dto);

        assertFalse(result.getSuccess());
        assertEquals("回复内容不超过200字", result.getErrorMsg());
    }

    /**
     * 准备已核销且尚未评价的订单。
     */
    private void prepareReviewSubmission() {
        Order verifiedOrder = new Order();
        verifiedOrder.setId(1L);
        verifiedOrder.setShopId(SHOP_ID);
        verifiedOrder.setStatus(2);
        when(orderMapper.selectById(anyLong())).thenReturn(verifiedOrder);
        when(reviewMapper.selectCount(any())).thenReturn(0);
    }

    /**
     * 创建评价提交参数。
     *
     * @param orderId 订单 ID
     * @param score 评分
     * @param content 评价内容
     * @return 评价参数
     */
    private ReviewDTO createReviewDto(Long orderId, Integer score, String content) {
        ReviewDTO dto = new ReviewDTO();
        dto.setOrderId(orderId);
        dto.setOrderType(2);
        dto.setScore(score);
        dto.setContent(content);
        return dto;
    }

    /**
     * 创建评价实体。
     *
     * @param reviewId 评价 ID
     * @param shopId 店铺 ID
     * @return 评价实体
     */
    private Review createReview(Long reviewId, Long shopId) {
        Review review = new Review();
        review.setId(reviewId);
        review.setShopId(shopId);
        review.setIsDeleted(0);
        return review;
    }

    /**
     * 准备商家回复场景。
     *
     * @param existingReply 已存在回复
     */
    private void prepareReplyScenario(String existingReply) {
        Review review = createReview(REVIEW_ID, SHOP_ID);
        review.setReply(existingReply);
        when(reviewMapper.selectById(REVIEW_ID)).thenReturn(review);

        ShopApply shopApply = new ShopApply();
        shopApply.setShopId(SHOP_ID);
        when(shopApplyMapper.selectOne(any())).thenReturn(shopApply);
    }

    /**
     * 创建评价回复参数。
     *
     * @param reply 回复内容
     * @return 回复参数
     */
    private ReviewReplyDTO createReplyDto(String reply) {
        ReviewReplyDTO dto = new ReviewReplyDTO();
        dto.setReviewId(REVIEW_ID);
        dto.setReply(reply);
        return dto;
    }

    /**
     * 创建兼容 Java 8 的重复字符串。
     *
     * @param value 重复字符
     * @param count 重复次数
     * @return 重复后的字符串
     */
    private String repeat(char value, int count) {
        char[] values = new char[count];
        Arrays.fill(values, value);
        return new String(values);
    }
}
