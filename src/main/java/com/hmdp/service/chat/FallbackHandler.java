package com.hmdp.service.chat;

import com.hmdp.enums.IntentType;

public interface FallbackHandler {
    String handleFallback(String userMessage, IntentType intent);
}
