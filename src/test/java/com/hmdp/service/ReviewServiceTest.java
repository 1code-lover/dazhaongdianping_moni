package com.hmdp.service;

import cn.hutool.core.util.StrUtil;
import com.hmdp.dto.ReviewDTO;
import com.hmdp.dto.ReviewReplyDTO;
import com.hmdp.dto.Result;
import com.hmdp.service.impl.ReviewServiceImpl;
import com.hmdp.utils.UserHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.annotation.Resource;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 评价服务测试（integration 分层）
 * 基于 JdbcTemplate 数据夹具 + 真实 Service，需要外部 MySQL/Redis，
 * 由 -Pintegration-test 显式执行；默认快速分层不加载 Spring 上下文
 */
@Tag("integration")
@SpringBootTest
class ReviewServiceTest {

    private static final long TEST_USER_ID = 1010L;
    private static final long ORDER_ID_SUCCESS = 11001L;
    private static final long ORDER_ID_SCORE_INVALID = 11002L;
    private static final long ORDER_ID_CONTENT_TOO_LONG = 11003L;
    private static final long EXISTING_REVIEW_ID = 21001L;
    private static final long EXISTING_REVIEW_ORDER_ID = 11010L;
    private static final long TEST_SHOP_ID = 1L;

    @Resource
    private ReviewServiceImpl reviewService;

    @Resource
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        // 模拟用户登录
        UserHolder.saveUser(new com.hmdp.dto.UserDTO() {{
            setId(TEST_USER_ID);
        }});

        ensureReviewInfrastructure();
        seedTestData();
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
        dto.setOrderId(ORDER_ID_SUCCESS);
        dto.setOrderType(2);
        dto.setScore(5);
        dto.setContent("非常好吃，服务态度很好！");

        // when
        Result result = reviewService.submitReview(dto);

        // then
        assertTrue(Boolean.TRUE.equals(result.getSuccess()));
        assertNotNull(result.getData());
    }

    /**
     * 测试提交评价 - 评分超出范围
     */
    @Test
    void testSubmitReviewWhenScoreOutOfRangeThenFail() {
        // given
        ReviewDTO dto = new ReviewDTO();
        dto.setOrderId(ORDER_ID_SCORE_INVALID);
        dto.setOrderType(2);
        dto.setScore(6); // 超出范围
        dto.setContent("测试评价");

        // when
        Result result = reviewService.submitReview(dto);

        // then
        assertFalse(Boolean.TRUE.equals(result.getSuccess()));
        assertEquals("评分范围为1-5", result.getErrorMsg());
    }

    /**
     * 测试提交评价 - 内容超过500字
     */
    @Test
    void testSubmitReviewWhenContentTooLongThenFail() {
        // given
        ReviewDTO dto = new ReviewDTO();
        dto.setOrderId(ORDER_ID_CONTENT_TOO_LONG);
        dto.setOrderType(2);
        dto.setScore(4);
        dto.setContent(StrUtil.repeat("a", 501)); // 超过500字

        // when
        Result result = reviewService.submitReview(dto);

        // then
        assertFalse(Boolean.TRUE.equals(result.getSuccess()));
        assertEquals("评价内容不超过500字", result.getErrorMsg());
    }

    /**
     * 测试查看单条评价 - 正常场景
     */
    @Test
    void testGetReviewByIdWhenExistsThenSuccess() {
        // given
        Long reviewId = EXISTING_REVIEW_ID;

        // when
        Result result = reviewService.getReviewById(reviewId);

        // then
        assertTrue(Boolean.TRUE.equals(result.getSuccess()));
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
        assertFalse(Boolean.TRUE.equals(result.getSuccess()));
        assertEquals("评价不存在", result.getErrorMsg());
    }

    /**
     * 测试商家回复评价 - 正常场景
     */
    @Test
    void testReplyReviewWhenValidThenSuccess() {
        // given
        ReviewReplyDTO dto = new ReviewReplyDTO();
        dto.setReviewId(EXISTING_REVIEW_ID);
        dto.setReply("感谢您的好评，欢迎下次光临！");

        // when
        Result result = reviewService.replyReview(dto);

        // then
        assertTrue(Boolean.TRUE.equals(result.getSuccess()));
    }

    /**
     * 测试商家回复评价 - 回复内容超过200字
     */
    @Test
    void testReplyReviewWhenReplyTooLongThenFail() {
        // given
        ReviewReplyDTO dto = new ReviewReplyDTO();
        dto.setReviewId(EXISTING_REVIEW_ID);
        dto.setReply(StrUtil.repeat("a", 201)); // 超过200字

        // when
        Result result = reviewService.replyReview(dto);

        // then
        assertFalse(Boolean.TRUE.equals(result.getSuccess()));
        assertEquals("回复内容不超过200字", result.getErrorMsg());
    }

    /**
     * 确保评价相关表和字段存在
     */
    private void ensureReviewInfrastructure() {
        jdbcTemplate.execute(
                "CREATE TABLE IF NOT EXISTS tb_review (" +
                        "id bigint(20) NOT NULL AUTO_INCREMENT," +
                        "user_id bigint(20) NOT NULL," +
                        "shop_id bigint(20) NOT NULL," +
                        "order_id bigint(20) NOT NULL," +
                        "order_type tinyint(1) NOT NULL," +
                        "score int(1) NOT NULL," +
                        "content text DEFAULT NULL," +
                        "images varchar(1000) DEFAULT NULL," +
                        "reply text DEFAULT NULL," +
                        "reply_time datetime DEFAULT NULL," +
                        "status tinyint(1) DEFAULT 1," +
                        "is_deleted tinyint(1) DEFAULT 0," +
                        "create_time datetime DEFAULT CURRENT_TIMESTAMP," +
                        "update_time datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
                        "PRIMARY KEY (id)," +
                        "UNIQUE KEY uk_order_id (order_id)," +
                        "KEY idx_shop_id (shop_id)," +
                        "KEY idx_user_id (user_id)" +
                        ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='评价表'"
        );

        try {
            jdbcTemplate.execute("ALTER TABLE tb_shop ADD COLUMN avg_score decimal(2,1) DEFAULT 0 COMMENT '平均评分'");
        } catch (Exception ignored) {
        }

        try {
            jdbcTemplate.execute("ALTER TABLE tb_shop ADD COLUMN review_count int DEFAULT 0 COMMENT '评价数量'");
        } catch (Exception ignored) {
        }

        try {
            jdbcTemplate.execute("ALTER TABLE tb_shop_apply ADD COLUMN shop_id bigint(20) NULL COMMENT '审核通过后创建的店铺ID'");
        } catch (Exception ignored) {
        }
    }

    /**
     * 准备测试夹具数据
     */
    private void seedTestData() {
        jdbcTemplate.update("DELETE FROM tb_review WHERE id = ? OR order_id IN (?, ?, ?, ?)",
                EXISTING_REVIEW_ID, ORDER_ID_SUCCESS, ORDER_ID_SCORE_INVALID, ORDER_ID_CONTENT_TOO_LONG, EXISTING_REVIEW_ORDER_ID);
        jdbcTemplate.update("DELETE FROM tb_order WHERE id IN (?, ?, ?, ?)",
                ORDER_ID_SUCCESS, ORDER_ID_SCORE_INVALID, ORDER_ID_CONTENT_TOO_LONG, EXISTING_REVIEW_ORDER_ID);
        jdbcTemplate.update("DELETE FROM tb_shop_apply WHERE user_id = ?", TEST_USER_ID);

        LocalDateTime now = LocalDateTime.now();

        insertOrder(ORDER_ID_SUCCESS, "ORD-REVIEW-SUCCESS", "评价测试订单-成功场景", now);
        insertOrder(ORDER_ID_SCORE_INVALID, "ORD-REVIEW-SCORE", "评价测试订单-评分越界", now.plusSeconds(1));
        insertOrder(ORDER_ID_CONTENT_TOO_LONG, "ORD-REVIEW-CONTENT", "评价测试订单-内容过长", now.plusSeconds(2));
        insertOrder(EXISTING_REVIEW_ORDER_ID, "ORD-REVIEW-EXISTING", "评价测试订单-已有评价", now.plusSeconds(3));

        jdbcTemplate.update(
                "INSERT INTO tb_review " +
                        "(id, user_id, shop_id, order_id, order_type, score, content, reply, reply_time, status, is_deleted, create_time, update_time) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, NULL, NULL, 1, 0, NOW(), NOW())",
                EXISTING_REVIEW_ID, TEST_USER_ID, TEST_SHOP_ID, EXISTING_REVIEW_ORDER_ID, 2, 5, "这是一条用于查询和回复的测试评价");

        jdbcTemplate.update(
                "INSERT INTO tb_shop_apply " +
                        "(user_id, shop_id, shop_name, shop_type_id, contact_name, contact_phone, address, status, is_deleted, audit_time, create_time, update_time) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, 1, 0, NOW(), NOW(), NOW())",
                TEST_USER_ID, TEST_SHOP_ID, "测试商户", 1L, "测试联系人", "13800138000", "测试地址");
    }

    private void insertOrder(Long orderId, String orderNo, String title, LocalDateTime createTime) {
        String verifyCode = String.format("%06d", orderId % 1000000);
        jdbcTemplate.update(
                "INSERT INTO tb_order " +
                        "(id, order_no, user_id, shop_id, order_type, biz_id, title, amount, quantity, status, verify_code, pay_time, is_deleted, create_time, update_time) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW(), 0, ?, ?)",
                orderId, orderNo, TEST_USER_ID, TEST_SHOP_ID, 2, 1L, title, 16800L, 1, 2, verifyCode, createTime, createTime);
    }
}
