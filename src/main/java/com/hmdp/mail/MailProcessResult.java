package com.hmdp.mail;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 单封邮件的处理结果。
 * 汇总分级结果与各渠道执行结果，供接口直接返回展示。
 *
 * @author ethan
 * @date 2026-10-31
 */
@Data
public class MailProcessResult {

    /** 邮件唯一键 */
    private String messageKey;

    /** 邮件主题 */
    private String subject;

    /** 分级结果 */
    private MailLevel level;

    /** 处理状态：NEW 首次处理；DUPLICATED 重复跳过；DISABLED 链路关闭 */
    private String status;

    /** 渠道名 → 执行结果，保持插入顺序 */
    private Map<String, ChannelResult> channelResults = new LinkedHashMap<>();

    /**
     * 构建基础结果（不含渠道明细）。
     *
     * @param mail   邮件消息
     * @param level  分级结果
     * @param status 处理状态
     * @return 基础结果对象
     */
    public static MailProcessResult of(MailMessage mail, MailLevel level, String status) {
        MailProcessResult result = new MailProcessResult();
        result.setMessageKey(mail.getMessageKey());
        result.setSubject(mail.getSubject());
        result.setLevel(level);
        result.setStatus(status);
        return result;
    }
}
