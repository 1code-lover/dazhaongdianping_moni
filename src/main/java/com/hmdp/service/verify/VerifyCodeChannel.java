package com.hmdp.service.verify;

import cn.hutool.core.util.StrUtil;
import com.hmdp.utils.RegexUtils;

/**
 * 验证码下发通道枚举
 * 用于区分验证码发送到手机号（预留短信）还是邮箱
 */
public enum VerifyCodeChannel {

    /**
     * 手机号通道（当前仅支持日志输出，预留短信接入）
     */
    PHONE,
    /**
     * 邮箱通道（SMTP 免费发送）
     */
    EMAIL;

    /**
     * 根据登录标识自动识别通道类型
     *
     * @param target 登录标识（手机号或邮箱）
     * @return 识别出的通道；格式均不符时返回 null
     */
    public static VerifyCodeChannel detect(String target) {
        if (StrUtil.isBlank(target)) {
            return null;
        }
        // 邮箱格式优先判断（手机号正则不会误中邮箱）
        if (!RegexUtils.isEmailInvalid(target)) {
            return EMAIL;
        }
        if (!RegexUtils.isPhoneInvalid(target)) {
            return PHONE;
        }
        return null;
    }
}
