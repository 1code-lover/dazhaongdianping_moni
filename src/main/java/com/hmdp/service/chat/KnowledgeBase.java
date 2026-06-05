package com.hmdp.service.chat;

import java.util.List;

public interface KnowledgeBase {
    String getSystemPrompt();
    List<String> searchRelevantKnowledge(String query, String method);
}
