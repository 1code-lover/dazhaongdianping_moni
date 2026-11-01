package com.hmdp.mail;

/**
 * 邮件告警级别枚举。
 * 级别决定参与通知的渠道集合，CRITICAL 最高。
 *
 * @author ethan
 * @date 2026-10-31
 */
public enum MailLevel {

    /** 严重：资损、超卖、宕机等，弹窗 + 钉钉 + 手机闹钟 */
    CRITICAL,

    /** 警告：积压、慢查询等，弹窗 + 钉钉 */
    WARNING,

    /** 信息：普通通知，仅弹窗 */
    INFO
}
