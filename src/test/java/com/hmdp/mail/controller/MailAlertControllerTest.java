package com.hmdp.mail.controller;

import com.hmdp.dto.Result;
import com.hmdp.mail.MailAlertProperties;
import com.hmdp.mail.MailCreateRequest;
import com.hmdp.mail.MailLevel;
import com.hmdp.mail.MailMessage;
import com.hmdp.mail.MailProcessResult;
import com.hmdp.mail.MailReceiver;
import com.hmdp.mail.MockMailSource;
import com.hmdp.mail.SseAlertBroadcaster;
import com.hmdp.mail.MailAlertPipelineService;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 邮件告警控制器测试。
 * 纯 Mockito 构造依赖，验证接口返回结构与链路衔接。
 *
 * @author ethan
 * @date 2026-10-31
 */
class MailAlertControllerTest {

    private final MailAlertPipelineService pipelineService = mock(MailAlertPipelineService.class);

    private final MailReceiver mailReceiver = mock(MailReceiver.class);

    private final MailAlertController controller =
            new MailAlertController(new MockMailSource(), pipelineService, new SseAlertBroadcaster(),
                    new MailAlertProperties(), mailReceiver);

    /**
     * 构造一条处理结果。
     *
     * @param key 邮件键
     * @return 处理结果
     */
    private MailProcessResult result(String key) {
        MailMessage mail = new MailMessage(key, "a@x.com", "主题", "正文", LocalDateTime.now());
        return MailProcessResult.of(mail, MailLevel.INFO, "NEW");
    }

    @Test
    void testListPresetMailsReturnsThree() {
        Result result = controller.listPresetMails();
        assertEquals(Boolean.TRUE, result.getSuccess());
        assertEquals(3, ((List<?>) result.getData()).size());
    }

    @Test
    void testCreateAndProcessMailReturnsPipelineResult() {
        when(pipelineService.process(anyList()))
                .thenReturn(Collections.singletonList(result("mock-custom-1")));

        MailCreateRequest request = new MailCreateRequest();
        request.setFrom("ops@hmdp.example");
        request.setSubject("压测通知");
        request.setContent("压测结束");

        Result result = controller.createAndProcessMail(request);

        assertEquals(Boolean.TRUE, result.getSuccess());
        MailProcessResult data = ((List<MailProcessResult>) result.getData()).get(0);
        assertEquals("mock-custom-1", data.getMessageKey());
        assertEquals("NEW", data.getStatus());
    }

    @Test
    void testCreateAndProcessMailUsesDefaultFromWhenBlank() {
        when(pipelineService.process(anyList())).thenReturn(Collections.singletonList(result("mock-custom-2")));

        MailCreateRequest request = new MailCreateRequest();
        request.setSubject("无发件人邮件");

        controller.createAndProcessMail(request);

        // 发件人为空时使用默认值（通过管道收到的邮件字段校验）
        org.mockito.ArgumentCaptor<List<MailMessage>> captor = org.mockito.ArgumentCaptor.forClass(List.class);
        org.mockito.Mockito.verify(pipelineService).process(captor.capture());
        assertEquals("demo@hmdp.example", captor.getValue().get(0).getFrom());
    }

    @Test
    void testRunPipelineReturnsResults() {
        when(pipelineService.process(anyList()))
                .thenReturn(java.util.Arrays.asList(result("a"), result("b"), result("c")));

        Result result = controller.runPipeline();

        assertEquals(Boolean.TRUE, result.getSuccess());
        assertEquals(3, ((List<?>) result.getData()).size());
    }

    @Test
    void testSubscribeEventsReturnsEmitter() {
        SseEmitter emitter = controller.subscribeEvents();
        assertNotNull(emitter);
    }

    @Test
    void testTestPopupReturnsOnlineClients() {
        Result result = controller.testPopup();
        assertEquals(Boolean.TRUE, result.getSuccess());
        assertTrue(((java.util.Map<?, ?>) result.getData()).containsKey("onlineClients"));
    }

    @Test
    void testConfigReturnsSummary() {
        Result result = controller.config();
        java.util.Map<?, ?> data = (java.util.Map<?, ?>) result.getData();
        assertTrue(data.containsKey("enabled"));
        assertTrue(data.containsKey("phoneAlarmEnabled"));
        assertTrue(data.containsKey("dingTalkConfigured"));
        assertTrue(data.containsKey("mailSenderReady"));
        assertTrue(data.containsKey("mailReaderEnabled"));
        assertTrue(data.containsKey("onlineClients"));
    }

    @Test
    void testPollMailboxWithoutAccountReturnsFail() {
        when(mailReceiver.isConfigured()).thenReturn(false);
        Result result = controller.pollMailbox();
        assertEquals(Boolean.FALSE, result.getSuccess());
    }

    @Test
    void testPollMailboxConfiguredReturnsFetchedAndResults() {
        when(mailReceiver.isConfigured()).thenReturn(true);
        when(mailReceiver.fetchNewMails())
                .thenReturn(Collections.singletonList(new MailMessage("imap-9", "a@x.com", "超卖", "内容", LocalDateTime.now())));
        when(mailReceiver.getLastSeenUid()).thenReturn(9L);
        when(pipelineService.process(anyList())).thenReturn(Collections.singletonList(result("imap-9")));

        Result result = controller.pollMailbox();

        assertEquals(Boolean.TRUE, result.getSuccess());
        java.util.Map<?, ?> data = (java.util.Map<?, ?>) result.getData();
        assertEquals(1, data.get("fetched"));
        assertEquals(9L, data.get("lastSeenUid"));
    }

    @Test
    void testReaderStatusReturnsConfigAndBaseline() {
        when(mailReceiver.isConfigured()).thenReturn(true);
        when(mailReceiver.getLastSeenUid()).thenReturn(42L);
        Result result = controller.readerStatus();
        java.util.Map<?, ?> data = (java.util.Map<?, ?>) result.getData();
        assertEquals(42L, data.get("lastSeenUid"));
        assertTrue(data.containsKey("host"));
        assertTrue(data.containsKey("pollIntervalMs"));
    }

    @Test
    void testResetBaselineReturnsSuccess() {
        assertEquals(Boolean.TRUE, controller.resetBaseline().getSuccess());
    }
}
