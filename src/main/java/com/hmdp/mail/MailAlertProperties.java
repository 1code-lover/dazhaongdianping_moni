package com.hmdp.mail;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 邮件告警链路配置。
 * 绑定 application.yaml 中 app.notify 前缀，关键词用于邮件分级。
 *
 * @author ethan
 * @date 2026-10-31
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.notify")
public class MailAlertProperties {

    /** 链路总开关 */
    private boolean enabled = true;

    /** 邮件分级关键词配置 */
    private MailKeywords mail = new MailKeywords();

    /** 手机闹钟渠道配置 */
    private PhoneAlarm phoneAlarm = new PhoneAlarm();

    /** 钉钉渠道配置 */
    private DingTalk dingTalk = new DingTalk();

    /** 邮件发信渠道配置 */
    private MailSenderProps mailSender = new MailSenderProps();

    /** IMAP 邮件读取配置 */
    private MailReaderProps mailReader = new MailReaderProps();

    /** 日程提醒配置 */
    private ReminderProps reminder = new ReminderProps();

    /**
     * 分级关键词配置项。
     *
     * @author ethan
     * @date 2026-10-31
     */
    @Data
    public static class MailKeywords {

        /** 命中即 CRITICAL 的关键词，逗号分隔 */
        private String criticalKeywords = "超卖,库存不一致,熔断,宕机,资损,OOM";

        /** 命中即 WARNING 的关键词，逗号分隔 */
        private String warningKeywords = "积压,延迟升高,重试失败,慢查询,磁盘告警";
    }

    /**
     * 手机闹钟渠道配置项。
     *
     * @author ethan
     * @date 2026-10-31
     */
    @Data
    public static class PhoneAlarm {

        /** 渠道开关，默认关闭避免 CI 环境误触发 */
        private boolean enabled = false;

        /** adb 可执行文件路径 */
        private String adbPath = "adb";

        /** 指定设备序列号，空则使用 adb 默认设备 */
        private String deviceSerial = "";

        /** 系统时钟应用包名 */
        private String clockPackage = "com.android.deskclock";

        /** 闹钟延迟分钟数 */
        private int delayMinutes = 5;
    }

    /**
     * 钉钉渠道配置项。
     *
     * @author ethan
     * @date 2026-10-31
     */
    @Data
    public static class DingTalk {

        /** 渠道开关 */
        private boolean enabled = true;

        /** 群机器人 webhook 地址，未配置时渠道跳过 */
        private String webhook = "";
    }

    /**
     * 邮件发信渠道配置项。
     * SMTP 连接参数在 spring.mail 下配置，此处只管收件人与开关。
     *
     * @author ethan
     * @date 2026-10-31
     */
    @Data
    public static class MailSenderProps {

        /** 渠道开关 */
        private boolean enabled = true;

        /** 告警收件邮箱，未配置时渠道跳过 */
        private String to = "";
    }

    /**
     * IMAP 邮件读取配置项。
     * 账号密码复用 spring.mail 的 username 与授权码。
     *
     * @author ethan
     * @date 2026-10-31
     */
    @Data
    public static class MailReaderProps {

        /** 定时拉取开关（手动拉取接口不受此开关限制） */
        private boolean enabled = false;

        /** IMAP 服务器地址 */
        private String host = "imap.163.com";

        /** IMAP 端口（SSL） */
        private int port = 993;

        /** 拉取的邮箱文件夹 */
        private String folder = "INBOX";

        /** 定时拉取间隔毫秒 */
        private long pollIntervalMs = 60000;

        /** 单次最多拉取封数 */
        private int maxMailsPerPoll = 20;

        /** 拉取后是否标记为已读 */
        private boolean markAsRead = true;
    }

    /**
     * 日程提醒配置项。
     *
     * @author ethan
     * @date 2026-11-06
     */
    @Data
    public static class ReminderProps {

        /** 日程提醒开关 */
        private boolean enabled = true;

        /** 到期轮询间隔毫秒 */
        private long pollIntervalMs = 30000;
    }
}
