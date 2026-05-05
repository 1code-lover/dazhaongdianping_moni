package com.hmdp.utils;

import cn.hutool.core.util.StrUtil;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.UserDTO;
import com.hmdp.enums.LimitType;
import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServletRequest;
import java.util.Map;

@Component
public class RateLimitKeyResolver {

    public String resolveKeySuffix(LimitType limitType, Object[] args, HttpServletRequest request) {
        switch (limitType) {
            case USER:
            case USER_AND_PATH:
                return resolveUserId();
            case PHONE:
                return resolvePhone(args);
            case IP:
            case IP_AND_PATH:
            default:
                return resolveIp(request);
        }
    }

    private String resolveUserId() {
        UserDTO user = UserHolder.getUser();
        if (user == null || user.getId() == null) {
            throw new IllegalArgumentException("未获取到登录用户，无法进行用户维度限流");
        }
        return user.getId().toString();
    }

    private String resolvePhone(Object[] args) {
        for (Object arg : args) {
            if (arg instanceof String && !RegexUtils.isPhoneInvalid((String) arg)) {
                return (String) arg;
            }
            if (arg instanceof LoginFormDTO) {
                String phone = ((LoginFormDTO) arg).getPhone();
                if (!RegexUtils.isPhoneInvalid(phone)) {
                    return phone;
                }
            }
            if (arg instanceof Map) {
                Object phone = ((Map<?, ?>) arg).get("phone");
                if (phone instanceof String && !RegexUtils.isPhoneInvalid((String) phone)) {
                    return (String) phone;
                }
            }
        }
        throw new IllegalArgumentException("未获取到手机号，无法进行手机号维度限流");
    }

    private String resolveIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (StrUtil.isNotBlank(forwardedFor)) {
            return forwardedFor.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (StrUtil.isNotBlank(realIp)) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }
}
