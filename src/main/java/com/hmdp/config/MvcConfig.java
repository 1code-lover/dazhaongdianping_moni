package com.hmdp.config;

import com.hmdp.utils.AdminInterceptor;
import com.hmdp.utils.LoginInterceptor;
import com.hmdp.utils.RefreshTokenInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import javax.annotation.Resource;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Web MVC 拦截器和静态资源配置。
 *
 * @author ethan
 * @date 2026-07-16
 */
@Configuration
public class MvcConfig implements WebMvcConfigurer {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private UploadProperties uploadProperties;

    @Value("${app.security.admin-user-ids:}")
    private String adminUserIds;

    /**
     * 注册登录态刷新、登录校验和管理员权限校验拦截器。
     *
     * @param registry 拦截器注册器
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 登录校验：公开查询和登录接口允许匿名访问
        registry.addInterceptor(new LoginInterceptor())
                .excludePathPatterns(
                        "/shop/**",
                        "/voucher/**",
                        "/shop-type/**",
                        "/imgs/**",
                        "/blogs/**",
                        "/blog/hot",
                        "/user/code",
                        "/user/login",
                        "/doc.html",
                        "/webjars/**",
                        "/swagger-resources/**",
                        "/v2/api-docs/**",
                        "/combo/list/**",
                        "/combo/*"
                ).order(1);
        // 管理员接口在登录校验后继续执行管理员白名单校验
        registry.addInterceptor(new AdminInterceptor(adminUserIds))
                .addPathPatterns("/admin/**", "/voucher-order-fail-task/**")
                .order(2);
        // 对所有请求尝试恢复登录态并刷新 token 有效期
        registry.addInterceptor(new RefreshTokenInterceptor(stringRedisTemplate))
                .addPathPatterns("/**")
                .order(0);
    }

    /**
     * 将外置上传目录映射为图片访问路径。
     * /blogs/** 为兼容现有 Blog 图片路径保留。
     *
     * @param registry 静态资源注册器
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        Path uploadRoot = Paths.get(uploadProperties.getDir()).toAbsolutePath().normalize();
        registry.addResourceHandler("/imgs/**")
                .addResourceLocations(toResourceLocation(uploadRoot));
        registry.addResourceHandler("/blogs/**")
                .addResourceLocations(toResourceLocation(uploadRoot.resolve("blogs")));
    }

    /**
     * 将目录路径转换为 Spring 静态资源位置。
     *
     * @param directory 资源目录
     * @return 以斜杠结尾的 file URI
     */
    private String toResourceLocation(Path directory) {
        String location = directory.toUri().toString();
        return location.endsWith("/") ? location : location + "/";
    }
}
