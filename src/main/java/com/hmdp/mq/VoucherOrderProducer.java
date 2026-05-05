package com.hmdp.mq;

public interface VoucherOrderProducer {
    boolean send(VoucherOrderMessage msg);
}
