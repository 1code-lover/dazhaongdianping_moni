package com.hmdp.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

/**
 * 图片上传配置。
 *
 * @author ethan
 * @date 2026-07-16
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.upload")
public class UploadProperties {

    private static final long BYTES_PER_MB = 1024L * 1024L;

    private String dir = "./uploads/imgs/";
    private long maxSizeMb = 5L;
    private List<String> allowedExtensions = Arrays.asList("jpg", "jpeg", "png", "webp");

    /**
     * 将配置的 MB 大小转换为字节数。
     *
     * @return 最大文件字节数
     */
    public long getMaxSizeBytes() {
        return maxSizeMb * BYTES_PER_MB;
    }
}
