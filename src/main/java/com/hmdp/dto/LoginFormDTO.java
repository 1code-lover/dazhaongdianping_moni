package com.hmdp.dto;

import lombok.Data;

/**
 * 登录表单DTO
 * 支持手机号或邮箱验证码登录；target 为新字段，phone 保留兼容旧调用方
 */
@Data
public class LoginFormDTO {
    /**
     * 登录标识：手机号或邮箱（优先取值）
     */
    private String target;
    /**
     * 手机号（兼容旧前端，target 为空时使用）
     */
    private String phone;
    private String code;
    private String password;
}
