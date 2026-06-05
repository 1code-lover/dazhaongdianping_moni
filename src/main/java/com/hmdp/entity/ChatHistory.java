package com.hmdp.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("chat_history")
public class ChatHistory {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String sessionId;
    private Long userId;
    private String role;
    private String content;
    private String intent;
    private Integer tokens;
    private Integer duration;
    private LocalDateTime createTime;
}
