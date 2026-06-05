package com.hmdp.service.chat;

import com.hmdp.dto.chat.ChatMessage;
import java.util.List;

public interface ContextManager {
    List<ChatMessage> loadContext(String sessionId);
    void saveMessage(String sessionId, ChatMessage message);
    void clearSession(String sessionId);
    String tryLock(String sessionId, long timeoutMs);
    void unlock(String sessionId, String lockValue);
}
