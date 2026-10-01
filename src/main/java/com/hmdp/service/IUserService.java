package com.hmdp.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.entity.User;

import javax.servlet.http.HttpSession;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IUserService extends IService<User> {

    /**
     * 发送登录验证码
     * 支持手机号或邮箱（自动识别通道），验证码存 Redis 并由配置的通道下发
     *
     * @param target 登录标识（手机号或邮箱）
     * @param ip     请求来源 IP（用于单日发送上限控制）
     * @return 发送结果
     */
    Result sendCode(String target, String ip);

    Result login(LoginFormDTO loginForm, HttpSession session);

    Result sign();

    Result signCount();

}
