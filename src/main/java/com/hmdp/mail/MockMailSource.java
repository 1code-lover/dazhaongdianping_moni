package com.hmdp.mail;

import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Mock 邮件源。
 * 预置三封不同级别的演示邮件，并支持外部造数，模拟真实收件入口。
 * 将来接入真实 IMAP 时，只需提供同样产出的 MailMessage 列表即可无缝替换。
 *
 * @author ethan
 * @date 2026-10-31
 */
@Component
public class MockMailSource {

    /** 造数邮件的键前缀，避免与预置邮件键冲突 */
    private static final String CUSTOM_KEY_PREFIX = "mock-custom-";

    private final AtomicLong customSequence = new AtomicLong();

    /**
     * 返回预置演示邮件列表。
     * 每次调用返回新实例，messageKey 固定以便验证去重。
     *
     * @return 预置邮件列表
     */
    public List<MailMessage> presetMails() {
        List<MailMessage> mails = new ArrayList<>();
        mails.add(new MailMessage(
                "mock-preset-001", "monitor@hmdp.example",
                "[严重告警] 秒杀库存超卖告警",
                "订单表出现库存不一致，疑似超卖，请立即处理！",
                LocalDateTime.now().minusMinutes(10)));
        mails.add(new MailMessage(
                "mock-preset-002", "monitor@hmdp.example",
                "[警告] Kafka 消费积压告警",
                "seckill-order-g1 组积压 12000 条，延迟升高，请关注。",
                LocalDateTime.now().minusMinutes(5)));
        mails.add(new MailMessage(
                "mock-preset-003", "noreply@hmdp.example",
                "[通知] 每日备份任务完成",
                "MySQL 全量备份已完成，耗时 3 分钟。",
                LocalDateTime.now().minusMinutes(1)));
        return mails;
    }

    /**
     * 构造一封外部造数邮件。
     *
     * @param from    发件人
     * @param subject 主题
     * @param content 正文
     * @return 带自增唯一键的新邮件
     */
    public MailMessage createCustomMail(String from, String subject, String content) {
        String key = CUSTOM_KEY_PREFIX + customSequence.incrementAndGet();
        return new MailMessage(key, from, subject, content, LocalDateTime.now());
    }
}
