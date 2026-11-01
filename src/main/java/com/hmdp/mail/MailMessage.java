package com.hmdp.mail;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 邮件消息模型。
 * Mock 邮件源与未来真实邮件接入共用该模型，messageKey 用于链路去重。
 *
 * @author ethan
 * @date 2026-10-31
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MailMessage {

    /** 邮件唯一键，重复投递只告警一次 */
    private String messageKey;

    /** 发件人 */
    private String from;

    /** 邮件主题 */
    private String subject;

    /** 邮件正文 */
    private String content;

    /** 收件时间 */
    private LocalDateTime receivedAt;
}
