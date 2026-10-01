package com.hmdp.service.verify;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * 邮箱验证码发送器
 * 通过 SMTP 将验证码发送到目标邮箱；发件账号与授权码由用户在配置中填写
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.verify-code.channel", havingValue = "email")
public class EmailVerifyCodeSender implements VerifyCodeSender {

    @Resource
    private JavaMailSender javaMailSender;

    /**
     * 发件人地址（通常与 spring.mail.username 一致）
     */
    @Value("${app.mail.from:${spring.mail.username:}}")
    private String from;

    /**
     * 当前实现对应的通道类型
     *
     * @return 通道枚举
     */
    @Override
    public VerifyCodeChannel channel() {
        return VerifyCodeChannel.EMAIL;
    }

    /**
     * 发送验证码邮件
     *
     * @param target 接收方邮箱
     * @param code   6 位数字验证码
     * @throws Exception SMTP 发送失败时抛出，由调用方统一提示
     */
    @Override
    public void send(String target, String code) throws Exception {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(target);
        message.setSubject("【黑马点评】登录验证码");
        message.setText("您的登录验证码为：" + code + "，2 分钟内有效。若非本人操作，请忽略本邮件。");
        javaMailSender.send(message);
        log.info("【验证码】已通过邮件发送至 {}，code={}", target, code);
    }
}
