package com.hmdp.dto.chat;

import lombok.Data;
import java.util.List;

@Data
public class ChatResponse {
    private String sessionId;
    private String reply;
    private String intent;
    private List<String> suggestions;
}
