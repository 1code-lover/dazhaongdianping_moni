package com.hmdp.service.impl;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class VoucherOrderServiceImplTransactionTest {

    @Test
    void handleVoucherOrderFromMqShouldBeTransactional() throws NoSuchMethodException {
        Method method = VoucherOrderServiceImpl.class.getMethod("handleVoucherOrderFromMQ", com.hmdp.entity.VoucherOrder.class);
        Transactional transactional = method.getAnnotation(Transactional.class);
        assertNotNull(transactional, "Kafka 消费后的订单落库入口应该具备事务边界");
    }
}
