package com.hmdp.mail;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 分级后的告警事件。
 * 由邮件经分级器产出，是通知渠道的统一输入。
 *
 * @author ethan
 * @date 2026-10-31
 */
@Data
@AllArgsConstructor
public class MailAlert {

    /** 原始邮件 */
    private MailMessage mail;

    /** 告警级别 */
    private MailLevel level;

    /** 告警产生时间 */
    private LocalDateTime alertTime;

    /**
     * 生成展示用标题，形如 [CRITICAL] 库存超卖告警。
     *
     * @return 标题文本
     */
    public String displayTitle() {
        return "[" + level.name() + "] " + mail.getSubject();
    }
}
