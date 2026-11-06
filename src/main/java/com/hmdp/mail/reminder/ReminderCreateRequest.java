package com.hmdp.mail.reminder;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 日程提醒创建请求体。
 * remindAt 与 delayMinutes 二选一，都提供时优先 remindAt。
 *
 * @author ethan
 * @date 2026-11-06
 */
@Data
public class ReminderCreateRequest {

    /** 提醒标题 */
    private String title;

    /** 补充说明 */
    private String content;

    /** 触发时间，ISO 格式如 2026-11-06T15:00 */
    private LocalDateTime remindAt;

    /** 延迟分钟数（与 remindAt 二选一） */
    private Long delayMinutes;

    /** 级别名称，空则默认 WARNING */
    private String level;
}
