package com.hmdp.mail;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * IMAP 邮件定时拉取调度器。
 * 按配置间隔拉取邮箱新增邮件并送入告警管道；账号未配置或开关关闭时静默跳过。
 *
 * @author ethan
 * @date 2026-10-31
 */
@Slf4j
@Component
public class MailReaderScheduler {

    private final MailReceiver mailReceiver;

    private final MailAlertPipelineService pipelineService;

    private final MailAlertProperties properties;

    /**
     * 创建调度器。
     *
     * @param mailReceiver    邮件读取器
     * @param pipelineService 告警管道
     * @param properties      告警配置
     */
    public MailReaderScheduler(MailReceiver mailReceiver,
                               MailAlertPipelineService pipelineService,
                               MailAlertProperties properties) {
        this.mailReceiver = mailReceiver;
        this.pipelineService = pipelineService;
        this.properties = properties;
    }

    /**
     * 定时拉取入口。
     * 间隔由 app.notify.mail-reader.poll-interval-ms 配置。
     */
    @Scheduled(fixedDelayString = "${app.notify.mail-reader.poll-interval-ms:60000}")
    public void poll() {
        if (!properties.getMailReader().isEnabled() || !mailReceiver.isConfigured()) {
            return;
        }
        try {
            List<MailMessage> mails = mailReceiver.fetchNewMails();
            if (!mails.isEmpty()) {
                log.info("IMAP 拉取到 {} 封新邮件，进入告警链路", mails.size());
                pipelineService.process(mails);
            }
        } catch (Exception ex) {
            // 定时任务异常只记录，不允许中断调度
            log.warn("IMAP 定时拉取异常: {}", ex.getMessage());
        }
    }
}
