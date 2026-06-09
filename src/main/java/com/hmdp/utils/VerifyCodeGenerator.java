package com.hmdp.utils;

import java.util.concurrent.ThreadLocalRandom;

/**
 * 核销码生成器
 * 生成6位数字核销码
 * 使用ThreadLocalRandom保证线程安全
 */
public class VerifyCodeGenerator {
    
    /**
     * 生成核销码
     * @return 6位数字核销码，如：123456
     */
    public static String generate() {
        int code = 100000 + ThreadLocalRandom.current().nextInt(900000);
        return String.valueOf(code);
    }
}
