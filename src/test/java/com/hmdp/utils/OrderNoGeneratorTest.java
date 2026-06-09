package com.hmdp.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 订单号生成器测试
 */
class OrderNoGeneratorTest {

    /**
     * 测试订单号格式
     * 格式：ORD + 14位日期 + 6位序列号 = 23位
     */
    @Test
    void testGenerate_Format() {
        String orderNo = OrderNoGenerator.generate();
        assertNotNull(orderNo);
        assertTrue(orderNo.startsWith("ORD"));
        assertEquals(23, orderNo.length());
    }
    
    /**
     * 测试订单号唯一性
     */
    @Test
    void testGenerate_Unique() {
        String orderNo1 = OrderNoGenerator.generate();
        String orderNo2 = OrderNoGenerator.generate();
        assertNotEquals(orderNo1, orderNo2);
    }
}
