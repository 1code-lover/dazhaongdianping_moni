package com.hmdp.utils;

import com.hmdp.dto.UserDTO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 管理员权限拦截器单元测试。
 *
 * @author ethan
 * @date 2026-07-16
 */
class AdminInterceptorTest {

    /**
     * 每个测试结束后清理线程中的登录用户，避免用例相互污染。
     */
    @AfterEach
    void tearDown() {
        UserHolder.removeUser();
    }

    /**
     * 未登录访问管理员接口时应返回 401。
     */
    @Test
    void testPreHandleWhenUserMissingThenUnauthorized() {
        AdminInterceptor interceptor = new AdminInterceptor("1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean allowed = interceptor.preHandle(new MockHttpServletRequest(), response, new Object());

        assertFalse(allowed);
        assertEquals(401, response.getStatus());
    }

    /**
     * 普通登录用户访问管理员接口时应返回 403。
     */
    @Test
    void testPreHandleWhenUserIsNotAdminThenForbidden() {
        UserHolder.saveUser(createUser(2L));
        AdminInterceptor interceptor = new AdminInterceptor("1, 3");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean allowed = interceptor.preHandle(new MockHttpServletRequest(), response, new Object());

        assertFalse(allowed);
        assertEquals(403, response.getStatus());
    }

    /**
     * 白名单中的登录用户访问管理员接口时应放行。
     */
    @Test
    void testPreHandleWhenUserIsAdminThenAllowed() {
        UserHolder.saveUser(createUser(3L));
        AdminInterceptor interceptor = new AdminInterceptor("1, 3");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean allowed = interceptor.preHandle(new MockHttpServletRequest(), response, new Object());

        assertTrue(allowed);
        assertEquals(200, response.getStatus());
    }

    /**
     * 管理员白名单为空时应默认拒绝普通登录用户。
     */
    @Test
    void testPreHandleWhenAdminConfigEmptyThenForbidden() {
        UserHolder.saveUser(createUser(1L));
        AdminInterceptor interceptor = new AdminInterceptor("");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean allowed = interceptor.preHandle(new MockHttpServletRequest(), response, new Object());

        assertFalse(allowed);
        assertEquals(403, response.getStatus());
    }

    /**
     * 管理员 ID 配置不是数字时应在启动阶段快速失败。
     */
    @Test
    void testConstructorWhenAdminConfigInvalidThenThrowException() {
        assertThrows(IllegalArgumentException.class, () -> new AdminInterceptor("1,invalid"));
    }

    /**
     * 创建指定 ID 的测试用户。
     *
     * @param userId 用户 ID
     * @return 测试用户
     */
    private UserDTO createUser(Long userId) {
        UserDTO user = new UserDTO();
        user.setId(userId);
        return user;
    }
}
