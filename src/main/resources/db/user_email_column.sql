-- ============================================================================
-- 用户表新增邮箱字段
-- 用途：支持邮箱验证码登录（spec: 2026-10-01-email-verify-code-spec）
-- 说明：email 可空；手机号登录的老数据不受影响
-- ============================================================================
SET NAMES utf8mb4;

ALTER TABLE `tb_user`
    ADD COLUMN `email` VARCHAR(128) NULL DEFAULT NULL COMMENT '邮箱，邮箱登录用户绑定' AFTER `phone`;

ALTER TABLE `tb_user`
    ADD INDEX `idx_email` (`email`);

-- 邮箱登录用户无手机号，放宽非空约束（老数据不受影响）
ALTER TABLE `tb_user`
    MODIFY COLUMN `phone` VARCHAR(11) NULL DEFAULT NULL COMMENT '手机号码';
