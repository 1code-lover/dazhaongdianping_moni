package com.hmdp.mail;

import java.util.List;

/**
 * 邮件读取接口。
 * 抽象"真实邮箱拉取"入口，Mock 邮件源与 IMAP 实现共用同一链路下游。
 *
 * @author ethan
 * @date 2026-10-31
 */
public interface MailReceiver {

    /**
     * 是否已完成账号配置（SMTP 用户名与授权码非空）。
     *
     * @return true 表示可尝试拉取
     */
    boolean isConfigured();

    /**
     * 拉取新增邮件。
     * 首次调用建立基线（记录当前最新邮件位置，不产生告警），之后每次返回基线之后的新邮件。
     *
     * @return 新邮件列表，按接收顺序排列，可能为空
     * @throws IllegalStateException 账号未配置时调用
     */
    List<MailMessage> fetchNewMails();

    /**
     * 当前已记录的最新邮件位置（UID），0 表示尚未建立基线。
     *
     * @return 最新 UID
     */
    long getLastSeenUid();

    /**
     * 重置基线，下次拉取将重新建立基线。
     */
    void resetBaseline();
}
