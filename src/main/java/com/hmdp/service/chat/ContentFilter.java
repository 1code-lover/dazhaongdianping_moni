package com.hmdp.service.chat;

public interface ContentFilter {
    boolean isUserInputSafe(String input);
    boolean isAiOutputSafe(String output);
    boolean detectPromptInjection(String input);
}
