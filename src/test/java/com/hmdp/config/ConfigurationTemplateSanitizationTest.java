package com.hmdp.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigurationTemplateSanitizationTest {

    @Test
    void localExampleShouldUsePlaceholdersInsteadOfDemoPasswords() throws IOException {
        String content = new String(
                Files.readAllBytes(Paths.get("src/main/resources/application-local.example.yaml")),
                StandardCharsets.UTF_8
        );

        assertFalse(content.contains("123456"), "示例配置不应该保留演示口令");
        assertTrue(content.contains("your-db-password"), "数据库密码应使用占位值");
        assertTrue(content.contains("your-redis-password"), "Redis 密码应使用占位值");
    }
}
