package com.hmdp.controller;

import com.hmdp.config.UploadProperties;
import com.hmdp.dto.Result;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 图片上传控制器单元测试。
 *
 * @author ethan
 * @date 2026-07-16
 */
class UploadControllerTest {

    private static final byte[] PNG_HEADER = {
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
    };

    @TempDir
    Path uploadDir;

    private UploadProperties uploadProperties;
    private UploadController uploadController;

    /**
     * 为每个用例创建独立的外置上传目录和配置。
     */
    @BeforeEach
    void setUp() {
        uploadProperties = new UploadProperties();
        uploadProperties.setDir(uploadDir.toString());
        uploadProperties.setMaxSizeMb(1L);
        uploadProperties.setAllowedExtensions(Arrays.asList("jpg", "jpeg", "png", "webp"));
        uploadController = new UploadController(uploadProperties);
    }

    /**
     * 合法评价图片应保存到外置目录并返回 /imgs 访问路径。
     */
    @Test
    void testUploadReviewImageWhenPngValidThenSuccess() {
        MockMultipartFile image = image("review.png", "image/png", PNG_HEADER);

        Result result = uploadController.uploadReviewImage(image);

        assertTrue(result.getSuccess());
        String accessPath = (String) result.getData();
        assertTrue(accessPath.startsWith("/imgs/reviews/"));
        assertTrue(Files.exists(uploadDir.resolve(accessPath.substring("/imgs/".length()))));
    }

    /**
     * 空文件上传应被拒绝。
     */
    @Test
    void testUploadImageWhenFileEmptyThenFail() {
        MockMultipartFile image = image("empty.png", "image/png", new byte[0]);

        Result result = uploadController.uploadImage(image);

        assertFalse(result.getSuccess());
        assertEquals("上传文件不能为空", result.getErrorMsg());
    }

    /**
     * 不在白名单中的扩展名应被拒绝。
     */
    @Test
    void testUploadImageWhenExtensionForbiddenThenFail() {
        MockMultipartFile image = image("payload.exe", "image/png", PNG_HEADER);

        Result result = uploadController.uploadImage(image);

        assertFalse(result.getSuccess());
        assertEquals("仅支持 jpg、jpeg、png、webp 图片", result.getErrorMsg());
    }

    /**
     * 扩展名与 MIME 类型不一致时应被拒绝。
     */
    @Test
    void testUploadImageWhenContentTypeMismatchThenFail() {
        MockMultipartFile image = image("fake.jpg", "image/png", PNG_HEADER);

        Result result = uploadController.uploadImage(image);

        assertFalse(result.getSuccess());
        assertEquals("图片格式与文件内容不匹配", result.getErrorMsg());
    }

    /**
     * 伪造图片扩展名和 MIME 但文件头无效时应被拒绝。
     */
    @Test
    void testUploadImageWhenSignatureInvalidThenFail() {
        MockMultipartFile image = image("fake.png", "image/png", "not-image".getBytes());

        Result result = uploadController.uploadImage(image);

        assertFalse(result.getSuccess());
        assertEquals("图片格式与文件内容不匹配", result.getErrorMsg());
    }

    /**
     * 超过业务大小限制的图片应被拒绝。
     */
    @Test
    void testUploadImageWhenFileTooLargeThenFail() {
        byte[] content = new byte[1024 * 1024 + 1];
        System.arraycopy(PNG_HEADER, 0, content, 0, PNG_HEADER.length);
        MockMultipartFile image = image("large.png", "image/png", content);

        Result result = uploadController.uploadImage(image);

        assertFalse(result.getSuccess());
        assertEquals("图片大小不能超过 1MB", result.getErrorMsg());
    }

    /**
     * 系统生成的 Blog 图片路径应允许删除。
     */
    @Test
    void testDeleteBlogImgWhenPathGeneratedThenSuccess() {
        Result uploadResult = uploadController.uploadImage(image("blog.png", "image/png", PNG_HEADER));
        String accessPath = (String) uploadResult.getData();
        Path storedFile = uploadDir.resolve(accessPath.substring(1));
        assertTrue(Files.exists(storedFile));

        Result deleteResult = uploadController.deleteBlogImg(accessPath);

        assertTrue(deleteResult.getSuccess());
        assertFalse(Files.exists(storedFile));
    }

    /**
     * 包含目录穿越的删除路径应被拒绝且不得删除目标文件。
     *
     * @throws Exception 创建测试文件失败时抛出
     */
    @Test
    void testDeleteBlogImgWhenPathTraversalThenFail() throws Exception {
        Path outsideFile = uploadDir.getParent().resolve("outside.png");
        Files.write(outsideFile, PNG_HEADER);

        Result result = uploadController.deleteBlogImg("../../outside.png");

        assertFalse(result.getSuccess());
        assertEquals("错误的文件名称", result.getErrorMsg());
        assertTrue(Files.exists(outsideFile));
        Files.deleteIfExists(outsideFile);
    }

    /**
     * 创建图片类型的 Mock 上传文件。
     *
     * @param filename 文件名
     * @param contentType MIME 类型
     * @param content 文件内容
     * @return Mock 上传文件
     */
    private MockMultipartFile image(String filename, String contentType, byte[] content) {
        return new MockMultipartFile("file", filename, contentType, content);
    }
}
