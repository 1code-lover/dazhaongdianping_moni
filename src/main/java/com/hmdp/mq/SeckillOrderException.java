package com.hmdp.mq;

/**
 * 秒杀订单创建异常：携带是否可恢复（可恢复则走 Kafka 重试，不可恢复则回滚 Redis 并 ack）。
 */
public class SeckillOrderException extends RuntimeException {

    private final boolean recoverable;

    public SeckillOrderException(boolean recoverable, String message) {
        super(message);
        this.recoverable = recoverable;
    }

    public SeckillOrderException(boolean recoverable, String message, Throwable cause) {
        super(message, cause);
        this.recoverable = recoverable;
    }

    public boolean isRecoverable() {
        return recoverable;
    }
}
