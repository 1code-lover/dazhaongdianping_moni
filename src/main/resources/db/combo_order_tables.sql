-- 套餐表
CREATE TABLE IF NOT EXISTS `tb_combo` (
    `id` bigint(20) NOT NULL AUTO_INCREMENT,
    `shop_id` bigint(20) NOT NULL COMMENT '商户ID',
    `title` varchar(100) NOT NULL COMMENT '套餐标题',
    `sub_title` varchar(200) DEFAULT NULL COMMENT '副标题',
    `cover` varchar(500) DEFAULT NULL COMMENT '封面图',
    `images` varchar(2000) DEFAULT NULL COMMENT '详情图片JSON',
    `original_price` bigint(20) NOT NULL COMMENT '原价（分）',
    `price` bigint(20) NOT NULL COMMENT '团购价（分）',
    `content` text COMMENT '包含内容',
    `rules` text COMMENT '使用规则',
    `stock` int(11) NOT NULL DEFAULT 0 COMMENT '库存',
    `sales` int(11) DEFAULT 0 COMMENT '已售数量',
    `status` tinyint(1) DEFAULT 1 COMMENT '状态：0下架 1上架',
    `is_deleted` tinyint(1) DEFAULT 0 COMMENT '逻辑删除：0未删除 1已删除',
    `begin_time` datetime DEFAULT NULL COMMENT '生效时间',
    `end_time` datetime DEFAULT NULL COMMENT '失效时间',
    `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
    `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_shop_id` (`shop_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='套餐表';

-- 统一订单表
CREATE TABLE IF NOT EXISTS `tb_order` (
    `id` bigint(20) NOT NULL,
    `order_no` varchar(32) NOT NULL COMMENT '订单号',
    `user_id` bigint(20) NOT NULL COMMENT '用户ID',
    `shop_id` bigint(20) NOT NULL COMMENT '商户ID',
    `order_type` tinyint(1) NOT NULL COMMENT '订单类型：1优惠券 2套餐',
    `biz_id` bigint(20) NOT NULL COMMENT '业务ID',
    `title` varchar(100) DEFAULT NULL COMMENT '商品标题',
    `amount` bigint(20) NOT NULL COMMENT '支付金额（分）',
    `quantity` int(11) DEFAULT 1 COMMENT '数量',
    `status` tinyint(1) DEFAULT 0 COMMENT '状态：0待支付 1待使用 2已核销 3已取消 4退款中 5已退款',
    `verify_code` varchar(6) DEFAULT NULL COMMENT '核销码（6位数字）',
    `verify_time` datetime DEFAULT NULL COMMENT '核销时间',
    `pay_time` datetime DEFAULT NULL COMMENT '支付时间',
    `cancel_time` datetime DEFAULT NULL COMMENT '取消时间',
    `refund_time` datetime DEFAULT NULL COMMENT '退款时间',
    `is_deleted` tinyint(1) DEFAULT 0 COMMENT '逻辑删除：0未删除 1已删除',
    `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
    `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_order_no` (`order_no`),
    UNIQUE KEY `uk_verify_code` (`verify_code`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_shop_id` (`shop_id`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='统一订单表';

-- 核销记录表
CREATE TABLE IF NOT EXISTS `tb_verify_record` (
    `id` bigint(20) NOT NULL AUTO_INCREMENT,
    `order_id` bigint(20) NOT NULL COMMENT '订单ID',
    `order_no` varchar(32) NOT NULL COMMENT '订单号',
    `user_id` bigint(20) NOT NULL COMMENT '用户ID',
    `shop_id` bigint(20) NOT NULL COMMENT '商户ID',
    `operator_id` bigint(20) NOT NULL COMMENT '操作人ID',
    `remark` varchar(255) DEFAULT NULL COMMENT '备注',
    `verify_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '核销时间',
    PRIMARY KEY (`id`),
    KEY `idx_order_id` (`order_id`),
    KEY `idx_shop_id` (`shop_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='核销记录表';

-- 插入测试数据
INSERT INTO `tb_combo` (`shop_id`, `title`, `sub_title`, `cover`, `original_price`, `price`, `content`, `rules`, `stock`, `status`, `begin_time`, `end_time`) VALUES
(1, '双人超值套餐', '适合2-3人享用', 'https://example.com/combo1.jpg', 29800, 16800, '1. 招牌烤鱼 x1\n2. 凉菜 x2\n3. 饮品 x2', '1. 每桌限用1张\n2. 需提前预约\n3. 不与其他优惠同享', 100, 1, '2026-01-01 00:00:00', '2026-12-31 23:59:59'),
(1, '家庭欢乐套餐', '适合4-5人享用', 'https://example.com/combo2.jpg', 49800, 29800, '1. 招牌烤鱼 x1\n2. 热菜 x3\n3. 凉菜 x2\n4. 主食 x2\n5. 饮品 x4', '1. 每桌限用1张\n2. 需提前预约\n3. 不与其他优惠同享', 50, 1, '2026-01-01 00:00:00', '2026-12-31 23:59:59'),
(2, '单人午餐套餐', '工作日午餐首选', 'https://example.com/combo3.jpg', 12800, 6800, '1. 主菜 x1\n2. 小菜 x1\n3. 汤 x1\n4. 米饭 x1', '1. 仅限工作日使用\n2. 每人限用1张\n3. 不与其他优惠同享', 200, 1, '2026-01-01 00:00:00', '2026-12-31 23:59:59');
