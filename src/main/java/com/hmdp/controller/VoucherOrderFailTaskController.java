package com.hmdp.controller;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.entity.VoucherOrderFailTask;
import com.hmdp.service.IVoucherOrderFailTaskService;
import com.hmdp.utils.SystemConstants;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

@RestController
@RequestMapping("/voucher-order-fail-task")
public class VoucherOrderFailTaskController {

    @Resource
    private IVoucherOrderFailTaskService failTaskService;

    @Value("${app.kafka.fail-task.max-retry-count:5}")
    private int maxRetryCount;

    @GetMapping("/page")
    public Result queryFailTasks(
            @RequestParam(value = "current", defaultValue = "1") Integer current,
            @RequestParam(value = "size", required = false) Integer size,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "traceId", required = false) String traceId) {
        int pageSize = size == null || size <= 0 ? SystemConstants.MAX_PAGE_SIZE : size;
        Page<VoucherOrderFailTask> page = failTaskService.query()
                .eq(StrUtil.isNotBlank(status), "status", status)
                .like(StrUtil.isNotBlank(traceId), "trace_id", traceId)
                .orderByDesc("id")
                .page(new Page<>(current, pageSize));
        return Result.ok(page.getRecords(), page.getTotal());
    }

    @PostMapping("/{id}/retry")
    public Result retryTask(@PathVariable("id") Long id) {
        VoucherOrderFailTask task = failTaskService.getById(id);
        if (task == null) {
            return Result.fail("任务不存在");
        }
        boolean success = failTaskService.retryTaskById(id, maxRetryCount);
        return success ? Result.ok("重试请求已执行") : Result.fail("重试失败，请查看任务错误信息");
    }

    @PostMapping("/{id}/ignore")
    public Result ignoreTask(@PathVariable("id") Long id,
                             @RequestParam(value = "note", required = false) String note) {
        boolean success = failTaskService.markTaskIgnored(id, note);
        return success ? Result.ok("任务已标记为人工处理") : Result.fail("任务不存在");
    }
}
