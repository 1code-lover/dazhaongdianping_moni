package com.hmdp.mail;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 邮件告警管道服务。
 * 编排完整链路：分级 → 去重 → 按级别分发到各通知渠道，并汇总结果。
 *
 * @author ethan
 * @date 2026-10-31
 */
@Slf4j
@Service
public class MailAlertPipelineService {

    private static final String STATUS_NEW = "NEW";
    private static final String STATUS_DUPLICATED = "DUPLICATED";
    private static final String STATUS_DISABLED = "DISABLED";

    private final MailAlertProperties properties;
    private final MailClassifier classifier;
    private final MailAlertDeduper deduper;
    private final List<NotificationChannel> channels;

    /**
     * 创建管道服务。
     * Spring 会注入容器内全部 NotificationChannel 实现。
     *
     * @param properties 告警配置
     * @param classifier 邮件分级器
     * @param deduper    邮件去重器
     * @param channels   通知渠道列表
     */
    public MailAlertPipelineService(MailAlertProperties properties,
                                    MailClassifier classifier,
                                    MailAlertDeduper deduper,
                                    List<NotificationChannel> channels) {
        this.properties = properties;
        this.classifier = classifier;
        this.deduper = deduper;
        this.channels = channels;
    }

    /**
     * 处理一批邮件。
     *
     * @param mails 邮件列表
     * @return 每封邮件的处理结果，与输入顺序一致
     */
    public List<MailProcessResult> process(List<MailMessage> mails) {
        List<MailProcessResult> results = new ArrayList<>();
        if (!properties.isEnabled()) {
            // 链路总开关关闭时直接标记，不执行任何渠道
            for (MailMessage mail : mails) {
                results.add(MailProcessResult.of(mail, MailLevel.INFO, STATUS_DISABLED));
            }
            return results;
        }
        for (MailMessage mail : mails) {
            results.add(processOne(mail));
        }
        return results;
    }

    /**
     * 处理单封邮件：分级、去重、分发。
     *
     * @param mail 邮件消息
     * @return 处理结果
     */
    private MailProcessResult processOne(MailMessage mail) {
        MailLevel level = classifier.classify(mail);
        MailProcessResult result = MailProcessResult.of(mail, level, STATUS_NEW);
        if (!deduper.tryRegister(mail.getMessageKey())) {
            result.setStatus(STATUS_DUPLICATED);
            return result;
        }
        result.setChannelResults(dispatch(new MailAlert(mail, level, LocalDateTime.now())));
        return result;
    }

    /**
     * 按级别将告警分发到支持的通知渠道。
     * 单渠道异常互相隔离，保证一个渠道失败不影响其余渠道。
     *
     * @param alert 告警事件
     * @return 渠道名到执行结果的映射
     */
    private java.util.Map<String, ChannelResult> dispatch(MailAlert alert) {
        java.util.Map<String, ChannelResult> results = new java.util.LinkedHashMap<>();
        for (NotificationChannel channel : channels) {
            if (!channel.supports(alert.getLevel())) {
                continue;
            }
            results.put(channel.name(), safeNotify(channel, alert));
        }
        return results;
    }

    /**
     * 安全调用单渠道通知。
     *
     * @param channel 通知渠道
     * @param alert   告警事件
     * @return 渠道执行结果，异常时返回 FAILED
     */
    private ChannelResult safeNotify(NotificationChannel channel, MailAlert alert) {
        try {
            return channel.notify(alert);
        } catch (Exception ex) {
            log.warn("通知渠道 {} 发送失败: {}", channel.name(), ex.getMessage());
            return ChannelResult.FAILED;
        }
    }
}
