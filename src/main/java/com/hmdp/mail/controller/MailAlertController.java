package com.hmdp.mail.controller;

import com.hmdp.dto.Result;
import com.hmdp.mail.MailAlertPipelineService;
import com.hmdp.mail.MailAlertProperties;
import com.hmdp.mail.MailCreateRequest;
import com.hmdp.mail.MailLevel;
import com.hmdp.mail.MailMessage;
import com.hmdp.mail.MailProcessResult;
import com.hmdp.mail.MailReceiver;
import com.hmdp.mail.MockMailSource;
import com.hmdp.mail.SseAlertBroadcaster;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 邮件告警链路演示接口。
 * 提供 Mock 邮件造数、一键跑通全链路、SSE 弹窗订阅等端点。
 *
 * @author ethan
 * @date 2026-10-31
 */
@Slf4j
@RestController
@RequestMapping("/mail-alert")
public class MailAlertController {

    private static final String DEFAULT_FROM = "demo@hmdp.example";

    private final MockMailSource mockMailSource;

    private final MailAlertPipelineService pipelineService;

    private final SseAlertBroadcaster broadcaster;

    private final MailAlertProperties properties;

    private final MailReceiver mailReceiver;

    /**
     * 创建邮件告警控制器。
     *
     * @param mockMailSource   Mock 邮件源
     * @param pipelineService  告警管道服务
     * @param broadcaster      SSE 广播器
     * @param properties       告警配置
     * @param mailReceiver     IMAP 邮件读取器
     */
    public MailAlertController(MockMailSource mockMailSource,
                               MailAlertPipelineService pipelineService,
                               SseAlertBroadcaster broadcaster,
                               MailAlertProperties properties,
                               MailReceiver mailReceiver) {
        this.mockMailSource = mockMailSource;
        this.pipelineService = pipelineService;
        this.broadcaster = broadcaster;
        this.properties = properties;
        this.mailReceiver = mailReceiver;
    }

    /**
     * 查看预置 Mock 邮件。
     *
     * @return 预置邮件列表
     */
    @GetMapping("/mock/emails")
    public Result listPresetMails() {
        return Result.ok(mockMailSource.presetMails());
    }

    /**
     * 造一封 Mock 邮件并立即走完整链路。
     *
     * @param request 邮件内容
     * @return 处理结果
     */
    @PostMapping("/mock/emails")
    public Result createAndProcessMail(@RequestBody MailCreateRequest request) {
        MailMessage mail = mockMailSource.createCustomMail(
                firstNonBlank(request.getFrom(), DEFAULT_FROM),
                firstNonBlank(request.getSubject(), "（无主题）"),
                firstNonBlank(request.getContent(), "（无正文）"));
        List<MailProcessResult> results = pipelineService.process(Collections.singletonList(mail));
        return Result.ok(results);
    }

    /**
     * 一键跑通全链路：预置邮件 → 分级 → 去重 → 分发。
     * 再次调用时预置邮件因去重而全部 DUPLICATED，可重置后演示。
     *
     * @return 每封邮件的处理结果
     */
    @PostMapping("/pipeline/run")
    public Result runPipeline() {
        List<MailProcessResult> results = pipelineService.process(mockMailSource.presetMails());
        return Result.ok(results);
    }

    /**
     * SSE 订阅端点，浏览器通过 EventSource 接收弹窗推送。
     *
     * @return SSE 连接
     */
    @GetMapping(value = "/events/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribeEvents() {
        return broadcaster.register();
    }

    /**
     * 手动触发一条测试弹窗，验证浏览器订阅链路。
     *
     * @return 发送结果与在线订阅数
     */
    @PostMapping("/popup/test")
    public Result testPopup() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("level", MailLevel.WARNING.name());
        payload.put("title", "[WARNING] 弹窗测试消息");
        payload.put("content", "这是一条手动触发的测试弹窗。");
        payload.put("from", DEFAULT_FROM);
        payload.put("time", LocalDateTime.now().toString());
        broadcaster.broadcast("mail-alert", payload);
        payload.put("onlineClients", broadcaster.onlineCount());
        return Result.ok(payload);
    }

    /**
     * 查看告警链路当前配置与在线订阅数。
     *
     * @return 配置摘要
     */
    @GetMapping("/config")
    public Result config() {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("enabled", properties.isEnabled());
        summary.put("phoneAlarmEnabled", properties.getPhoneAlarm().isEnabled());
        summary.put("dingTalkConfigured", !properties.getDingTalk().getWebhook().trim().isEmpty());
        summary.put("mailSenderReady", mailSenderReady());
        summary.put("mailReaderEnabled", properties.getMailReader().isEnabled());
        summary.put("mailReaderConfigured", mailReceiver.isConfigured());
        summary.put("onlineClients", broadcaster.onlineCount());
        return Result.ok(summary);
    }

    /**
     * 手动触发一次 IMAP 拉取并送入告警链路。
     * 不受 mail-reader.enabled 开关限制，便于配置完成后立即验证。
     *
     * @return 拉取数量与每封邮件的处理结果
     */
    @PostMapping("/mail/poll")
    public Result pollMailbox() {
        if (!mailReceiver.isConfigured()) {
            return Result.fail("邮箱账号未配置，请设置 MAIL_USERNAME 与 MAIL_AUTH_CODE 环境变量后重启");
        }
        List<MailMessage> mails = mailReceiver.fetchNewMails();
        List<MailProcessResult> results = pipelineService.process(mails);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("fetched", mails.size());
        data.put("lastSeenUid", mailReceiver.getLastSeenUid());
        data.put("results", results);
        return Result.ok(data);
    }

    /**
     * 查看 IMAP 读取器状态。
     *
     * @return 开关、服务器、基线 UID 等信息
     */
    @GetMapping("/mail/reader")
    public Result readerStatus() {
        MailAlertProperties.MailReaderProps config = properties.getMailReader();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("enabled", config.isEnabled());
        data.put("configured", mailReceiver.isConfigured());
        data.put("host", config.getHost());
        data.put("port", config.getPort());
        data.put("folder", config.getFolder());
        data.put("pollIntervalMs", config.getPollIntervalMs());
        data.put("lastSeenUid", mailReceiver.getLastSeenUid());
        return Result.ok(data);
    }

    /**
     * 重置 IMAP 读取基线。
     * 重置后首次拉取将重新记录当前最新位置，历史邮件不会产生告警。
     *
     * @return 操作结果
     */
    @PostMapping("/mail/reader/reset-baseline")
    public Result resetBaseline() {
        mailReceiver.resetBaseline();
        return Result.ok();
    }

    /**
     * 判断发信渠道配置是否完整。
     *
     * @return true 表示 SMTP 账号与收件人均已配置
     */
    private boolean mailSenderReady() {
        boolean accountConfigured = mailReceiver.isConfigured();
        return accountConfigured && !properties.getMailSender().getTo().trim().isEmpty();
    }

    /**
     * 取第一个非空字符串。
     *
     * @param value        待检查值
     * @param defaultValue 空时的默认值
     * @return 非空字符串
     */
    private String firstNonBlank(String value, String defaultValue) {
        return value == null || value.trim().isEmpty() ? defaultValue : value;
    }
}
