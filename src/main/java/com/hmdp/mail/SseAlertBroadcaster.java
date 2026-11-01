package com.hmdp.mail;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 弹窗提醒 SSE 广播器。
 * 管理浏览器订阅连接，向所有在线客户端推送告警事件供前端弹窗展示。
 *
 * @author ethan
 * @date 2026-10-31
 */
@Slf4j
@Component
public class SseAlertBroadcaster {

    private static final long NEVER_TIMEOUT = 0L;

    /** 订阅连接表：key 为 emitter 身份，value 为连接 */
    private final Map<SseEmitter, SseEmitter> clients = new ConcurrentHashMap<>();

    /**
     * 注册一个新的浏览器订阅。
     *
     * @return SSE 连接，客户端直接返回给浏览器
     */
    public SseEmitter register() {
        return addClient(new SseEmitter(NEVER_TIMEOUT));
    }

    /**
     * 登记连接并挂接断开清理回调。
     * 包级可见以便测试注入模拟连接。
     *
     * @param emitter SSE 连接
     * @return 传入的连接
     */
    SseEmitter addClient(SseEmitter emitter) {
        clients.put(emitter, emitter);
        emitter.onCompletion(() -> clients.remove(emitter));
        emitter.onTimeout(() -> clients.remove(emitter));
        emitter.onError(ex -> clients.remove(emitter));
        return emitter;
    }

    /**
     * 向所有在线订阅广播告警事件。
     * 发送失败即视为连接断开，移除该连接。
     *
     * @param eventName 事件名
     * @param payload   事件数据
     */
    public void broadcast(String eventName, Object payload) {
        for (SseEmitter emitter : clients.keySet()) {
            try {
                emitter.send(SseEmitter.event().name(eventName).data(payload, MediaType.APPLICATION_JSON));
            } catch (IOException | IllegalStateException ex) {
                log.info("SSE 连接断开，移除订阅: {}", ex.getMessage());
                clients.remove(emitter);
            }
        }
    }

    /**
     * 查看当前在线订阅数。
     *
     * @return 在线连接数
     */
    public int onlineCount() {
        return clients.size();
    }
}
