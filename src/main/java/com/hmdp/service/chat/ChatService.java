package com.hmdp.service.chat;

import com.hmdp.dto.Result;
import com.hmdp.dto.chat.ChatRequest;

public interface ChatService {
    Result send(ChatRequest request);
    Result getHistory(String sessionId);
    Result clearSession(String sessionId);
}
