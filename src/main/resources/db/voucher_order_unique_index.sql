ALTER TABLE tb_voucher_order
ADD CONSTRAINT uk_user_voucher UNIQUE (user_id, voucher_id);
