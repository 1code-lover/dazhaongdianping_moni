package com.hmdp.mail.channel;

import com.hmdp.mail.ChannelResult;
import com.hmdp.mail.MailAlert;
import com.hmdp.mail.MailAlertProperties;
import com.hmdp.mail.MailLevel;
import com.hmdp.mail.NotificationChannel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * 邮件发信通知渠道。
 * 通过 SMTP 将告警作为真实邮件发送到配置的收件邮箱，与弹窗、钉钉、手机闹钟并列。
 * SMTP 连接参数（host/port/username/授权码）在 spring.mail 配置中读取，授权码走环境变量 MAIL_AUTH_CODE。
 *
 * @author ethan
 * @date 2026-10-31
 */
@Slf4j
@Component
public class MailNotificationChannel implements NotificationChannel {

    private final JavaMailSender mailSender;

    private final MailAlertProperties properties;

    /**
     * 创建邮件发信渠道。
     *
     * @param mailSender Spring 邮件发送器
     * @param properties 告警配置
     */
    public MailNotificationChannel(JavaMailSender mailSender, MailAlertProperties properties) {
        this.mailSender = mailSender;
        this.properties = properties;
    }

    /**
     * 渠道名称。
     *
     * @return mail
     */
    @Override
    public String name() {
        return "mail";
    }

    /**
     * 仅 CRITICAL 与 WARNING 级别发信。
     *
     * @param level 告警级别
     * @return 是否支持
     */
    @Override
    public boolean supports(MailLevel level) {
        return level == MailLevel.CRITICAL || level == MailLevel.WARNING;
    }

    /**
     * 发送告警邮件。
     * 未配置账号或收件人时跳过；发送异常由管道统一隔离为 FAILED。
     *
     * @param alert 告警事件
     * @return SENT 发送成功；SKIPPED 未配置完整；FAILED 发送失败
     */
    @Override
    public ChannelResult notify(MailAlert alert) {
        MailAlertProperties.MailSenderProps config = properties.getMailSender();
        String username = propertiesContextUsername();
        if (!config.isEnabled() || isBlank(username) || isBlank(config.getTo())) {
            return ChannelResult.SKIPPED;
        }
        mailSender.send(buildMessage(alert, username));
        log.info("告警邮件已发送: {} -> {}", alert.displayTitle(), config.getTo());
        return ChannelResult.SENT;
    }

    /**
     * 构造纯文本告警邮件。
     * 发件人必须与 SMTP 认证账号一致（网易等服务商强制校验）。
     *
     * @param alert    告警事件
     * @param username 发件邮箱（SMTP 认证账号）
     * @return 邮件消息
     */
    private SimpleMailMessage buildMessage(MailAlert alert, String username) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(username);
        message.setTo(properties.getMailSender().getTo());
        message.setSubject(alert.displayTitle());
        message.setText(alert.getMail().getContent()
                + "\n\n来自: " + alert.getMail().getFrom()
                + "\n告警时间: " + alert.getAlertTime());
        return message;
    }

    /**
     * 读取 SMTP 认证账号。
     * JavaMailSenderImpl 未初始化或未配置用户名时返回空串。
     *
     * @return SMTP 用户名，可能为空
     */
    private String propertiesContextUsername() {
        if (mailSender instanceof org.springframework.mail.javamail.JavaMailSenderImpl) {
            return ((org.springframework.mail.javamail.JavaMailSenderImpl) mailSender).getUsername();
        }
        return "";
    }

    /**
     * 判断字符串是否为空白。
     *
     * @param value 待检查字符串
     * @return true 表示空白
     */
    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
