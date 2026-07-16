package com.hmdp.controller;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.entity.VoucherOrderFailTask;
import com.hmdp.service.IVoucherOrderFailTaskService;
import com.hmdp.utils.PageUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

/**
 * 秒杀订单失败任务运维控制器。
 * 提供管理员查询、重试和人工忽略失败任务的能力。
 *
 * @author ethan
 * @date 2026-07-16
 */
@RestController
@RequestMapping("/voucher-order-fail-task")
public class VoucherOrderFailTaskController {

    @Resource
    private IVoucherOrderFailTaskService failTaskService;

    @Value("${app.kafka.fail-task.max-retry-count:5}")
    private int maxRetryCount;

    /**
     * 分页查询失败任务。
     *
     * @param current 当前页码
     * @param size 每页数量
     * @param status 任务状态
     * @param traceId 链路追踪 ID
     * @return 失败任务分页结果
     */
    @GetMapping("/page")
    public Result queryFailTasks(
            @RequestParam(value = "current", defaultValue = "1") Integer current,
            @RequestParam(value = "size", required = false) Integer size,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "traceId", required = false) String traceId) {
        Page<VoucherOrderFailTask> page = failTaskService.query()
                .eq(StrUtil.isNotBlank(status), "status", status)
                .like(StrUtil.isNotBlank(traceId), "trace_id", traceId)
                .orderByDesc("id")
                .page(new Page<>(PageUtils.normalizePageNumber(current), PageUtils.normalizePageSize(size)));
        return Result.ok(page.getRecords(), page.getTotal());
    }

    /**
     * 手动重试指定失败任务。
     *
     * @param id 失败任务 ID
     * @return 重试执行结果
     */
    @PostMapping("/{id}/retry")
    public Result retryTask(@PathVariable("id") Long id) {
        VoucherOrderFailTask task = failTaskService.getById(id);
        if (task == null) {
            return Result.fail("任务不存在");
        }
        boolean success = failTaskService.retryTaskById(id, maxRetryCount);
        return success ? Result.ok("重试请求已执行") : Result.fail("重试失败，请查看任务错误信息");
    }

    /**
     * 将失败任务标记为人工处理。
     *
     * @param id 失败任务 ID
     * @param note 人工处理备注
     * @return 标记结果
     */
    @PostMapping("/{id}/ignore")
    public Result ignoreTask(@PathVariable("id") Long id,
                             @RequestParam(value = "note", required = false) String note) {
        boolean success = failTaskService.markTaskIgnored(id, note);
        return success ? Result.ok("任务已标记为人工处理") : Result.fail("任务不存在");
    }
}
