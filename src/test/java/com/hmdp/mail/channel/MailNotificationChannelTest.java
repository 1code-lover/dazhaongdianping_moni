package com.hmdp.mail.channel;

import com.hmdp.mail.ChannelResult;
import com.hmdp.mail.MailAlert;
import com.hmdp.mail.MailAlertProperties;
import com.hmdp.mail.MailLevel;
import com.hmdp.mail.MailMessage;
import org.junit.jupiter.api.Test;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 邮件发信渠道测试。
 * SMTP 账号通过 JavaMailSenderImpl 的 username 判断，收件人来自配置属性。
 *
 * @author ethan
 * @date 2026-10-31
 */
class MailNotificationChannelTest {

    /**
     * 构造携带 SMTP 账号信息的发信器。
     *
     * @param username SMTP 账号，null 表示未配置
     * @return 发信器
     */
    private JavaMailSender senderWithUsername(String username) {
        JavaMailSenderImpl impl = new JavaMailSenderImpl();
        impl.setUsername(username);
        return impl;
    }

    /**
     * 构造指定级别告警。
     *
     * @param level 告警级别
     * @return 告警事件
     */
    private MailAlert alert(MailLevel level) {
        MailMessage mail = new MailMessage("k1", "monitor@hmdp.example", "库存超卖告警", "立即处理", LocalDateTime.now());
        return new MailAlert(mail, level, LocalDateTime.now());
    }

    @Test
    void testSupportsCriticalAndWarningOnly() {
        MailNotificationChannel channel =
                new MailNotificationChannel(mock(JavaMailSender.class), new MailAlertProperties());
        assertTrue(channel.supports(MailLevel.CRITICAL));
        assertTrue(channel.supports(MailLevel.WARNING));
        assertFalse(channel.supports(MailLevel.INFO));
    }

    @Test
    void testNotifyWithoutRecipientReturnsSkipped() {
        // SMTP 账号已配但收件人为空 → 跳过
        MailAlertProperties props = new MailAlertProperties();
        MailNotificationChannel channel =
                new MailNotificationChannel(senderWithUsername("a@163.com"), props);
        assertEquals(ChannelResult.SKIPPED, channel.notify(alert(MailLevel.CRITICAL)));
    }

    @Test
    void testNotifyWithoutSmtpAccountReturnsSkipped() {
        // 收件人已配但 SMTP 账号为空 → 跳过
        MailAlertProperties props = new MailAlertProperties();
        props.getMailSender().setTo("boss@163.com");
        MailNotificationChannel channel =
                new MailNotificationChannel(senderWithUsername(""), props);
        assertEquals(ChannelResult.SKIPPED, channel.notify(alert(MailLevel.WARNING)));
    }

    @Test
    void testNotifyDisabledReturnsSkipped() {
        MailAlertProperties props = new MailAlertProperties();
        props.getMailSender().setEnabled(false);
        props.getMailSender().setTo("boss@163.com");
        MailNotificationChannel channel =
                new MailNotificationChannel(senderWithUsername("a@163.com"), props);
        assertEquals(ChannelResult.SKIPPED, channel.notify(alert(MailLevel.CRITICAL)));
    }

    @Test
    void testNotifyConfiguredSendsMessageWithAlertContent() {
        // 配置完整时应真实调用 send，且发件人=SMTP 账号、收件人=配置收件人
        JavaMailSenderImpl sender = mock(JavaMailSenderImpl.class);
        when(sender.getUsername()).thenReturn("a@163.com");
        MailAlertProperties props = new MailAlertProperties();
        props.getMailSender().setTo("boss@163.com");
        MailNotificationChannel channel = new MailNotificationChannel(sender, props);

        ChannelResult result = channel.notify(alert(MailLevel.CRITICAL));

        assertEquals(ChannelResult.SENT, result);
        org.mockito.ArgumentCaptor<SimpleMailMessage> captor =
                org.mockito.ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(sender).send(captor.capture());
        assertEquals("a@163.com", captor.getValue().getFrom());
        assertEquals("boss@163.com", captor.getValue().getTo()[0]);
        assertTrue(captor.getValue().getSubject().contains("库存超卖告警"));
        assertTrue(captor.getValue().getText().contains("立即处理"));
    }

    @Test
    void testNotifySendFailurePropagatesForPipelineIsolation() {
        // 发送异常直接上抛，由管道 safeNotify 统一隔离为 FAILED（与钉钉渠道一致）
        JavaMailSender sender = mock(JavaMailSender.class);
        doThrow(new RuntimeException("smtp down")).when(sender).send(any(SimpleMailMessage.class));
        MailAlertProperties props = new MailAlertProperties();
        props.getMailSender().setTo("boss@163.com");
        MailNotificationChannel channel = new MailNotificationChannel(senderWithUsername("a@163.com"), props);

        assertThrows(RuntimeException.class, () -> channel.notify(alert(MailLevel.CRITICAL)));
    }
}
