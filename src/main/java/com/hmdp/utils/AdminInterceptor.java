package com.hmdp.utils;

import com.hmdp.dto.UserDTO;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * 管理员权限拦截器。
 * 通过配置的管理员用户 ID 白名单保护平台审核和失败任务运维接口。
 *
 * @author ethan
 * @date 2026-07-16
 */
public class AdminInterceptor implements HandlerInterceptor {

    private final Set<Long> adminUserIds;

    /**
     * 创建管理员权限拦截器。
     *
     * @param configuredAdminUserIds 逗号分隔的管理员用户 ID
     */
    public AdminInterceptor(String configuredAdminUserIds) {
        this.adminUserIds = parseAdminUserIds(configuredAdminUserIds);
    }

    /**
     * 校验当前登录用户是否属于管理员白名单。
     *
     * @param request 当前请求
     * @param response 当前响应
     * @param handler 请求处理器
     * @return 具备管理员权限时返回 true，否则返回 false
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return false;
        }
        if (user.getId() == null || !adminUserIds.contains(user.getId())) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            return false;
        }
        return true;
    }

    /**
     * 将管理员用户 ID 配置解析为不可变集合。
     * 空配置返回空集合，使后台接口默认拒绝访问。
     *
     * @param configuredAdminUserIds 逗号分隔的管理员用户 ID
     * @return 管理员用户 ID 集合
     */
    private Set<Long> parseAdminUserIds(String configuredAdminUserIds) {
        if (configuredAdminUserIds == null || configuredAdminUserIds.trim().isEmpty()) {
            return Collections.emptySet();
        }
        Set<Long> userIds = new HashSet<>();
        String[] values = configuredAdminUserIds.split(",");
        for (String value : values) {
            String trimmedValue = value.trim();
            if (trimmedValue.isEmpty()) {
                continue;
            }
            try {
                userIds.add(Long.valueOf(trimmedValue));
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException("管理员用户 ID 配置非法: " + trimmedValue, exception);
            }
        }
        return Collections.unmodifiableSet(userIds);
    }
}
