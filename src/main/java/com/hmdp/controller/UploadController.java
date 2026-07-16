package com.hmdp.controller;

import com.hmdp.config.UploadProperties;
import com.hmdp.dto.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * 文件上传控制器。
 * 负责图片校验、外置目录存储和安全删除。
 *
 * @author ethan
 * @date 2026-07-16
 */
@Slf4j
@RestController
@RequestMapping("/upload")
public class UploadController {

    private static final String BLOG_DIRECTORY = "blogs";
    private static final String REVIEW_DIRECTORY = "reviews";
    private static final String IMAGE_URL_PREFIX = "/imgs/";

    private final UploadProperties uploadProperties;

    /**
     * 创建文件上传控制器。
     *
     * @param uploadProperties 图片上传配置
     */
    public UploadController(UploadProperties uploadProperties) {
        this.uploadProperties = uploadProperties;
    }

    /**
     * 上传 Blog 图片。
     * 为兼容现有前端，继续返回以 /blogs 开头的访问路径。
     *
     * @param image 待上传图片
     * @return 图片访问路径或校验错误
     */
    @PostMapping("/blog")
    public Result uploadImage(@RequestParam("file") MultipartFile image) {
        return uploadImage(image, BLOG_DIRECTORY, false);
    }

    /**
     * 上传评价图片。
     *
     * @param image 待上传图片
     * @return 图片访问路径或校验错误
     */
    @PostMapping("/review")
    public Result uploadReviewImage(@RequestParam("file") MultipartFile image) {
        return uploadImage(image, REVIEW_DIRECTORY, true);
    }

    /**
     * 删除 Blog 图片。
     * 仅允许删除符合系统生成规则的 blogs 目录图片。
     *
     * @param filename 图片相对路径
     * @return 删除结果
     */
    @DeleteMapping("/blog")
    public Result deleteBlogImg(@RequestParam("name") String filename) {
        try {
            Path target = resolveBlogDeleteTarget(filename);
            if (target == null || Files.isDirectory(target)) {
                return Result.fail("错误的文件名称");
            }
            Files.deleteIfExists(target);
            return Result.ok();
        } catch (IOException exception) {
            log.error("删除 Blog 图片失败，name={}", filename, exception);
            return Result.fail("文件删除失败");
        }
    }

    /**
     * 校验并保存图片。
     *
     * @param image 待上传图片
     * @param directory 业务目录
     * @param includeImagePrefix 是否返回 /imgs 前缀
     * @return 上传结果
     */
    private Result uploadImage(MultipartFile image, String directory, boolean includeImagePrefix) {
        try {
            String extension = validateImage(image);
            Path relativePath = createRelativePath(directory, extension);
            saveImage(image, relativePath);
            String accessPath = toAccessPath(relativePath, includeImagePrefix);
            log.debug("图片上传成功，path={}", accessPath);
            return Result.ok(accessPath);
        } catch (IllegalArgumentException exception) {
            return Result.fail(exception.getMessage());
        } catch (IOException exception) {
            log.error("图片上传失败，directory={}", directory, exception);
            return Result.fail("文件上传失败");
        }
    }

    /**
     * 校验图片大小、扩展名、MIME 类型和文件头。
     *
     * @param image 待校验图片
     * @return 标准化扩展名
     * @throws IOException 读取文件头失败时抛出
     */
    private String validateImage(MultipartFile image) throws IOException {
        if (image == null || image.isEmpty()) {
            throw new IllegalArgumentException("上传文件不能为空");
        }
        if (image.getSize() > uploadProperties.getMaxSizeBytes()) {
            throw new IllegalArgumentException("图片大小不能超过 " + uploadProperties.getMaxSizeMb() + "MB");
        }
        String extension = extractExtension(image.getOriginalFilename());
        if (!allowedExtensions().contains(extension)) {
            throw new IllegalArgumentException("仅支持 jpg、jpeg、png、webp 图片");
        }
        if (!matchesContentType(extension, image.getContentType()) || !hasValidSignature(image, extension)) {
            throw new IllegalArgumentException("图片格式与文件内容不匹配");
        }
        return extension;
    }

    /**
     * 从原始文件名提取小写扩展名。
     *
     * @param originalFilename 原始文件名
     * @return 小写扩展名
     */
    private String extractExtension(String originalFilename) {
        if (originalFilename == null) {
            throw new IllegalArgumentException("文件名不能为空");
        }
        String filename = Paths.get(originalFilename).getFileName().toString();
        int dotIndex = filename.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == filename.length() - 1) {
            throw new IllegalArgumentException("文件扩展名不能为空");
        }
        return filename.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
    }

    /**
     * 获取标准化扩展名白名单。
     *
     * @return 小写扩展名集合
     */
    private Set<String> allowedExtensions() {
        Set<String> extensions = new HashSet<>();
        List<String> configuredExtensions = uploadProperties.getAllowedExtensions();
        if (configuredExtensions == null) {
            return extensions;
        }
        for (String extension : configuredExtensions) {
            if (extension != null) {
                extensions.add(extension.trim().toLowerCase(Locale.ROOT));
            }
        }
        return extensions;
    }

    /**
     * 校验扩展名与请求 MIME 类型是否一致。
     *
     * @param extension 文件扩展名
     * @param contentType 请求 MIME 类型
     * @return 是否匹配
     */
    private boolean matchesContentType(String extension, String contentType) {
        if (contentType == null) {
            return false;
        }
        String normalizedType = contentType.toLowerCase(Locale.ROOT);
        if ("jpg".equals(extension) || "jpeg".equals(extension)) {
            return "image/jpeg".equals(normalizedType);
        }
        if ("png".equals(extension)) {
            return "image/png".equals(normalizedType);
        }
        return "webp".equals(extension) && "image/webp".equals(normalizedType);
    }

    /**
     * 校验常见图片格式的魔数，避免仅伪造扩展名和 MIME 类型。
     *
     * @param image 待校验图片
     * @param extension 文件扩展名
     * @return 文件头是否有效
     * @throws IOException 读取文件头失败时抛出
     */
    private boolean hasValidSignature(MultipartFile image, String extension) throws IOException {
        byte[] header = new byte[12];
        int length;
        try (InputStream inputStream = image.getInputStream()) {
            length = inputStream.read(header);
        }
        if ("jpg".equals(extension) || "jpeg".equals(extension)) {
            return length >= 3 && byteEquals(header[0], 0xFF)
                    && byteEquals(header[1], 0xD8) && byteEquals(header[2], 0xFF);
        }
        if ("png".equals(extension)) {
            int[] signature = {0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
            return startsWith(header, length, signature);
        }
        int[] riff = {0x52, 0x49, 0x46, 0x46};
        int[] webp = {0x57, 0x45, 0x42, 0x50};
        return "webp".equals(extension) && startsWith(header, length, riff)
                && startsWithAt(header, length, webp, 8);
    }

    /**
     * 判断文件头是否包含指定魔数。
     *
     * @param header 文件头
     * @param length 实际读取长度
     * @param signature 魔数
     * @return 是否匹配
     */
    private boolean startsWith(byte[] header, int length, int[] signature) {
        return startsWithAt(header, length, signature, 0);
    }

    /**
     * 从指定偏移量判断文件头是否包含魔数。
     *
     * @param header 文件头
     * @param length 实际读取长度
     * @param signature 魔数
     * @param offset 起始偏移量
     * @return 是否匹配
     */
    private boolean startsWithAt(byte[] header, int length, int[] signature, int offset) {
        if (length < offset + signature.length) {
            return false;
        }
        for (int index = 0; index < signature.length; index++) {
            if (!byteEquals(header[offset + index], signature[index])) {
                return false;
            }
        }
        return true;
    }

    /**
     * 比较有符号字节和无符号十六进制值。
     *
     * @param actual 实际字节
     * @param expected 预期无符号值
     * @return 是否相等
     */
    private boolean byteEquals(byte actual, int expected) {
        return (actual & 0xFF) == expected;
    }

    /**
     * 创建不可预测的分级相对存储路径。
     *
     * @param directory 业务目录
     * @param extension 文件扩展名
     * @return 相对存储路径
     */
    private Path createRelativePath(String directory, String extension) {
        String name = UUID.randomUUID().toString();
        int hash = name.hashCode();
        String firstLevel = Integer.toHexString(hash & 0xF);
        String secondLevel = Integer.toHexString((hash >> 4) & 0xF);
        return Paths.get(directory, firstLevel, secondLevel, name + "." + extension);
    }

    /**
     * 将图片保存到配置的外置目录。
     *
     * @param image 待保存图片
     * @param relativePath 相对存储路径
     * @throws IOException 保存失败时抛出
     */
    private void saveImage(MultipartFile image, Path relativePath) throws IOException {
        Path uploadRoot = uploadRoot();
        Path target = uploadRoot.resolve(relativePath).normalize();
        if (!target.startsWith(uploadRoot)) {
            throw new IllegalArgumentException("错误的文件路径");
        }
        Files.createDirectories(target.getParent());
        image.transferTo(target.toFile());
    }

    /**
     * 解析并验证 Blog 图片删除目标。
     *
     * @param filename 外部传入文件路径
     * @return 安全目标；路径非法时返回 null
     */
    private Path resolveBlogDeleteTarget(String filename) {
        try {
            String normalizedName = normalizeExternalPath(filename);
            Path relativePath = Paths.get(normalizedName).normalize();
            if (!isGeneratedBlogPath(relativePath)) {
                return null;
            }
            Path uploadRoot = uploadRoot();
            Path target = uploadRoot.resolve(relativePath).normalize();
            return target.startsWith(uploadRoot) ? target : null;
        } catch (InvalidPathException exception) {
            return null;
        }
    }

    /**
     * 去除访问路径前缀，转换为存储相对路径。
     *
     * @param filename 外部访问路径
     * @return 存储相对路径字符串
     */
    private String normalizeExternalPath(String filename) {
        if (filename == null) {
            return "";
        }
        String normalized = filename.trim().replace('\\', '/');
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        return normalized.startsWith("imgs/") ? normalized.substring(5) : normalized;
    }

    /**
     * 校验路径是否符合系统生成的 Blog 图片结构。
     *
     * @param relativePath 相对路径
     * @return 是否为合法 Blog 图片路径
     */
    private boolean isGeneratedBlogPath(Path relativePath) {
        if (relativePath.isAbsolute() || relativePath.getNameCount() != 4) {
            return false;
        }
        if (!BLOG_DIRECTORY.equals(relativePath.getName(0).toString())) {
            return false;
        }
        String firstLevel = relativePath.getName(1).toString();
        String secondLevel = relativePath.getName(2).toString();
        String filename = relativePath.getFileName().toString();
        return firstLevel.matches("[0-9a-f]") && secondLevel.matches("[0-9a-f]")
                && filename.matches("[0-9a-fA-F-]{36}[.](?i:jpg|jpeg|png|webp)");
    }

    /**
     * 获取标准化的上传根目录。
     *
     * @return 上传根目录绝对路径
     */
    private Path uploadRoot() {
        return Paths.get(uploadProperties.getDir()).toAbsolutePath().normalize();
    }

    /**
     * 将存储相对路径转换为对外访问路径。
     *
     * @param relativePath 存储相对路径
     * @param includeImagePrefix 是否添加 /imgs 前缀
     * @return 对外访问路径
     */
    private String toAccessPath(Path relativePath, boolean includeImagePrefix) {
        String path = relativePath.toString().replace('\\', '/');
        return includeImagePrefix ? IMAGE_URL_PREFIX + path : "/" + path;
    }
}
