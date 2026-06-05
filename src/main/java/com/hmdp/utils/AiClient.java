package com.hmdp.utils;

import com.hmdp.dto.chat.ChatMessage;
import java.util.List;

public interface AiClient {
    String chat(List<ChatMessage> messages);
}
