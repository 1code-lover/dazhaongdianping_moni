package com.hmdp.mail;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 邮件分级器测试。
 *
 * @author ethan
 * @date 2026-10-31
 */
class MailClassifierTest {

    private final MailAlertProperties properties = new MailAlertProperties();

    private final MailClassifier classifier = new MailClassifier(properties);

    /**
     * 构造测试邮件。
     *
     * @param subject 主题
     * @param content 正文
     * @return 邮件消息
     */
    private MailMessage mail(String subject, String content) {
        return new MailMessage("key-1", "test@hmdp.example", subject, content, java.time.LocalDateTime.now());
    }

    @Test
    void testClassifyCriticalKeywordInSubjectReturnsCritical() {
        assertEquals(MailLevel.CRITICAL, classifier.classify(mail("库存超卖告警", "请立即处理")));
    }

    @Test
    void testClassifyCriticalKeywordInContentReturnsCritical() {
        assertEquals(MailLevel.CRITICAL, classifier.classify(mail("任务通知", "检测到服务宕机，请处理")));
    }

    @Test
    void testClassifyWarningKeywordReturnsWarning() {
        assertEquals(MailLevel.WARNING, classifier.classify(mail("消费组告警", "消息积压 12000 条")));
    }

    @Test
    void testClassifyNoKeywordReturnsInfo() {
        assertEquals(MailLevel.INFO, classifier.classify(mail("每日备份完成", "备份任务已完成")));
    }

    @Test
    void testClassifyKeywordCaseInsensitiveReturnsCritical() {
        assertEquals(MailLevel.CRITICAL, classifier.classify(mail("jvm alert", "process oom occurred")));
    }

    @Test
    void testClassifyCriticalPriorityHigherThanWarning() {
        // 同一封邮件同时命中两级关键词时，CRITICAL 优先
        assertEquals(MailLevel.CRITICAL, classifier.classify(mail("慢查询与资损同时发生", "出现积压且资损")));
    }

    @Test
    void testClassifyNullSubjectAndContentReturnsInfo() {
        assertEquals(MailLevel.INFO, classifier.classify(mail(null, null)));
    }

    @Test
    void testClassifyBlankKeywordsConfigReturnsInfo() {
        properties.getMail().setCriticalKeywords("  ");
        properties.getMail().setWarningKeywords("");
        assertEquals(MailLevel.INFO, classifier.classify(mail("任意主题", "任意正文")));
    }
}
