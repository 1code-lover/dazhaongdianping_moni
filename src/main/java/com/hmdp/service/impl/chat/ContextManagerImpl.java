package com.hmdp.service.impl.chat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hmdp.dto.chat.ChatMessage;
import com.hmdp.service.chat.ContextManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class ContextManagerImpl implements ContextManager {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Value("${ai.chat.max-history:10}")
    private Integer maxHistory;

    @Value("${ai.chat.session-timeout:3600}")
    private Integer sessionTimeout;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String SESSION_KEY_PREFIX = "chat:session:";
    private static final String LOCK_KEY_PREFIX = "chat:lock:";

    @Override
    public List<ChatMessage> loadContext(String sessionId) {
        String key = SESSION_KEY_PREFIX + sessionId;
        List<String> messages = stringRedisTemplate.opsForList().range(key, 0, -1);

        if (messages == null || messages.isEmpty()) {
            return new ArrayList<>();
        }

        List<ChatMessage> result = new ArrayList<>();
        for (String msg : messages) {
            try {
                result.add(objectMapper.readValue(msg, ChatMessage.class));
            } catch (JsonProcessingException e) {
                log.error("解析消息失败: {}", msg, e);
            }
        }
        return result;
    }

    @Override
    public void saveMessage(String sessionId, ChatMessage message) {
        String key = SESSION_KEY_PREFIX + sessionId;
        try {
            String json = objectMapper.writeValueAsString(message);
            stringRedisTemplate.opsForList().rightPush(key, json);

            Long size = stringRedisTemplate.opsForList().size(key);
            if (size != null && size > maxHistory) {
                stringRedisTemplate.opsForList().trim(key, size - maxHistory, size - 1);
            }

            stringRedisTemplate.expire(key, sessionTimeout, TimeUnit.SECONDS);
        } catch (JsonProcessingException e) {
            log.error("保存消息失败", e);
        }
    }

    @Override
    public void clearSession(String sessionId) {
        String key = SESSION_KEY_PREFIX + sessionId;
        stringRedisTemplate.delete(key);
    }

    @Override
    public String tryLock(String sessionId, long timeoutMs) {
        String key = LOCK_KEY_PREFIX + sessionId;
        String lockValue = UUID.randomUUID().toString();

        Boolean success = stringRedisTemplate.opsForValue()
                .setIfAbsent(key, lockValue, timeoutMs, TimeUnit.MILLISECONDS);

        if (Boolean.TRUE.equals(success)) {
            return lockValue;
        }
        return null;
    }

    @Override
    public void unlock(String sessionId, String lockValue) {
        String key = LOCK_KEY_PREFIX + sessionId;
        String currentValue = stringRedisTemplate.opsForValue().get(key);

        if (lockValue != null && lockValue.equals(currentValue)) {
            stringRedisTemplate.delete(key);
        }
    }
}
