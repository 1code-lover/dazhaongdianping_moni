package com.hmdp.mail.reminder.controller;

import com.hmdp.dto.Result;
import com.hmdp.mail.MailLevel;
import com.hmdp.mail.reminder.Reminder;
import com.hmdp.mail.reminder.ReminderCreateRequest;
import com.hmdp.mail.reminder.ReminderStore;
import com.hmdp.mail.reminder.ReminderTimeParser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 日程提醒接口。
 * 提供创建、列表、取消与邮件文本时间解析。
 *
 * @author ethan
 * @date 2026-11-06
 */
@Slf4j
@RestController
@RequestMapping("/mail-alert/reminders")
public class ReminderController {

    private final ReminderStore reminderStore;

    private final ReminderTimeParser timeParser;

    /**
     * 创建日程提醒控制器。
     *
     * @param reminderStore 提醒存储
     * @param timeParser    时间解析器
     */
    public ReminderController(ReminderStore reminderStore, ReminderTimeParser timeParser) {
        this.reminderStore = reminderStore;
        this.timeParser = timeParser;
    }

    /**
     * 创建日程提醒。
     *
     * @param request 创建请求
     * @return 创建结果
     */
    @PostMapping
    public Result create(@RequestBody ReminderCreateRequest request) {
        LocalDateTime remindAt = resolveRemindAt(request);
        if (remindAt == null) {
            return Result.fail("remindAt 与 delayMinutes 至少提供一个");
        }
        Reminder reminder = buildReminder(request, remindAt);
        reminderStore.save(reminder);
        log.info("日程提醒已创建: {} -> {}", reminder.getTitle(), reminder.getRemindAt());
        return Result.ok(reminder);
    }

    /**
     * 列出全部提醒。
     *
     * @return 提醒列表
     */
    @GetMapping
    public Result list() {
        return Result.ok(reminderStore.listAll());
    }

    /**
     * 取消提醒。
     *
     * @param id 提醒 id
     * @return 取消结果
     */
    @DeleteMapping("/{id}")
    public Result cancel(@PathVariable("id") String id) {
        boolean cancelled = reminderStore.cancel(id);
        return cancelled ? Result.ok("已取消") : Result.fail("提醒不存在");
    }

    /**
     * 从文本解析时间并直接创建提醒。
     *
     * @param payload 形如 {"text": "明天9点检查库存"} 的请求体
     * @return 解析结果与创建的提醒
     */
    @PostMapping("/parse")
    public Result parseAndCreate(@RequestBody Map<String, String> payload) {
        String text = payload == null ? null : payload.get("text");
        LocalDateTime time = timeParser.parse(text).orElse(null);
        if (time == null) {
            return Result.fail("未能从文本中解析出未来的时间");
        }
        Reminder reminder = new Reminder(
                UUID.randomUUID().toString(),
                text,
                text,
                time,
                MailLevel.WARNING,
                Reminder.SOURCE_MAIL,
                Reminder.STATUS_PENDING,
                LocalDateTime.now());
        reminderStore.save(reminder);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("parsedTime", time.toString());
        data.put("reminder", reminder);
        return Result.ok(data);
    }

    /**
     * 解析触发时间：优先 remindAt，其次 delayMinutes。
     *
     * @param request 创建请求
     * @return 触发时间，无法解析返回 null
     */
    private LocalDateTime resolveRemindAt(ReminderCreateRequest request) {
        if (request.getRemindAt() != null) {
            return request.getRemindAt();
        }
        if (request.getDelayMinutes() != null) {
            return LocalDateTime.now().plusMinutes(request.getDelayMinutes());
        }
        return null;
    }

    /**
     * 构造提醒对象。
     *
     * @param request  创建请求
     * @param remindAt 触发时间
     * @return 提醒
     */
    private Reminder buildReminder(ReminderCreateRequest request, LocalDateTime remindAt) {
        return new Reminder(
                UUID.randomUUID().toString(),
                request.getTitle() == null || request.getTitle().trim().isEmpty()
                        ? "未命名提醒" : request.getTitle(),
                request.getContent(),
                remindAt,
                levelOf(request.getLevel()),
                Reminder.SOURCE_MANUAL,
                Reminder.STATUS_PENDING,
                LocalDateTime.now());
    }

    /**
     * 解析级别名称，非法值回退 WARNING。
     *
     * @param level 级别名称
     * @return 告警级别
     */
    private MailLevel levelOf(String level) {
        if (level == null || level.trim().isEmpty()) {
            return MailLevel.WARNING;
        }
        try {
            return MailLevel.valueOf(level.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return MailLevel.WARNING;
        }
    }
}
