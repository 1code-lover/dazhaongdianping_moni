package com.hmdp.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hmdp.entity.VoucherOrderFailTask;
import com.hmdp.mq.VoucherOrderMessage;

public interface IVoucherOrderFailTaskService extends IService<VoucherOrderFailTask> {

    void saveOrUpdateFailTask(String topic, String rawPayload, VoucherOrderMessage message, String errorMsg, int retryCount, String status);

    void saveOrUpdateFailTask(String topic, String rawPayload, VoucherOrderMessage message, String errorMsg, int retryCount, String status, String failureType);

    /**
     * 死信进入后：落库 + 自动回滚 Redis 预扣，状态一般为 ROLLBACK_DONE 或需人工。
     */
    void saveDeadLetterTaskAndRollback(String topic, String payload, VoucherOrderMessage message, int retryCount);

    int retryPendingTasks(int batchSize, int maxRetryCount);

    boolean retryTaskById(Long taskId, int maxRetryCount);

    boolean markTaskIgnored(Long taskId, String note);
}
