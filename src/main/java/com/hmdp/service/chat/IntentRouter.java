package com.hmdp.service.chat;

import com.hmdp.enums.IntentType;

public interface IntentRouter {
    IntentType classify(String userMessage);
}
