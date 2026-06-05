package com.hmdp.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("chat_faq")
public class ChatFaq {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String question;
    private String answer;
    private String category;
    private Integer sort;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
