package com.hmdp.controller;

import com.hmdp.annotation.RateLimit;
import com.hmdp.dto.Result;
import com.hmdp.dto.chat.ChatRequest;
import com.hmdp.enums.LimitType;
import com.hmdp.service.chat.ChatService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

@RestController
@RequestMapping("/chat")
public class ChatController {

    @Resource
    private ChatService chatService;

    @PostMapping("/send")
    @RateLimit(
            keyPrefix = "chat:send:user",
            limitType = LimitType.USER,
            timeWindowSeconds = 1,
            maxRequests = 5,
            message = "消息发送过于频繁，请稍后再试"
    )
    public Result send(@RequestBody ChatRequest request) {
        return chatService.send(request);
    }

    @GetMapping("/history")
    public Result getHistory(@RequestParam String sessionId) {
        return chatService.getHistory(sessionId);
    }

    @DeleteMapping("/session")
    public Result clearSession(@RequestParam String sessionId) {
        return chatService.clearSession(sessionId);
    }
}
