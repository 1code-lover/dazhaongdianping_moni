package com.hmdp.mail;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 邮件告警管道服务测试。
 * 覆盖三级渠道路由、渠道异常隔离、去重集成与总开关。
 *
 * @author ethan
 * @date 2026-10-31
 */
class MailAlertPipelineServiceTest {

    private MailAlertProperties properties;

    private MailAlertDeduper deduper;

    private NotificationChannel popupChannel;

    private NotificationChannel dingTalkChannel;

    private NotificationChannel alarmChannel;

    private MailAlertPipelineService service;

    /**
     * 构造三个模拟渠道，支持级别与设计路由一致。
     */
    @BeforeEach
    void setUp() {
        properties = new MailAlertProperties();
        deduper = new MailAlertDeduper();
        popupChannel = mock(NotificationChannel.class);
        dingTalkChannel = mock(NotificationChannel.class);
        alarmChannel = mock(NotificationChannel.class);

        when(popupChannel.name()).thenReturn("popup");
        when(popupChannel.supports(any())).thenReturn(true);
        when(dingTalkChannel.name()).thenReturn("dingtalk");
        when(dingTalkChannel.supports(MailLevel.CRITICAL)).thenReturn(true);
        when(dingTalkChannel.supports(MailLevel.WARNING)).thenReturn(true);
        when(alarmChannel.name()).thenReturn("phone-alarm");
        when(alarmChannel.supports(MailLevel.CRITICAL)).thenReturn(true);

        service = new MailAlertPipelineService(properties, new MailClassifier(properties), deduper,
                Arrays.asList(popupChannel, dingTalkChannel, alarmChannel));
    }

    /**
     * 构造指定主题的测试邮件。
     *
     * @param key     邮件键
     * @param subject 主题
     * @return 邮件消息
     */
    private MailMessage mail(String key, String subject) {
        return new MailMessage(key, "test@hmdp.example", subject, "正文", LocalDateTime.now());
    }

    @Test
    void testProcessCriticalMailNotifiesAllThreeChannels() {
        when(popupChannel.notify(any())).thenReturn(ChannelResult.SENT);
        when(dingTalkChannel.notify(any())).thenReturn(ChannelResult.SENT);
        when(alarmChannel.notify(any())).thenReturn(ChannelResult.SENT);

        List<MailProcessResult> results = service.process(Collections.singletonList(mail("k1", "出现资损")));

        verify(popupChannel).notify(any());
        verify(dingTalkChannel).notify(any());
        verify(alarmChannel).notify(any());
        assertEquals(ChannelResult.SENT, results.get(0).getChannelResults().get("popup"));
        assertEquals(ChannelResult.SENT, results.get(0).getChannelResults().get("dingtalk"));
        assertEquals(ChannelResult.SENT, results.get(0).getChannelResults().get("phone-alarm"));
        assertEquals(MailLevel.CRITICAL, results.get(0).getLevel());
        assertEquals("NEW", results.get(0).getStatus());
    }

    @Test
    void testProcessWarningMailSkipsAlarmChannel() {
        List<MailProcessResult> results = service.process(Collections.singletonList(mail("k2", "消息积压严重")));

        verify(popupChannel).notify(any());
        verify(dingTalkChannel).notify(any());
        verify(alarmChannel, never()).notify(any());
        assertEquals(MailLevel.WARNING, results.get(0).getLevel());
    }

    @Test
    void testProcessInfoMailOnlyPopupChannel() {
        List<MailProcessResult> results = service.process(Collections.singletonList(mail("k3", "每日备份完成")));

        verify(popupChannel).notify(any());
        verify(dingTalkChannel, never()).notify(any());
        verify(alarmChannel, never()).notify(any());
        assertEquals(MailLevel.INFO, results.get(0).getLevel());
    }

    @Test
    void testProcessChannelExceptionDoesNotAffectOthers() {
        // popup 渠道抛异常时，其余渠道照常发送，异常渠道标记 FAILED
        when(popupChannel.notify(any())).thenThrow(new RuntimeException("boom"));
        when(dingTalkChannel.notify(any())).thenReturn(ChannelResult.SENT);
        when(alarmChannel.notify(any())).thenReturn(ChannelResult.SENT);

        List<MailProcessResult> results = service.process(Collections.singletonList(mail("k4", "库存超卖")));

        assertEquals(ChannelResult.FAILED, results.get(0).getChannelResults().get("popup"));
        assertEquals(ChannelResult.SENT, results.get(0).getChannelResults().get("dingtalk"));
        assertEquals(ChannelResult.SENT, results.get(0).getChannelResults().get("phone-alarm"));
    }

    @Test
    void testProcessDuplicateMailSkipsDispatch() {
        MailMessage duplicate = mail("k5", "出现资损");
        service.process(Collections.singletonList(duplicate));
        List<MailProcessResult> results = service.process(Collections.singletonList(duplicate));

        assertEquals("DUPLICATED", results.get(0).getStatus());
        assertTrue(results.get(0).getChannelResults().isEmpty());
        // 第二次处理不应触达渠道：popup 仅在首次处理时被调用一次
        org.mockito.Mockito.verify(popupChannel, org.mockito.Mockito.times(1)).notify(any());
    }

    @Test
    void testProcessDisabledReturnsDisabledWithoutDispatch() {
        properties.setEnabled(false);
        List<MailProcessResult> results = service.process(Collections.singletonList(mail("k6", "出现资损")));

        assertEquals("DISABLED", results.get(0).getStatus());
        assertTrue(results.get(0).getChannelResults().isEmpty());
        verify(popupChannel, never()).notify(any());
    }

    @Test
    void testProcessPreservesInputOrder() {
        List<MailProcessResult> results =
                service.process(Arrays.asList(mail("ka", "资损"), mail("kb", "备份完成"), mail("kc", "积压")));
        assertEquals("ka", results.get(0).getMessageKey());
        assertEquals("kb", results.get(1).getMessageKey());
        assertEquals("kc", results.get(2).getMessageKey());
    }

    @Test
    void testDispatchDirectRoutesByLevelWithoutDedup() {
        // 日程提醒等非邮件来源直接分发：同一告警可重复分发，不受去重与分级影响
        when(popupChannel.notify(any())).thenReturn(ChannelResult.SENT);
        when(dingTalkChannel.notify(any())).thenReturn(ChannelResult.SENT);

        MailMessage synthetic = new MailMessage("reminder-1", "sys@hmdp.example", "提醒", "内容",
                LocalDateTime.now());
        MailAlert alert = new MailAlert(synthetic, MailLevel.WARNING, LocalDateTime.now());
        service.dispatchDirect(alert);
        service.dispatchDirect(alert);

        verify(popupChannel, org.mockito.Mockito.times(2)).notify(any());
        verify(dingTalkChannel, org.mockito.Mockito.times(2)).notify(any());
        verify(alarmChannel, never()).notify(any());
    }
}
