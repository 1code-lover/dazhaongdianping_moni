package com.hmdp.mq;

import com.hmdp.service.IVoucherOrderFailTaskService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 失败任务定时重试调度器
 *
 * 【为什么需要定时重试】
 *
 *   正常情况下，Kafka 消息消费失败会重试3次。
 *   但如果3次都失败了，消息会进入 DLT（死信队列）。
 *
 *   DLT 中的消息可能是因为：
 *     - 临时性故障（网络抖动、DB慢查询）
 *     - 外部依赖暂时不可用
 *
 *   这些消息不应该直接丢弃，应该等待故障恢复后重试。
 *
 * 【调度策略】
 *
 *   每15秒（可配置）执行一次：
 *     1. 查询所有 RETRY_PENDING 状态的任务
 *     2. 批量重试（每次最多20个）
 *     3. 重试成功的标记为 DONE
 *     4. 重试失败的继续保持 RETRY_PENDING
 *
 * 【重试次数限制】
 *
 *   每个任务最多重试5次（可配置）。
 *   超过5次仍失败，标记为 MANUAL_HANDLE_REQUIRED，需人工处理。
 *
 * 【配置参数】
 *
 *   - app.kafka.fail-task.auto-retry-enabled：是否开启自动重试
 *   - app.kafka.fail-task.retry-interval-ms：重试间隔，默认15秒
 *   - app.kafka.fail-task.batch-size：每批处理数量，默认20
 *   - app.kafka.fail-task.max-retry-count：最大重试次数，默认5
 */
@Component
public class VoucherOrderFailTaskRetryScheduler {

    private static final Logger log = LoggerFactory.getLogger(VoucherOrderFailTaskRetryScheduler.class);

    private final IVoucherOrderFailTaskService failTaskService;

    /** 是否开启自动重试 */
    @Value("${app.kafka.fail-task.auto-retry-enabled:true}")
    private boolean autoRetryEnabled;
    /** 每批处理数量 */
    @Value("${app.kafka.fail-task.batch-size:20}")
    private int batchSize;
    /** 最大重试次数 */
    @Value("${app.kafka.fail-task.max-retry-count:5}")
    private int maxRetryCount;

    public VoucherOrderFailTaskRetryScheduler(IVoucherOrderFailTaskService failTaskService) {
        this.failTaskService = failTaskService;
    }

    /**
     * 定时重试失败任务
     *
     * 每隔15秒（默认）执行一次
     */
    @Scheduled(fixedDelayString = "${app.kafka.fail-task.retry-interval-ms:15000}")
    public void retryFailTasks() {
        // 检查是否开启自动重试
        if (!autoRetryEnabled) {
            return;
        }

        // 调用服务层批量重试
        int successCount = failTaskService.retryPendingTasks(batchSize, maxRetryCount);

        if (successCount > 0) {
            log.info("Fail task auto retry batch finished, successCount={}, batchSize={}, maxRetryCount={}",
                    successCount, batchSize, maxRetryCount);
        }
    }
}