package com.hmdp.mail;

import lombok.Data;

/**
 * Mock 邮件造数请求体。
 *
 * @author ethan
 * @date 2026-10-31
 */
@Data
public class MailCreateRequest {

    /** 发件人，空则使用默认值 */
    private String from;

    /** 邮件主题 */
    private String subject;

    /** 邮件正文 */
    private String content;
}
