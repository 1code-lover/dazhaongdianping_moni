package com.hmdp.mail;

import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

/**
 * SSE 广播器测试。
 * 无 servlet 容器环境下未初始化的 SseEmitter 发送只缓冲不抛异常，
 * 因此失败路径通过注入 mock 连接触发。
 *
 * @author ethan
 * @date 2026-10-31
 */
class SseAlertBroadcasterTest {

    private final SseAlertBroadcaster broadcaster = new SseAlertBroadcaster();

    @Test
    void testRegisterIncreasesOnlineCount() {
        broadcaster.register();
        assertEquals(1, broadcaster.onlineCount());
    }

    @Test
    void testBroadcastWithoutClientsIsSafe() {
        // 无订阅者时广播不应抛异常
        broadcaster.broadcast("mail-alert", "payload");
        assertEquals(0, broadcaster.onlineCount());
    }

    @Test
    void testBroadcastSendFailureRemovesClient() throws Exception {
        // 发送抛异常视为连接断开，应从订阅表中移除
        SseEmitter broken = mock(SseEmitter.class);
        doThrow(new java.io.IOException("broken pipe")).when(broken).send(
                org.mockito.ArgumentMatchers.any(SseEmitter.SseEventBuilder.class));
        broadcaster.addClient(broken);

        broadcaster.broadcast("mail-alert", "payload");

        assertEquals(0, broadcaster.onlineCount());
    }

    @Test
    void testBroadcastSuccessKeepsClient() {
        // 正常 mock 连接发送不抛异常，保持在线
        SseEmitter healthy = mock(SseEmitter.class);
        broadcaster.addClient(healthy);

        broadcaster.broadcast("mail-alert", "payload");

        assertEquals(1, broadcaster.onlineCount());
    }

    @Test
    void testAddClientReturnsSameEmitter() {
        SseEmitter emitter = new SseEmitter(0L);
        assertEquals(emitter, broadcaster.addClient(emitter));
    }
}
