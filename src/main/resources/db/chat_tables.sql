-- 对话历史表
CREATE TABLE IF NOT EXISTS `chat_history` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `session_id` varchar(64) NOT NULL COMMENT '会话ID',
  `user_id` bigint(20) DEFAULT NULL COMMENT '用户ID',
  `role` varchar(20) NOT NULL COMMENT '角色：user/assistant/system',
  `content` text NOT NULL COMMENT '消息内容',
  `intent` varchar(50) DEFAULT NULL COMMENT '识别的意图',
  `tokens` int(11) DEFAULT NULL COMMENT '消耗的token数',
  `duration` int(11) DEFAULT NULL COMMENT 'AI调用耗时(ms)',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  INDEX `idx_session_id` (`session_id`),
  INDEX `idx_user_id` (`user_id`),
  INDEX `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='对话历史表';

-- FAQ表
CREATE TABLE IF NOT EXISTS `chat_faq` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `question` varchar(255) NOT NULL COMMENT '问题',
  `answer` text NOT NULL COMMENT '答案',
  `category` varchar(50) DEFAULT NULL COMMENT '分类',
  `sort` int(11) DEFAULT 0 COMMENT '排序',
  `status` tinyint(1) DEFAULT 1 COMMENT '状态：0-禁用 1-启用',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='智能客服FAQ表';

-- 插入示例FAQ
INSERT INTO `chat_faq` (`question`, `answer`, `category`, `sort`, `status`) VALUES
('如何登录', '您可以通过手机号验证码登录，点击登录按钮后输入手机号，获取验证码即可登录。', '使用指南', 1, 1),
('如何使用优惠券', '在商户详情页可以看到可用的优惠券，点击领取后在支付时自动抵扣。', '优惠券', 2, 1),
('如何查看订单', '在个人中心页面可以查看所有订单，包括待支付、已支付、已使用等状态。', '使用指南', 3, 1);
