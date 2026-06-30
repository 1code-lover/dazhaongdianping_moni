package com.hmdp.controller;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.hmdp.annotation.RateLimit;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.entity.UserInfo;
import com.hmdp.enums.LimitType;
import com.hmdp.service.IUserInfoService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import static com.hmdp.utils.RedisConstants.LOGIN_USER_KEY;

/**
 * 用户控制器
 * 处理用户相关的 HTTP 请求，包括登录、登出、签到等功能
 * 接口路径：/user
 *
 * @author ethan
 * @date 2026-06-14
 */

@Slf4j
@RestController
@RequestMapping("/user")
public class UserController {

    @Resource
    private IUserService userService;

    @Resource
    private IUserInfoService userInfoService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 发送手机验证码
     * 验证码接口优先按手机号限流，并额外附加一个冷却时间
     * 限流规则：60秒内最多3次，30秒冷却时间
     *
     * @param phone 手机号码
     * @param session HTTP会话
     * @return 验证码发送结果
     */
    @PostMapping("code")
    @RateLimit(
            keyPrefix = "user:code:phone",
            limitType = LimitType.PHONE,
            timeWindowSeconds = 60,
            maxRequests = 3,
            cooldownSeconds = 30,
            message = "验证码发送过于频繁，请稍后再试"
    )
    public Result sendCode(@RequestParam("phone") String phone, HttpSession session) {
        return userService.sendCode(phone, session);
    }

    /**
     * 用户登录接口
     * 先做手机号维度限流，避免被脚本高频尝试
     * 限流规则：60秒内最多20次
     *
     * @param loginForm 登录表单数据（包含手机号和验证码）
     * @param session HTTP会话
     * @return 登录结果，成功返回用户Token
     */
    @PostMapping("/login")
    @RateLimit(
            keyPrefix = "user:login:phone",
            limitType = LimitType.PHONE,
            timeWindowSeconds = 60,
            maxRequests = 20,
            message = "登录请求过于频繁，请稍后再试"
    )
    public Result login(@RequestBody LoginFormDTO loginForm, HttpSession session) {
        return userService.login(loginForm, session);
    }

    /**
     * 用户登出
     * 清除 Redis 中的用户 Token 和本地 ThreadLocal 的用户信息
     *
     * @param request HTTP请求对象，从header中获取authorization token
     * @return 登出结果
     */
    @PostMapping("/logout")
    public Result logout(HttpServletRequest request) {
        String token = request.getHeader("authorization");
        if (StrUtil.isNotBlank(token)) {
            String key = LOGIN_USER_KEY + token;
            stringRedisTemplate.delete(key);
        }
        UserHolder.removeUser();
        return Result.ok("退出登录成功");
    }

    /**
     * 获取当前登录用户信息
     * 从 ThreadLocal 中获取用户信息
     *
     * @return 当前用户信息（UserDTO）
     */
    @GetMapping("/me")
    public Result me() {
        UserDTO user = UserHolder.getUser();
        return Result.ok(user);
    }

    /**
     * 获取用户详细信息
     * 查询用户的扩展信息（如头像、个人简介等）
     *
     * @param userId 用户ID
     * @return 用户详细信息
     */
    @GetMapping("/info/{id}")
    public Result info(@PathVariable("id") Long userId) {
        UserInfo info = userInfoService.getById(userId);
        if (info == null) {
            return Result.ok();
        }
        info.setCreateTime(null);
        info.setUpdateTime(null);
        return Result.ok(info);
    }

    /**
     * 查询用户基本信息
     * 根据用户ID查询用户名、头像等基本信息
     *
     * @param userId 用户ID
     * @return 用户基本信息（UserDTO）
     */
    @GetMapping("/{id}")
    public Result queryUserById(@PathVariable("id") Long userId) {
        User user = userService.getById(userId);
        if (user == null) {
            return Result.ok();
        }
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        return Result.ok(userDTO);
    }

    /**
     * 用户签到
     * 记录用户当天的签到，使用 Redis Bitmap 数据结构存储
     *
     * @return 签到结果
     */
    @PostMapping("/sign")
    public Result sign() {
        return userService.sign();
    }

    /**
     * 获取用户连续签到天数
     * 统计用户从今天往前连续签到的天数
     *
     * @return 连续签到天数
     */
    @GetMapping("/sign/count")
    public Result signCount() {
        return userService.signCount();
    }
}
