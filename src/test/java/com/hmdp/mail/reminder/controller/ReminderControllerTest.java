package com.hmdp.mail.reminder.controller;

import com.hmdp.dto.Result;
import com.hmdp.mail.MailLevel;
import com.hmdp.mail.reminder.Reminder;
import com.hmdp.mail.reminder.ReminderCreateRequest;
import com.hmdp.mail.reminder.ReminderStore;
import com.hmdp.mail.reminder.ReminderTimeParser;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 日程提醒控制器测试。
 *
 * @author ethan
 * @date 2026-11-06
 */
class ReminderControllerTest {

    private final ReminderStore reminderStore = mock(ReminderStore.class);

    private final ReminderController controller =
            new ReminderController(reminderStore, new ReminderTimeParser());

    @Test
    void testCreateWithDelayMinutesComputesRemindAt() {
        LocalDateTime before = LocalDateTime.now();
        ReminderCreateRequest request = new ReminderCreateRequest();
        request.setTitle("检查库存");
        request.setDelayMinutes(60L);

        Result result = controller.create(request);

        assertEquals(Boolean.TRUE, result.getSuccess());
        ArgumentCaptor<Reminder> captor = ArgumentCaptor.forClass(Reminder.class);
        verify(reminderStore).save(captor.capture());
        Reminder saved = captor.getValue();
        assertTrue(saved.getRemindAt().isAfter(before.plusMinutes(59)));
        assertTrue(saved.getRemindAt().isBefore(before.plusMinutes(61)));
        assertEquals(MailLevel.WARNING, saved.getLevel());
        assertEquals(Reminder.SOURCE_MANUAL, saved.getSource());
        assertEquals(Reminder.STATUS_PENDING, saved.getStatus());
    }

    @Test
    void testCreateWithExplicitRemindAtAndLevel() {
        LocalDateTime remindAt = LocalDateTime.now().plusDays(1);
        ReminderCreateRequest request = new ReminderCreateRequest();
        request.setTitle("上线检查");
        request.setRemindAt(remindAt);
        request.setLevel("critical");

        controller.create(request);

        ArgumentCaptor<Reminder> captor = ArgumentCaptor.forClass(Reminder.class);
        verify(reminderStore).save(captor.capture());
        assertEquals(remindAt, captor.getValue().getRemindAt());
        assertEquals(MailLevel.CRITICAL, captor.getValue().getLevel());
    }

    @Test
    void testCreateWithoutTimeReturnsFail() {
        ReminderCreateRequest request = new ReminderCreateRequest();
        request.setTitle("无时间提醒");

        Result result = controller.create(request);

        assertEquals(Boolean.FALSE, result.getSuccess());
        verify(reminderStore, never()).save(any());
    }

    @Test
    void testCreateInvalidLevelFallsBackToWarning() {
        ReminderCreateRequest request = new ReminderCreateRequest();
        request.setTitle("级别非法");
        request.setDelayMinutes(5L);
        request.setLevel("NOT-A-LEVEL");

        controller.create(request);

        ArgumentCaptor<Reminder> captor = ArgumentCaptor.forClass(Reminder.class);
        verify(reminderStore).save(captor.capture());
        assertEquals(MailLevel.WARNING, captor.getValue().getLevel());
    }

    @Test
    void testListReturnsStoreEntries() {
        Reminder reminder = new Reminder("r1", "标题", null, LocalDateTime.now().plusMinutes(5),
                MailLevel.INFO, Reminder.SOURCE_MANUAL, Reminder.STATUS_PENDING, LocalDateTime.now());
        when(reminderStore.listAll()).thenReturn(Collections.singletonList(reminder));

        Result result = controller.list();

        assertEquals(Boolean.TRUE, result.getSuccess());
        assertEquals(1, ((List<?>) result.getData()).size());
    }

    @Test
    void testCancelExistingReturnsSuccess() {
        when(reminderStore.cancel("r1")).thenReturn(true);
        assertEquals(Boolean.TRUE, controller.cancel("r1").getSuccess());
    }

    @Test
    void testCancelMissingReturnsFail() {
        when(reminderStore.cancel("rX")).thenReturn(false);
        assertEquals(Boolean.FALSE, controller.cancel("rX").getSuccess());
    }

    @Test
    void testParseTextWithTimeCreatesReminder() {
        Map<String, String> payload = new HashMap<>();
        payload.put("text", "30分钟后检查库存");

        Result result = controller.parseAndCreate(payload);

        assertEquals(Boolean.TRUE, result.getSuccess());
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) result.getData();
        assertTrue(data.containsKey("parsedTime"));
        verify(reminderStore).save(any());
    }

    @Test
    void testParseTextWithoutTimeReturnsFail() {
        Map<String, String> payload = new HashMap<>();
        payload.put("text", "你好世界");

        Result result = controller.parseAndCreate(payload);

        assertEquals(Boolean.FALSE, result.getSuccess());
        verify(reminderStore, never()).save(any());
    }
}
