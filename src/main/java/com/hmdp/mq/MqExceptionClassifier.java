package com.hmdp.mq;

import org.springframework.dao.DataAccessException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.transaction.TransactionTimedOutException;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.sql.SQLException;
import java.util.concurrent.TimeoutException;

/**
 * 将异常粗分为「可恢复」（适合重试）与「不可恢复」（应回滚 Redis 或人工处理）。
 */
public final class MqExceptionClassifier {

    private MqExceptionClassifier() {
    }

    public static boolean isRecoverable(Throwable t) {
        if (t == null) {
            return true;
        }
        Throwable cur = t;
        while (cur != null) {
            if (cur instanceof SeckillOrderException) {
                return ((SeckillOrderException) cur).isRecoverable();
            }
            cur = cur.getCause();
        }
        cur = t;
        while (cur != null) {
            if (cur instanceof IllegalArgumentException) {
                return false;
            }
            cur = cur.getCause();
        }
        if (t instanceof SQLException) {
            return true;
        }
        if (t instanceof DataAccessException) {
            return true;
        }
        if (t instanceof TransientDataAccessException) {
            return true;
        }
        if (t instanceof QueryTimeoutException) {
            return true;
        }
        if (t instanceof CannotCreateTransactionException) {
            return true;
        }
        if (t instanceof TransactionTimedOutException) {
            return true;
        }
        if (t instanceof IOException) {
            return true;
        }
        if (t instanceof SocketTimeoutException) {
            return true;
        }
        if (t instanceof TimeoutException) {
            return true;
        }
        // 默认偏保守：未知异常先按可重试处理，避免误删 Redis 预扣
        return true;
    }
}
