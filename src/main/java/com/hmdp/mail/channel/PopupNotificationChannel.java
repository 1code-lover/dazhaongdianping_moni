package com.hmdp.mail.channel;

import com.hmdp.mail.ChannelResult;
import com.hmdp.mail.MailAlert;
import com.hmdp.mail.MailLevel;
import com.hmdp.mail.NotificationChannel;
import com.hmdp.mail.SseAlertBroadcaster;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 浏览器弹窗通知渠道。
 * 通过 SSE 将告警广播到前端 demo 页，由浏览器 Notification API 真实弹出。
 *
 * @author ethan
 * @date 2026-10-31
 */
@Slf4j
@Component
public class PopupNotificationChannel implements NotificationChannel {

    private static final String EVENT_NAME = "mail-alert";

    private final SseAlertBroadcaster broadcaster;

    /**
     * 创建弹窗渠道。
     *
     * @param broadcaster SSE 广播器
     */
    public PopupNotificationChannel(SseAlertBroadcaster broadcaster) {
        this.broadcaster = broadcaster;
    }

    /**
     * 渠道名称。
     *
     * @return popup
     */
    @Override
    public String name() {
        return "popup";
    }

    /**
     * 弹窗支持所有级别。
     *
     * @param level 告警级别
     * @return 恒为 true
     */
    @Override
    public boolean supports(MailLevel level) {
        return true;
    }

    /**
     * 广播告警到浏览器。
     *
     * @param alert 告警事件
     * @return 已发送
     */
    @Override
    public ChannelResult notify(MailAlert alert) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("level", alert.getLevel().name());
        payload.put("title", alert.displayTitle());
        payload.put("content", alert.getMail().getContent());
        payload.put("from", alert.getMail().getFrom());
        payload.put("time", alert.getAlertTime().toString());
        broadcaster.broadcast(EVENT_NAME, payload);
        log.info("弹窗提醒已广播: {}", alert.displayTitle());
        return ChannelResult.SENT;
    }
}
