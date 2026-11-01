package com.hmdp.mail;

import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 邮件去重器。
 * 内存实现：同一 messageKey 的邮件只允许通过一次，保证链路幂等。
 *
 * @author ethan
 * @date 2026-10-31
 */
@Component
public class MailAlertDeduper {

    private final Set<String> seenKeys = new LinkedHashSet<>();

    /**
     * 尝试登记一封邮件。
     *
     * @param messageKey 邮件唯一键
     * @return true 表示首次出现可放行；false 表示重复需跳过
     */
    public synchronized boolean tryRegister(String messageKey) {
        if (messageKey == null || messageKey.trim().isEmpty()) {
            // 无键邮件无法追踪重复，放行避免漏报
            return true;
        }
        return seenKeys.add(messageKey);
    }

    /**
     * 清空去重记录，供测试与演示重置使用。
     */
    public synchronized void reset() {
        seenKeys.clear();
    }

    /**
     * 查看当前已登记的键数量。
     *
     * @return 已登记键数
     */
    public synchronized int size() {
        return seenKeys.size();
    }
}
