-- 已有表追加：异常分类字段（若已存在可忽略报错）
ALTER TABLE tb_voucher_order_fail_task
    ADD COLUMN failure_type VARCHAR(32) DEFAULT NULL COMMENT 'RECOVERABLE|NON_RECOVERABLE' AFTER status;
