package com.hmdp.mail;

/**
 * 通知渠道接口。
 * 每个渠道声明自己支持的级别，由管道按级别路由调用。
 *
 * @author ethan
 * @date 2026-10-31
 */
public interface NotificationChannel {

    /**
     * 渠道名称，用于结果汇总展示。
     *
     * @return 渠道名
     */
    String name();

    /**
     * 是否支持该级别。
     *
     * @param level 告警级别
     * @return true 表示参与通知
     */
    boolean supports(MailLevel level);

    /**
     * 发送告警。
     *
     * @param alert 告警事件
     * @return 执行结果：SENT / SKIPPED / FAILED
     */
    ChannelResult notify(MailAlert alert);
}
