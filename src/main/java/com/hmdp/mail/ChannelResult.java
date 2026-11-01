package com.hmdp.mail;

/**
 * 通知渠道执行结果。
 * 单渠道失败不影响其他渠道，结果只做汇总展示。
 *
 * @author ethan
 * @date 2026-10-31
 */
public enum ChannelResult {

    /** 已发送 */
    SENT,

    /** 跳过：开关关闭、依赖缺失（如未配 webhook、无设备） */
    SKIPPED,

    /** 失败：执行异常，已记录日志 */
    FAILED
}
