package com.hmdp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.entity.VoucherOrderFailTask;
import com.hmdp.mapper.VoucherOrderFailTaskMapper;
import com.hmdp.mq.VoucherOrderMessage;
import com.hmdp.service.IVoucherOrderFailTaskService;
import com.hmdp.service.SeckillRedisRollbackService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 秒杀订单失败任务服务
 *
 * 【为什么需要失败任务表】
 *
 *   正常情况下，Kafka消息消费失败会重试3次。
 *   但如果3次都失败了，消息会进入 DLT（死信队列）。
 *   DLT 中的消息如果直接丢弃，订单就永远无法处理了。
 *
 *   所以需要一个表来记录这些失败的任务，
 *   方便后续：人工处理 / 定时重试 / 数据分析
 *
 * 【任务状态】
 *
 *   RETRY_PENDING：待重试（刚进入失败任务表）
 *   RETRYING：重试中
 *   DONE：重试成功
 *   ROLLBACK_DONE：已回滚（Redis已恢复，订单失败）
 *   MANUAL_HANDLE_REQUIRED：需人工处理
 *
 * 【失败类型】
 *
 *   RECOVERABLE：可恢复（网络超时、临时故障）
 *     → 定时重试
 *   NON_RECOVERABLE：不可恢复（数据错误、系统错误）
 *     → 回滚Redis + 人工处理
 */
@Service
public class VoucherOrderFailTaskServiceImpl extends ServiceImpl<VoucherOrderFailTaskMapper, VoucherOrderFailTask> implements IVoucherOrderFailTaskService {

    private static final Logger log = LoggerFactory.getLogger(VoucherOrderFailTaskServiceImpl.class);

    /** 待重试 */
    private static final String STATUS_RETRY_PENDING = "RETRY_PENDING";
    /** 重试中 */
    private static final String STATUS_RETRYING = "RETRYING";
    /** 重试成功 */
    private static final String STATUS_DONE = "DONE";
    /** 需人工处理 */
    private static final String STATUS_MANUAL = "MANUAL_HANDLE_REQUIRED";
    /** 已回滚完成 */
    private static final String STATUS_ROLLBACK_DONE = "ROLLBACK_DONE";

    /** 可恢复类型 */
    private static final String FAIL_TYPE_RECOVERABLE = "RECOVERABLE";
    /** 不可恢复类型 */
    private static final String FAIL_TYPE_NON_RECOVERABLE = "NON_RECOVERABLE";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final SeckillRedisRollbackService seckillRedisRollbackService;

    /** 主Topic名称 */
    @Value("${spring.kafka.template.default-topic:seckill-order}")
    private String mainTopic;

    public VoucherOrderFailTaskServiceImpl(KafkaTemplate<String, String> kafkaTemplate,
                                           SeckillRedisRollbackService seckillRedisRollbackService) {
        this.kafkaTemplate = kafkaTemplate;
        this.seckillRedisRollbackService = seckillRedisRollbackService;
    }

    /**
     * 保存或更新失败任务
     *
     * @param topic       Kafka Topic
     * @param rawPayload  原始消息
     * @param message     消息对象
     * @param errorMsg    错误信息
     * @param retryCount  重试次数
     * @param status      状态
     */
    @Override
    public void saveOrUpdateFailTask(String topic, String rawPayload, VoucherOrderMessage message, String errorMsg, int retryCount, String status) {
        saveOrUpdateFailTask(topic, rawPayload, message, errorMsg, retryCount, status, null);
    }

    @Override
    public void saveOrUpdateFailTask(String topic, String rawPayload, VoucherOrderMessage message, String errorMsg, int retryCount, String status, String failureType) {
        // 根据 traceId 查询是否已存在
        VoucherOrderFailTask exist = query().eq("trace_id", message.getTraceId()).one();
        VoucherOrderFailTask task = exist == null ? new VoucherOrderFailTask() : exist;

        // 设置字段
        task.setTraceId(message.getTraceId());
        task.setOrderId(message.getOrderId());
        task.setUserId(message.getUserId());
        task.setVoucherId(message.getVoucherId());
        task.setTopic(topic);
        task.setRawPayload(rawPayload);
        task.setErrorMsg(errorMsg);
        task.setRetryCount(retryCount);
        task.setStatus(status);
        task.setFailureType(failureType);

        // 新增或更新
        if (exist == null) {
            save(task);
        } else {
            updateById(task);
        }
    }

    /**
     * 保存死信任务并回滚Redis
     *
     * 当消息进入 DLT（重试3次仍失败）时调用。
     * 1. 回滚 Redis 预扣的库存和用户标记
     * 2. 保存失败任务到数据库
     *
     * @param topic       原Topic
     * @param payload     原始消息
     * @param message     消息对象
     * @param retryCount  重试次数
     */
    @Override
    public void saveDeadLetterTaskAndRollback(String topic, String payload, VoucherOrderMessage message, int retryCount) {
        // 1. 回滚 Redis 预扣
        boolean rolledBack = seckillRedisRollbackService.rollback(
                message.getVoucherId(),
                message.getUserId(),
                "DLT retries exhausted"
        );

        // 2. 构建状态和详情
        String detail = rolledBack
                ? "Kafka consumer retries exhausted, redisRollback=OK"
                : "Kafka consumer retries exhausted, redisRollback=FAILED";
        String status = rolledBack ? STATUS_ROLLBACK_DONE : STATUS_MANUAL;

        // 3. 保存失败任务
        saveOrUpdateFailTask(topic, payload, message, detail, retryCount, status, FAIL_TYPE_NON_RECOVERABLE);

        log.error("Dead-letter task saved, traceId={}, orderId={}, status={}, failureType={}",
                message.getTraceId(), message.getOrderId(), status, FAIL_TYPE_NON_RECOVERABLE);
    }

    /**
     * 批量重试待处理任务
     *
     * 由 VoucherOrderFailTaskRetryScheduler 定时调用。
     * 查询所有 RETRY_PENDING 状态的任务，批量重试。
     *
     * @param batchSize    每批处理数量
     * @param maxRetryCount 最大重试次数
     * @return 成功重试的数量
     */
    @Override
    public int retryPendingTasks(int batchSize, int maxRetryCount) {
        // 查询待重试任务
        List<VoucherOrderFailTask> tasks = list(new QueryWrapper<VoucherOrderFailTask>()
                .eq("status", STATUS_RETRY_PENDING)
                .and(w -> w.isNull("retry_count").or().lt("retry_count", maxRetryCount))
                .orderByAsc("id")
                .last("limit " + Math.max(batchSize, 1)));
        if (tasks == null || tasks.isEmpty()) {
            return 0;
        }

        // 逐个重试
        int successCount = 0;
        for (VoucherOrderFailTask task : tasks) {
            if (retrySingleTask(task, maxRetryCount)) {
                successCount++;
            }
        }
        return successCount;
    }

    /**
     * 根据ID重试单个任务
     */
    @Override
    public boolean retryTaskById(Long taskId, int maxRetryCount) {
        VoucherOrderFailTask task = getById(taskId);
        if (task == null) {
            return false;
        }
        if (STATUS_DONE.equals(task.getStatus())) {
            return true;  // 已经成功了
        }
        return retrySingleTask(task, maxRetryCount);
    }

    /**
     * 标记任务为人工处理
     *
     * @param taskId 任务ID
     * @param note   备注
     * @return 是否成功
     */
    @Override
    public boolean markTaskIgnored(Long taskId, String note) {
        VoucherOrderFailTask task = getById(taskId);
        if (task == null) {
            return false;
        }
        // 先回滚 Redis
        seckillRedisRollbackService.rollback(task.getVoucherId(), task.getUserId(), "manual ignore");
        // 更新状态
        task.setStatus(STATUS_MANUAL);
        task.setFailureType(FAIL_TYPE_NON_RECOVERABLE);
        if (note != null && !note.trim().isEmpty()) {
            task.setErrorMsg("MANUAL_NOTE: " + note.trim());
        }
        return updateById(task);
    }

    /**
     * 重试单个任务
     *
     * @param task         失败任务
     * @param maxRetryCount 最大重试次数
     * @return 是否成功
     */
    private boolean retrySingleTask(VoucherOrderFailTask task, int maxRetryCount) {
        // 1. 更新状态为重试中
        task.setStatus(STATUS_RETRYING);
        task.setFailureType(FAIL_TYPE_RECOVERABLE);
        updateById(task);

        try {
            // 2. 重新发送消息到 Kafka
            kafkaTemplate
                    .send(mainTopic, String.valueOf(task.getUserId()), task.getRawPayload())
                    .get(5, TimeUnit.SECONDS);

            // 3. 成功，更新状态
            task.setStatus(STATUS_DONE);
            task.setErrorMsg("AUTO_RETRY_SUCCESS");
            updateById(task);
            log.info("Fail task auto retry success, taskId={}, traceId={}, orderId={}",
                    task.getId(), task.getTraceId(), task.getOrderId());
            return true;
        } catch (Exception e) {
            // 4. 失败，更新状态
            int nextRetryCount = task.getRetryCount() == null ? 1 : task.getRetryCount() + 1;
            task.setRetryCount(nextRetryCount);
            task.setStatus(nextRetryCount >= maxRetryCount ? STATUS_MANUAL : STATUS_RETRY_PENDING);
            task.setErrorMsg("AUTO_RETRY_FAILED: " + e.getClass().getSimpleName() + ": " + e.getMessage());
            updateById(task);
            log.error("Fail task auto retry failed, taskId={}, traceId={}, orderId={}, retryCount={}, maxRetryCount={}",
                    task.getId(), task.getTraceId(), task.getOrderId(), nextRetryCount, maxRetryCount, e);
            return false;
        }
    }
}