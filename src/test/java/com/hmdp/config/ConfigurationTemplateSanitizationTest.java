package com.hmdp.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 配置模板与部署配置安全检查。
 *
 * @author ethan
 * @date 2026-07-16
 */
class ConfigurationTemplateSanitizationTest {

    /**
     * 公共配置和本地示例不得保留演示口令或弱口令默认值。
     *
     * @throws IOException 读取配置文件失败时抛出
     */
    @Test
    void testConfigurationWhenCommittedThenUseCredentialPlaceholders() throws IOException {
        String application = readUtf8("src/main/resources/application.yaml");
        String localExample = readUtf8("src/main/resources/application-local.example.yaml");

        assertFalse(application.contains("DB_PASSWORD:password"), "数据库密码不应提供弱口令默认值");
        assertFalse(application.contains("REDIS_PASSWORD:123456"), "Redis 密码不应提供演示口令默认值");
        assertFalse(localExample.contains("123456"), "示例配置不应该保留演示口令");
        assertTrue(localExample.contains("your-db-password"), "数据库密码应使用占位值");
        assertTrue(localExample.contains("your-redis-password"), "Redis 密码应使用占位值");
    }

    /**
     * Nginx 应将外置上传图片路径转发到后端资源处理器。
     *
     * @throws IOException 读取 Nginx 配置失败时抛出
     */
    @Test
    void testNginxWhenServingUploadedImagesThenProxyToBackend() throws IOException {
        String nginxConfig = readUtf8("heima_qianduan/nginx-1.18.0/conf/nginx.conf");

        assertTrue(nginxConfig.contains("location /imgs/"), "Nginx 应代理评价图片访问路径");
        assertTrue(nginxConfig.contains("location /blogs/"), "Nginx 应代理历史 Blog 图片访问路径");
    }

    /**
     * 以 UTF-8 编码读取项目文件。
     *
     * @param relativePath 项目相对路径
     * @return 文件内容
     * @throws IOException 文件读取失败时抛出
     */
    private String readUtf8(String relativePath) throws IOException {
        Path path = Paths.get(relativePath);
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}
