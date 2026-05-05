package com.hmdp.mq;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MqExceptionClassifierTest {

    @Test
    void seckillOrderExceptionRecoverable() {
        assertTrue(MqExceptionClassifier.isRecoverable(new SeckillOrderException(true, "LOCK_FAILED")));
    }

    @Test
    void seckillOrderExceptionNonRecoverable() {
        assertFalse(MqExceptionClassifier.isRecoverable(new SeckillOrderException(false, "NO_DB_STOCK")));
    }

    @Test
    void causeChainFindsSeckillOrderException() {
        assertFalse(MqExceptionClassifier.isRecoverable(
                new RuntimeException(new SeckillOrderException(false, "NO_DB_STOCK"))));
    }

    @Test
    void illegalArgumentNonRecoverable() {
        assertFalse(MqExceptionClassifier.isRecoverable(new IllegalArgumentException("bad")));
    }

    @Test
    void sqlExceptionRecoverable() {
        assertTrue(MqExceptionClassifier.isRecoverable(new SQLException("conn")));
    }

    @Test
    void ioExceptionRecoverable() {
        assertTrue(MqExceptionClassifier.isRecoverable(new IOException("reset")));
    }

    @Test
    void unknownDefaultsToRecoverable() {
        assertTrue(MqExceptionClassifier.isRecoverable(new UnsupportedOperationException("unknown")));
    }
}
