package com.hmdp.mail.channel;

import com.hmdp.mail.ChannelResult;
import com.hmdp.mail.MailAlert;
import com.hmdp.mail.MailAlertProperties;
import com.hmdp.mail.MailLevel;
import com.hmdp.mail.NotificationChannel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 钉钉群机器人通知渠道。
 * 通过群机器人 webhook 推送文本消息，未配置 webhook 时自动跳过。
 *
 * @author ethan
 * @date 2026-10-31
 */
@Slf4j
@Component
public class DingTalkNotificationChannel implements NotificationChannel {

    private final MailAlertProperties properties;

    private final RestTemplate restTemplate;

    /**
     * 创建钉钉渠道。
     *
     * @param properties 告警配置
     */
    public DingTalkNotificationChannel(MailAlertProperties properties) {
        this.properties = properties;
        this.restTemplate = new RestTemplate();
    }

    /**
     * 渠道名称。
     *
     * @return dingtalk
     */
    @Override
    public String name() {
        return "dingtalk";
    }

    /**
     * 仅 CRITICAL 与 WARNING 级别推送钉钉。
     *
     * @param level 告警级别
     * @return 是否支持
     */
    @Override
    public boolean supports(MailLevel level) {
        return level == MailLevel.CRITICAL || level == MailLevel.WARNING;
    }

    /**
     * 推送文本消息到钉钉群。
     *
     * @param alert 告警事件
     * @return SENT 发送成功；SKIPPED 未配置 webhook；FAILED 发送失败
     */
    @Override
    public ChannelResult notify(MailAlert alert) {
        MailAlertProperties.DingTalk config = properties.getDingTalk();
        if (!config.isEnabled() || config.getWebhook() == null || config.getWebhook().trim().isEmpty()) {
            return ChannelResult.SKIPPED;
        }
        restTemplate.postForObject(config.getWebhook(), buildRequest(alert), String.class);
        log.info("钉钉提醒已发送: {}", alert.displayTitle());
        return ChannelResult.SENT;
    }

    /**
     * 构造钉钉机器人文本消息请求体。
     *
     * @param alert 告警事件
     * @return HTTP 请求实体
     */
    private HttpEntity<Map<String, Object>> buildRequest(MailAlert alert) {
        Map<String, Object> text = new LinkedHashMap<>();
        text.put("content", alert.displayTitle() + "\n" + alert.getMail().getContent());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("msgtype", "text");
        body.put("text", text);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, headers);
    }
}
