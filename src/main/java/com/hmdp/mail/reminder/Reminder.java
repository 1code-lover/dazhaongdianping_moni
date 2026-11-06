package com.hmdp.mail.reminder;

import com.hmdp.mail.MailLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 日程提醒模型。
 * 存入 Redis 的提醒任务，到期后包装为 MailAlert 走既有通知渠道分发。
 *
 * @author ethan
 * @date 2026-11-06
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Reminder {

    /** 状态：待触发 */
    public static final String STATUS_PENDING = "PENDING";

    /** 状态：已触发 */
    public static final String STATUS_FIRED = "FIRED";

    /** 状态：已取消 */
    public static final String STATUS_CANCELLED = "CANCELLED";

    /** 来源：接口手动创建 */
    public static final String SOURCE_MANUAL = "MANUAL";

    /** 来源：邮件解析创建 */
    public static final String SOURCE_MAIL = "MAIL";

    /** 唯一标识 */
    private String id;

    /** 提醒标题 */
    private String title;

    /** 补充说明 */
    private String content;

    /** 触发时间 */
    private LocalDateTime remindAt;

    /** 级别，决定通知渠道路由 */
    private MailLevel level;

    /** 来源 */
    private String source;

    /** 状态 */
    private String status;

    /** 创建时间 */
    private LocalDateTime createdAt;
}
