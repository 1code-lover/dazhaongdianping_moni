-- 评价表
CREATE TABLE IF NOT EXISTS `tb_review` (
    `id` bigint(20) NOT NULL AUTO_INCREMENT,
    `user_id` bigint(20) NOT NULL COMMENT '用户ID',
    `shop_id` bigint(20) NOT NULL COMMENT '商户ID',
    `order_id` bigint(20) NOT NULL COMMENT '订单ID',
    `order_type` tinyint(1) NOT NULL COMMENT '订单类型：1优惠券 2套餐',
    `score` int(1) NOT NULL COMMENT '评分：1-5星',
    `content` text DEFAULT NULL COMMENT '评价内容',
    `images` varchar(1000) DEFAULT NULL COMMENT '评价图片，多个用逗号分隔',
    `reply` text DEFAULT NULL COMMENT '商家回复',
    `reply_time` datetime DEFAULT NULL COMMENT '商家回复时间',
    `status` tinyint(1) DEFAULT 1 COMMENT '状态：0隐藏 1显示',
    `is_deleted` tinyint(1) DEFAULT 0 COMMENT '逻辑删除：0未删除 1已删除',
    `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
    `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_order_id` (`order_id`) COMMENT '每个订单只能评价一次',
    KEY `idx_shop_id` (`shop_id`),
    KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='评价表';

-- 商户表扩展字段
ALTER TABLE `tb_shop` ADD COLUMN `avg_score` decimal(2,1) DEFAULT 0 COMMENT '平均评分';
ALTER TABLE `tb_shop` ADD COLUMN `review_count` int DEFAULT 0 COMMENT '评价数量';
