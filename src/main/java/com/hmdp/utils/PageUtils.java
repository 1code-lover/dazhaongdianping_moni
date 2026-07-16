package com.hmdp.utils;

/**
 * 分页参数规范化工具。
 * 统一限制页码和每页数量，避免非法参数和超大分页查询。
 *
 * @author ethan
 * @date 2026-07-16
 */
public final class PageUtils {

    public static final int DEFAULT_PAGE_NUMBER = 1;
    public static final int DEFAULT_PAGE_SIZE = 10;
    public static final int MAX_PAGE_SIZE = 50;

    /**
     * 将页码规范化为从 1 开始的正整数。
     *
     * @param pageNumber 外部页码
     * @return 合法页码
     */
    public static int normalizePageNumber(Integer pageNumber) {
        return pageNumber == null || pageNumber < DEFAULT_PAGE_NUMBER
                ? DEFAULT_PAGE_NUMBER : pageNumber;
    }

    /**
     * 将每页数量限制在 1 到最大值之间。
     *
     * @param pageSize 外部每页数量
     * @return 合法每页数量
     */
    public static int normalizePageSize(Integer pageSize) {
        if (pageSize == null || pageSize <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(pageSize, MAX_PAGE_SIZE);
    }

    /**
     * 转换为 Spring Data 使用的从 0 开始页码。
     *
     * @param pageNumber 外部页码
     * @return 从 0 开始的合法页码
     */
    public static int toZeroBasedPage(Integer pageNumber) {
        return normalizePageNumber(pageNumber) - 1;
    }

    /**
     * 工具类不允许实例化。
     */
    private PageUtils() {
    }
}
