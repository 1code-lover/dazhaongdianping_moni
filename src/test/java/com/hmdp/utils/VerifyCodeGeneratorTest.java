package com.hmdp.utils;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 核销码生成器测试
 */
class VerifyCodeGeneratorTest {

    /**
     * 测试核销码长度
     */
    @Test
    void testGenerate_Length() {
        String code = VerifyCodeGenerator.generate();
        assertNotNull(code);
        assertEquals(6, code.length());
    }
    
    /**
     * 测试核销码为纯数字
     */
    @Test
    void testGenerate_Numeric() {
        String code = VerifyCodeGenerator.generate();
        assertTrue(code.matches("\\d{6}"));
    }
    
    /**
     * 测试核销码范围（100000-999999）
     */
    @Test
    void testGenerate_Range() {
        String code = VerifyCodeGenerator.generate();
        int value = Integer.parseInt(code);
        assertTrue(value >= 100000 && value <= 999999);
    }
    
    /**
     * 测试核销码唯一性（100次生成，大部分应该不重复）
     */
    @Test
    void testGenerate_Unique() {
        Set<String> codes = new HashSet<>();
        for (int i = 0; i < 100; i++) {
            codes.add(VerifyCodeGenerator.generate());
        }
        // 允许少量重复，但大部分应该唯一
        assertTrue(codes.size() > 90);
    }
}
