package com.hmdp.service.impl.chat;

import com.hmdp.service.chat.ContentFilter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

@Slf4j
@Service
public class ContentFilterImpl implements ContentFilter {

    @Value("${ai.security.sensitive-words:政治,暴力,色情,赌博,毒品}")
    private String sensitiveWordsConfig;

    @Value("${ai.security.prompt-injection-protection:true}")
    private boolean promptInjectionProtection;

    @Value("${ai.chat.max-message-length:500}")
    private Integer maxMessageLength;

    private static final List<Pattern> INJECTION_PATTERNS = Arrays.asList(
            Pattern.compile("(?i)ignore\\s+(previous|above|all)\\s+instructions"),
            Pattern.compile("(?i)you\\s+are\\s+now\\s+"),
            Pattern.compile("(?i)system\\s*:\\s*"),
            Pattern.compile("(?i)forget\\s+(everything|all)"),
            Pattern.compile("(?i)new\\s+instructions"),
            Pattern.compile("(?i)override\\s+"),
            Pattern.compile("(?i)忽略.*指令"),
            Pattern.compile("(?i)你现在是"),
            Pattern.compile("(?i)系统\\s*[:：]")
    );

    @Override
    public boolean isUserInputSafe(String input) {
        if (input == null || input.trim().isEmpty()) {
            return false;
        }

        if (input.length() > maxMessageLength) {
            log.warn("用户输入超过长度限制: {}", input.length());
            return false;
        }

        if (promptInjectionProtection && detectPromptInjection(input)) {
            log.warn("检测到Prompt注入尝试: {}", input);
            return false;
        }

        List<String> sensitiveWords = Arrays.asList(sensitiveWordsConfig.split(","));
        for (String word : sensitiveWords) {
            if (input.contains(word.trim())) {
                log.warn("检测到敏感词: {}", word);
                return false;
            }
        }

        return true;
    }

    @Override
    public boolean isAiOutputSafe(String output) {
        if (output == null) {
            return false;
        }

        List<String> sensitiveWords = Arrays.asList(sensitiveWordsConfig.split(","));
        for (String word : sensitiveWords) {
            if (output.contains(word.trim())) {
                log.warn("AI输出包含敏感词: {}", word);
                return false;
            }
        }

        return true;
    }

    @Override
    public boolean detectPromptInjection(String input) {
        if (input == null) {
            return false;
        }

        for (Pattern pattern : INJECTION_PATTERNS) {
            if (pattern.matcher(input).find()) {
                return true;
            }
        }

        return false;
    }
}
