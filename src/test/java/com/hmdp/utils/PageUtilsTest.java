package com.hmdp.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 分页参数规范化工具单元测试。
 *
 * @author ethan
 * @date 2026-07-16
 */
class PageUtilsTest {

    /**
     * 页码为空或小于 1 时应回退到第一页。
     */
    @Test
    void testNormalizePageNumberWhenInvalidThenReturnFirstPage() {
        assertEquals(1, PageUtils.normalizePageNumber(null));
        assertEquals(1, PageUtils.normalizePageNumber(0));
        assertEquals(1, PageUtils.normalizePageNumber(-10));
    }

    /**
     * 合法页码应保持不变。
     */
    @Test
    void testNormalizePageNumberWhenValidThenKeepValue() {
        assertEquals(8, PageUtils.normalizePageNumber(8));
    }

    /**
     * 每页数量为空或非正数时应使用默认值。
     */
    @Test
    void testNormalizePageSizeWhenInvalidThenReturnDefaultSize() {
        assertEquals(10, PageUtils.normalizePageSize(null));
        assertEquals(10, PageUtils.normalizePageSize(0));
        assertEquals(10, PageUtils.normalizePageSize(-1));
    }

    /**
     * 合法每页数量应保持不变。
     */
    @Test
    void testNormalizePageSizeWhenValidThenKeepValue() {
        assertEquals(20, PageUtils.normalizePageSize(20));
    }

    /**
     * 超大每页数量应限制为 50。
     */
    @Test
    void testNormalizePageSizeWhenTooLargeThenReturnMaximum() {
        assertEquals(50, PageUtils.normalizePageSize(10000));
    }

    /**
     * Spring Data 页码应安全转换为从 0 开始。
     */
    @Test
    void testToZeroBasedPageWhenPageInvalidThenReturnZero() {
        assertEquals(0, PageUtils.toZeroBasedPage(null));
        assertEquals(0, PageUtils.toZeroBasedPage(0));
        assertEquals(2, PageUtils.toZeroBasedPage(3));
    }
}
