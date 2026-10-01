package com.hmdp.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 登录验证码配置项
 * 对应配置文件 app.verify-code.*，控制下发通道与频控阈值
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.verify-code")
public class VerifyCodeProperties {

    /**
     * 下发通道：log（日志，默认）/ email（SMTP 邮件）；短信通道预留
     */
    private String channel = "log";

    /**
     * 同一登录标识的重发冷却时间（秒）
     */
    private long resendIntervalSeconds = 60;

    /**
     * 同一登录标识单日最大发送条数
     */
    private int maxPerTargetPerDay = 10;

    /**
     * 同一来源 IP 单日最大发送条数（防轰炸）
     */
    private int maxPerIpPerDay = 20;

    /**
     * 验证码最大校验错误次数，超过后验证码作废
     */
    private int maxVerifyAttempts = 5;
}
