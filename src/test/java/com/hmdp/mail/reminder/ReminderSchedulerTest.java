package com.hmdp.mail.reminder;

import com.hmdp.mail.MailAlert;
import com.hmdp.mail.MailAlertPipelineService;
import com.hmdp.mail.MailAlertProperties;
import com.hmdp.mail.MailLevel;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
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
 * 日程提醒调度器测试。
 *
 * @author ethan
 * @date 2026-11-06
 */
class ReminderSchedulerTest {

    private final ReminderStore reminderStore = mock(ReminderStore.class);

    private final MailAlertPipelineService pipelineService = mock(MailAlertPipelineService.class);

    private final MailAlertProperties properties = new MailAlertProperties();

    /**
     * 构造调度器。
     *
     * @return 调度器
     */
    private ReminderScheduler scheduler() {
        return new ReminderScheduler(reminderStore, pipelineService, properties);
    }

    /**
     * 构造到期提醒。
     *
     * @param level 级别
     * @return 提醒
     */
    private Reminder due(MailLevel level) {
        return new Reminder("r1", "检查库存", "超卖风险", LocalDateTime.now(),
                level, Reminder.SOURCE_MANUAL, Reminder.STATUS_FIRED, LocalDateTime.now());
    }

    @Test
    void testPollDisabledSkipsClaim() {
        properties.getReminder().setEnabled(false);
        scheduler().poll();
        verify(reminderStore, never()).claimDue(any());
    }

    @Test
    void testPollDispatchesDueReminders() {
        properties.getReminder().setEnabled(true);
        when(reminderStore.claimDue(any())).thenReturn(Collections.singletonList(due(MailLevel.CRITICAL)));

        scheduler().poll();

        ArgumentCaptor<MailAlert> captor = ArgumentCaptor.forClass(MailAlert.class);
        verify(pipelineService).dispatchDirect(captor.capture());
        assertEquals(MailLevel.CRITICAL, captor.getValue().getLevel());
        assertTrue(captor.getValue().displayTitle().contains("检查库存"));
        assertEquals("reminder-r1", captor.getValue().getMail().getMessageKey());
    }

    @Test
    void testPollNullLevelDefaultsToWarning() {
        properties.getReminder().setEnabled(true);
        Reminder noLevel = due(null);
        when(reminderStore.claimDue(any())).thenReturn(Collections.singletonList(noLevel));

        scheduler().poll();

        ArgumentCaptor<MailAlert> captor = ArgumentCaptor.forClass(MailAlert.class);
        verify(pipelineService).dispatchDirect(captor.capture());
        assertEquals(MailLevel.WARNING, captor.getValue().getLevel());
    }

    @Test
    void testPollWithoutDueSkipsDispatch() {
        properties.getReminder().setEnabled(true);
        when(reminderStore.claimDue(any())).thenReturn(Collections.emptyList());

        scheduler().poll();

        verify(pipelineService, never()).dispatchDirect(any());
    }

    @Test
    void testPollStoreExceptionDoesNotPropagate() {
        properties.getReminder().setEnabled(true);
        when(reminderStore.claimDue(any())).thenThrow(new RuntimeException("redis down"));

        // 调度任务异常必须被吞掉，不允许中断调度线程
        scheduler().poll();

        verify(pipelineService, never()).dispatchDirect(any());
    }

    @Test
    void testPollDispatchesEachDueReminder() {
        properties.getReminder().setEnabled(true);
        Reminder first = due(MailLevel.WARNING);
        Reminder second = new Reminder("r2", "开会", null, LocalDateTime.now(),
                MailLevel.INFO, Reminder.SOURCE_MAIL, Reminder.STATUS_FIRED, LocalDateTime.now());
        when(reminderStore.claimDue(any())).thenReturn(java.util.Arrays.asList(first, second));

        scheduler().poll();

        verify(pipelineService, org.mockito.Mockito.times(2)).dispatchDirect(any());
    }
}
