package com.hmdp.utils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 订单号生成器
 * 格式：ORD + 日期 + 6位序列号
 * 使用AtomicLong保证线程安全
 */
public class OrderNoGenerator {
    
    private static final AtomicLong SEQUENCE = new AtomicLong(0);
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    
    /**
     * 生成订单号
     * @return 订单号，如：ORD20260609103012000001
     */
    public static String generate() {
        String dateStr = LocalDateTime.now().format(FORMATTER);
        long seq = SEQUENCE.incrementAndGet() % 1000000;
        return String.format("ORD%s%06d", dateStr, seq);
    }
}
