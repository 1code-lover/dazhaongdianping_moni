package com.hmdp.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hmdp.dto.chat.ChatMessage;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class OpenAiClient implements AiClient {

    @Value("${ai.base-url:https://api.openai.com}")
    private String baseUrl;

    @Value("${ai.api-key:}")
    private String apiKey;

    @Value("${ai.model:gpt-3.5-turbo}")
    private String model;

    @Value("${ai.temperature:0.7}")
    private Double temperature;

    @Value("${ai.max-tokens:1000}")
    private Integer maxTokens;

    @Value("${ai.retry.max-attempts:3}")
    private Integer maxRetryAttempts;
    
    @Value("${ai.retry.initial-delay-ms:1000}")
    private Integer initialDelayMs;

    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build();

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String chat(List<ChatMessage> messages) {
        int attempts = 0;
        long delay = initialDelayMs;
        
        while (attempts < maxRetryAttempts) {
            try {
                String requestBody = buildRequestBody(messages);
                Request request = new Request.Builder()
                        .url(baseUrl + "/v1/chat/completions")
                        .addHeader("Authorization", "Bearer " + apiKey)
                        .addHeader("Content-Type", "application/json")
                        .post(RequestBody.create(requestBody, MediaType.parse("application/json")))
                        .build();

                try (Response response = client.newCall(request).execute()) {
                    if (!response.isSuccessful()) {
                        log.warn("AI API调用失败 (attempt {}): {}", attempts + 1, response.code());
                        attempts++;
                        if (attempts < maxRetryAttempts) {
                            Thread.sleep(delay);
                            delay *= 2;
                            continue;
                        }
                        return null;
                    }
                    
                    String responseBody = response.body().string();
                    return parseResponse(responseBody);
                }
            } catch (IOException | InterruptedException e) {
                log.warn("AI API调用异常 (attempt {}): {}", attempts + 1, e.getMessage());
                attempts++;
                if (attempts < maxRetryAttempts) {
                    try {
                        Thread.sleep(delay);
                        delay *= 2;
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return null;
                    }
                }
            }
        }
        
        log.error("AI API调用失败，已重试{}次", maxRetryAttempts);
        return null;
    }

    private String buildRequestBody(List<ChatMessage> messages) throws IOException {
        return objectMapper.writeValueAsString(new java.util.HashMap<String, Object>() {{
            put("model", model);
            put("messages", messages);
            put("temperature", temperature);
            put("max_tokens", maxTokens);
        }});
    }

    private String parseResponse(String responseBody) throws IOException {
        JsonNode root = objectMapper.readTree(responseBody);
        return root.path("choices").get(0).path("message").path("content").asText();
    }
}
