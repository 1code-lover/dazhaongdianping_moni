package com.hmdp.mail;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mock 邮件源测试。
 *
 * @author ethan
 * @date 2026-10-31
 */
class MockMailSourceTest {

    private final MockMailSource source = new MockMailSource();

    @Test
    void testPresetMailsContainThreeDistinctKeys() {
        List<MailMessage> mails = source.presetMails();
        assertEquals(3, mails.size());
        assertNotEquals(mails.get(0).getMessageKey(), mails.get(1).getMessageKey());
        assertNotEquals(mails.get(1).getMessageKey(), mails.get(2).getMessageKey());
    }

    @Test
    void testPresetMailsCoverAllThreeLevels() {
        // 预置邮件分别命中 CRITICAL / WARNING / INFO 三级，保证演示覆盖全路由
        List<MailMessage> mails = source.presetMails();
        MailClassifier classifier = new MailClassifier(new MailAlertProperties());
        assertEquals(MailLevel.CRITICAL, classifier.classify(mails.get(0)));
        assertEquals(MailLevel.WARNING, classifier.classify(mails.get(1)));
        assertEquals(MailLevel.INFO, classifier.classify(mails.get(2)));
    }

    @Test
    void testCreateCustomMailIncrementsKey() {
        MailMessage first = source.createCustomMail("a@x.com", "主题", "内容");
        MailMessage second = source.createCustomMail("b@x.com", "主题", "内容");
        assertNotEquals(first.getMessageKey(), second.getMessageKey());
        assertTrue(first.getMessageKey().startsWith("mock-custom-"));
        assertEquals("a@x.com", first.getFrom());
        assertEquals("主题", first.getSubject());
    }
}
