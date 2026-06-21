package com.hmdp.service;

import com.hmdp.dto.ReviewDTO;
import com.hmdp.dto.ReviewReplyDTO;
import com.hmdp.dto.Result;
import com.hmdp.entity.Review;
import com.hmdp.mapper.ReviewMapper;
import com.hmdp.service.impl.ReviewServiceImpl;
import com.hmdp.utils.UserHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 评价服务单元测试
 */
@SpringBootTest
class ReviewServiceTest {

    @Resource
    private ReviewServiceImpl reviewService;

    @Resource
    private ReviewMapper reviewMapper;

    @BeforeEach
    void setUp() {
        // 模拟用户登录
        UserHolder.saveUser(new com.hmdp.dto.UserDTO() {{
            setId(1010L);
        }});
    }

    @AfterEach
    void tearDown() {
        UserHolder.removeUser();
    }

    /**
     * 测试提交评价 - 正常场景
     */
    @Test
    void testSubmitReviewWhenValidThenSuccess() {
        // given
        ReviewDTO dto = new ReviewDTO();
        dto.setOrderId(1L);
        dto.setOrderType(2);
        dto.setScore(5);
        dto.setContent("非常好吃，服务态度很好！");

        // when
        Result result = reviewService.submitReview(dto);

        // then
        assertTrue(result.isSuccess());
        assertNotNull(result.getData());
    }

    /**
     * 测试提交评价 - 评分超出范围
     */
    @Test
    void testSubmitReviewWhenScoreOutOfRangeThenFail() {
        // given
        ReviewDTO dto = new ReviewDTO();
        dto.setOrderId(2L);
        dto.setOrderType(2);
        dto.setScore(6); // 超出范围
        dto.setContent("测试评价");

        // when
        Result result = reviewService.submitReview(dto);

        // then
        assertFalse(result.isSuccess());
        assertEquals("评分范围为1-5", result.getErrorMsg());
    }

    /**
     * 测试提交评价 - 内容超过500字
     */
    @Test
    void testSubmitReviewWhenContentTooLongThenFail() {
        // given
        ReviewDTO dto = new ReviewDTO();
        dto.setOrderId(3L);
        dto.setOrderType(2);
        dto.setScore(4);
        dto.setContent("a".repeat(501)); // 超过500字

        // when
        Result result = reviewService.submitReview(dto);

        // then
        assertFalse(result.isSuccess());
        assertEquals("评价内容不超过500字", result.getErrorMsg());
    }

    /**
     * 测试查看单条评价 - 正常场景
     */
    @Test
    void testGetReviewByIdWhenExistsThenSuccess() {
        // given
        Long reviewId = 1L;

        // when
        Result result = reviewService.getReviewById(reviewId);

        // then
        assertTrue(result.isSuccess());
        assertNotNull(result.getData());
    }

    /**
     * 测试查看单条评价 - 不存在
     */
    @Test
    void testGetReviewByIdWhenNotExistsThenFail() {
        // given
        Long reviewId = 999L;

        // when
        Result result = reviewService.getReviewById(reviewId);

        // then
        assertFalse(result.isSuccess());
        assertEquals("评价不存在", result.getErrorMsg());
    }

    /**
     * 测试商家回复评价 - 正常场景
     */
    @Test
    void testReplyReviewWhenValidThenSuccess() {
        // given
        ReviewReplyDTO dto = new ReviewReplyDTO();
        dto.setReviewId(1L);
        dto.setReply("感谢您的好评，欢迎下次光临！");

        // when
        Result result = reviewService.replyReview(dto);

        // then
        assertTrue(result.isSuccess());
    }

    /**
     * 测试商家回复评价 - 回复内容超过200字
     */
    @Test
    void testReplyReviewWhenReplyTooLongThenFail() {
        // given
        ReviewReplyDTO dto = new ReviewReplyDTO();
        dto.setReviewId(1L);
        dto.setReply("a".repeat(201)); // 超过200字

        // when
        Result result = reviewService.replyReview(dto);

        // then
        assertFalse(result.isSuccess());
        assertEquals("回复内容不超过200字", result.getErrorMsg());
    }
}
