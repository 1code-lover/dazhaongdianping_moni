-- 商家入驻申请表
CREATE TABLE IF NOT EXISTS `tb_shop_apply` (
    `id` bigint(20) NOT NULL AUTO_INCREMENT,
    `user_id` bigint(20) NOT NULL COMMENT '申请人用户ID',
    `shop_name` varchar(100) NOT NULL COMMENT '店铺名称',
    `shop_type_id` bigint(20) NOT NULL COMMENT '店铺类型ID',
    `shop_img` varchar(500) DEFAULT NULL COMMENT '店铺封面图/Logo',
    `contact_name` varchar(50) NOT NULL COMMENT '联系人姓名',
    `contact_phone` varchar(20) NOT NULL COMMENT '联系电话',
    `address` varchar(200) NOT NULL COMMENT '店铺地址',
    `x` decimal(10,7) DEFAULT NULL COMMENT '经度',
    `y` decimal(10,7) DEFAULT NULL COMMENT '纬度',
    `license_no` varchar(50) DEFAULT NULL COMMENT '营业执照号',
    `license_img` varchar(500) DEFAULT NULL COMMENT '营业执照图片',
    `description` text DEFAULT NULL COMMENT '店铺描述',
    `status` tinyint(1) DEFAULT 0 COMMENT '状态：0待审核 1通过 2拒绝',
    `reject_reason` varchar(255) DEFAULT NULL COMMENT '拒绝原因',
    `audit_time` datetime DEFAULT NULL COMMENT '审核时间',
    `auditor_id` bigint(20) DEFAULT NULL COMMENT '审核人ID',
    `is_deleted` tinyint(1) DEFAULT 0 COMMENT '逻辑删除：0未删除 1已删除',
    `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
    `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商家入驻申请表';

-- 测试数据
INSERT INTO `tb_shop_apply` (`user_id`, `shop_name`, `shop_type_id`, `contact_name`, `contact_phone`, `address`, `status`) VALUES
(1010, '测试餐厅', 1, '张三', '13800138000', '北京市朝阳区xxx路', 0);
