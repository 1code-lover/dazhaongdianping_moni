package com.hmdp.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "canal")
public class CanalConfig {
    private boolean enabled = false;
    private String host = "localhost";
    private int port = 11111;
    private String destination = "example";
    private int batchSize = 100;
}
