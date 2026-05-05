CREATE TABLE IF NOT EXISTS tb_voucher_order_fail_task (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    trace_id VARCHAR(64) NOT NULL COMMENT '链路追踪ID',
    order_id BIGINT NOT NULL COMMENT '订单ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    voucher_id BIGINT NOT NULL COMMENT '优惠券ID',
    topic VARCHAR(64) NOT NULL COMMENT '来源Topic',
    raw_payload TEXT COMMENT '原始消息体',
    error_msg VARCHAR(1024) COMMENT '错误信息',
    retry_count INT NOT NULL DEFAULT 0 COMMENT '重试次数',
    status VARCHAR(32) NOT NULL DEFAULT 'INIT' COMMENT '任务状态',
    failure_type VARCHAR(32) DEFAULT NULL COMMENT 'RECOVERABLE|NON_RECOVERABLE',
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_trace_id (trace_id),
    KEY idx_status_create_time (status, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='秒杀订单失败任务表';
