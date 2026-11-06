package com.hmdp.mail.reminder;

import com.hmdp.mail.MailAlert;
import com.hmdp.mail.MailAlertPipelineService;
import com.hmdp.mail.MailAlertProperties;
import com.hmdp.mail.MailLevel;
import com.hmdp.mail.MailMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 日程提醒到期调度器。
 * 按配置间隔轮询延迟队列，抢到到期提醒后包装为告警事件走既有渠道分发。
 *
 * @author ethan
 * @date 2026-11-06
 */
@Slf4j
@Component
public class ReminderScheduler {

    private static final String REMINDER_FROM = "reminder-system@hmdp.example";

    private final ReminderStore reminderStore;

    private final MailAlertPipelineService pipelineService;

    private final MailAlertProperties properties;

    /**
     * 创建日程提醒调度器。
     *
     * @param reminderStore   提醒存储
     * @param pipelineService 告警管道
     * @param properties      告警配置
     */
    public ReminderScheduler(ReminderStore reminderStore,
                             MailAlertPipelineService pipelineService,
                             MailAlertProperties properties) {
        this.reminderStore = reminderStore;
        this.pipelineService = pipelineService;
        this.properties = properties;
    }

    /**
     * 轮询到期提醒并分发。
     * 间隔由 app.notify.reminder.poll-interval-ms 配置。
     */
    @Scheduled(fixedDelayString = "${app.notify.reminder.poll-interval-ms:30000}")
    public void poll() {
        if (!properties.getReminder().isEnabled()) {
            return;
        }
        try {
            List<Reminder> due = reminderStore.claimDue(LocalDateTime.now());
            for (Reminder reminder : due) {
                dispatch(reminder);
            }
        } catch (Exception ex) {
            // 轮询异常只记录，不允许中断调度
            log.warn("日程提醒轮询异常: {}", ex.getMessage());
        }
    }

    /**
     * 将提醒包装为告警事件并分发。
     *
     * @param reminder 到期提醒
     */
    private void dispatch(Reminder reminder) {
        MailMessage mail = new MailMessage(
                "reminder-" + reminder.getId(),
                REMINDER_FROM,
                "[日程提醒] " + reminder.getTitle(),
                reminder.getContent() == null ? reminder.getTitle() : reminder.getContent(),
                reminder.getRemindAt());
        MailAlert alert = new MailAlert(mail, levelOf(reminder), LocalDateTime.now());
        pipelineService.dispatchDirect(alert);
        log.info("日程提醒已触发: {} (级别 {})", reminder.getTitle(), alert.getLevel());
    }

    /**
     * 读取提醒级别，空值回退 WARNING。
     *
     * @param reminder 提醒
     * @return 告警级别
     */
    private MailLevel levelOf(Reminder reminder) {
        return reminder.getLevel() == null ? MailLevel.WARNING : reminder.getLevel();
    }
}
