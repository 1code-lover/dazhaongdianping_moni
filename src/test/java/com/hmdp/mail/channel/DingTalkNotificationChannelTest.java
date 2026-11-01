package com.hmdp.mail.channel;

import com.hmdp.mail.ChannelResult;
import com.hmdp.mail.MailAlert;
import com.hmdp.mail.MailAlertProperties;
import com.hmdp.mail.MailLevel;
import com.hmdp.mail.MailMessage;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 钉钉通知渠道测试。
 * 只验证跳过逻辑与级别路由，不发起真实 HTTP 请求。
 *
 * @author ethan
 * @date 2026-10-31
 */
class DingTalkNotificationChannelTest {

    private final MailAlertProperties properties = new MailAlertProperties();

    private final DingTalkNotificationChannel channel = new DingTalkNotificationChannel(properties);

    /**
     * 构造指定级别的告警。
     *
     * @param level 告警级别
     * @return 告警事件
     */
    private MailAlert alert(MailLevel level) {
        MailMessage mail = new MailMessage("k1", "monitor@hmdp.example", "主题", "正文", LocalDateTime.now());
        return new MailAlert(mail, level, LocalDateTime.now());
    }

    @Test
    void testSupportsCriticalAndWarningOnly() {
        assertTrue(channel.supports(MailLevel.CRITICAL));
        assertTrue(channel.supports(MailLevel.WARNING));
        assertFalse(channel.supports(MailLevel.INFO));
    }

    @Test
    void testNotifyWithoutWebhookReturnsSkipped() {
        properties.getDingTalk().setWebhook("");
        assertEquals(ChannelResult.SKIPPED, channel.notify(alert(MailLevel.CRITICAL)));
    }

    @Test
    void testNotifyDisabledReturnsSkipped() {
        properties.getDingTalk().setEnabled(false);
        properties.getDingTalk().setWebhook("https://oapi.dingtalk.com/robot/send?access_token=x");
        assertEquals(ChannelResult.SKIPPED, channel.notify(alert(MailLevel.WARNING)));
    }

    @Test
    void testChannelNameIsDingtalk() {
        assertEquals("dingtalk", channel.name());
    }
}
