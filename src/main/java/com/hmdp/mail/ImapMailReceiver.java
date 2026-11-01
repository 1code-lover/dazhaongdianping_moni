package com.hmdp.mail;

import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import javax.mail.Folder;
import javax.mail.Message;
import javax.mail.Part;
import javax.mail.Session;
import javax.mail.Store;
import javax.mail.UIDFolder;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMultipart;

import com.sun.mail.imap.IMAPStore;

/**
 * IMAP 邮件读取实现。
 * 复用 spring.mail 配置的账号与授权码，通过 IMAPS 拉取新增邮件并转换为 MailMessage。
 * 首次拉取建立 UID 基线（历史邮件不告警），之后只返回基线之后的新邮件；
 * messageKey 采用 imap-{uid}，天然幂等，配合去重器避免重复告警。
 * 网易等服务商要求 IMAP 客户端登录前发送 ID 命令，否则报 Unsafe Login，已做兼容。
 *
 * @author ethan
 * @date 2026-10-31
 */
@Slf4j
@Component
public class ImapMailReceiver implements MailReceiver {

    private final JavaMailSender mailSender;

    private final MailAlertProperties properties;

    /** 已记录的最新邮件 UID，0 表示未建立基线 */
    private volatile long lastSeenUid = 0L;

    /**
     * 创建 IMAP 读取器。
     *
     * @param mailSender Spring 邮件发送器（复用其 Session 与账号）
     * @param properties 告警配置
     */
    public ImapMailReceiver(JavaMailSender mailSender, MailAlertProperties properties) {
        this.mailSender = mailSender;
        this.properties = properties;
    }

    /**
     * 判断账号是否配置完整。
     *
     * @return true 表示 SMTP 用户名与授权码均非空
     */
    @Override
    public boolean isConfigured() {
        String[] account = smtpAccount();
        return account != null && !isBlank(account[0]) && !isBlank(account[1]);
    }

    /**
     * 拉取基线之后的新邮件。
     *
     * @return 新邮件列表
     * @throws IllegalStateException 账号未配置
     */
    @Override
    public synchronized List<MailMessage> fetchNewMails() {
        if (!isConfigured()) {
            throw new IllegalStateException("邮箱账号未配置（MAIL_USERNAME / MAIL_AUTH_CODE）");
        }
        MailAlertProperties.MailReaderProps config = properties.getMailReader();
        Folder folder = null;
        try {
            folder = openFolder(config);
            return scanAndConvert(folder, config);
        } catch (Exception ex) {
            log.warn("IMAP 拉取邮件失败: {}", ex.getMessage());
            return new ArrayList<>();
        } finally {
            closeQuietly(folder);
        }
    }

    /**
     * 查看当前基线 UID。
     *
     * @return 最新 UID，0 表示未建立基线
     */
    @Override
    public synchronized long getLastSeenUid() {
        return lastSeenUid;
    }

    /**
     * 重置基线。
     */
    @Override
    public synchronized void resetBaseline() {
        lastSeenUid = 0L;
        log.info("IMAP 读取基线已重置");
    }

    /**
     * 扫描文件夹并转换新邮件。
     * 自己发给自己的邮件（如发信渠道的告警回执）跳过告警，但同样推进基线并标已读，
     * 防止"发信给自己 → IMAP 再读到 → 再发信"的无限循环。
     *
     * @param folder 已打开的邮箱文件夹
     * @param config 读取配置
     * @return 新邮件列表
     * @throws Exception 文件夹操作异常
     */
    private List<MailMessage> scanAndConvert(Folder folder,
                                             MailAlertProperties.MailReaderProps config) throws Exception {
        int count = folder.getMessageCount();
        List<MailMessage> result = new ArrayList<>();
        if (count <= 0) {
            return result;
        }
        TreeMap<Long, Message> candidates = collectCandidates(folder, config, count);
        if (lastSeenUid == 0L) {
            // 首次拉取只建立基线，历史邮件不产生告警
            lastSeenUid = candidates.isEmpty() ? 0L : candidates.lastKey();
            log.info("IMAP 读取基线已建立，当前最新 UID={}", lastSeenUid);
            return result;
        }
        for (Map.Entry<Long, Message> entry : candidates.entrySet()) {
            if (result.size() >= config.getMaxMailsPerPoll()) {
                break;
            }
            consumeCandidate(entry, config, result);
        }
        return result;
    }

    /**
     * 处理单封候选邮件：转换、过滤自发邮件、标记已读、推进基线。
     *
     * @param entry  UID 与邮件
     * @param config 读取配置
     * @param result 结果收集列表
     */
    private void consumeCandidate(Map.Entry<Long, Message> entry,
                                  MailAlertProperties.MailReaderProps config,
                                  List<MailMessage> result) {
        MailMessage mail = toMailMessage(entry.getKey(), entry.getValue());
        if (mail == null) {
            // 解析失败不推进基线，下次拉取重试
            return;
        }
        if (config.isMarkAsRead()) {
            markAsRead(entry.getValue());
        }
        lastSeenUid = entry.getKey();
        if (isSelfSent(mail.getFrom())) {
            log.debug("跳过自发邮件 uid={}（防止告警回环）", entry.getKey());
            return;
        }
        result.add(mail);
    }

    /**
     * 将邮件标记为已读。
     *
     * @param message 目标邮件
     */
    private void markAsRead(Message message) {
        try {
            message.setFlags(new javax.mail.Flags(javax.mail.Flags.Flag.SEEN), true);
        } catch (Exception ex) {
            log.debug("标记已读失败（忽略）: {}", ex.getMessage());
        }
    }

    /**
     * 判断邮件是否为本账号自发（发件人等于 SMTP 认证账号）。
     *
     * @param from 邮件发件人
     * @return true 表示自发邮件
     */
    private boolean isSelfSent(String from) {
        String[] account = smtpAccount();
        if (account == null || isBlank(account[0]) || isBlank(from)) {
            return false;
        }
        return account[0].trim().equalsIgnoreCase(from.trim());
    }

    /**
     * 收集扫描窗口内 UID 大于基线的候选邮件。
     * 扫描窗口为最近 3 倍单次拉取上限（至少 30 封），保证漏拉可被后续轮次补上。
     *
     * @param folder 已打开的文件夹
     * @param config 读取配置
     * @param count  文件夹内邮件总数
     * @return UID 升序的候选邮件表
     * @throws Exception 文件夹操作异常
     */
    private TreeMap<Long, Message> collectCandidates(Folder folder,
                                                     MailAlertProperties.MailReaderProps config,
                                                     int count) throws Exception {
        int scanSize = Math.min(count, Math.max(config.getMaxMailsPerPoll() * 3, 30));
        Message[] window = folder.getMessages(count - scanSize + 1, count);
        UIDFolder uidFolder = (UIDFolder) folder;
        TreeMap<Long, Message> candidates = new TreeMap<>();
        for (Message message : window) {
            long uid = uidFolder.getUID(message);
            if (uid > lastSeenUid) {
                candidates.put(uid, message);
            }
        }
        return candidates;
    }

    /**
     * 打开 IMAP 文件夹。
     * 测试可覆写此方法注入模拟 Folder。
     *
     * @param config 读取配置
     * @return 已打开的文件夹
     * @throws Exception 连接或打开失败
     */
    protected Folder openFolder(MailAlertProperties.MailReaderProps config) throws Exception {
        JavaMailSenderImpl impl = requireImpl();
        String[] account = smtpAccount();
        if (account == null) {
            throw new IllegalStateException("邮箱账号未配置（MAIL_USERNAME / MAIL_AUTH_CODE）");
        }
        Session session = impl.getSession();
        Store store = session.getStore("imaps");
        store.connect(config.getHost(), config.getPort(), account[0], account[1]);
        sendImapIdIfRequired(store, session);
        Folder folder = store.getFolder(config.getFolder());
        folder.open(config.isMarkAsRead() ? Folder.READ_WRITE : Folder.READ_ONLY);
        return folder;
    }

    /**
     * 兼容网易邮箱的 IMAP ID 命令要求。
     * 未发送 ID 命令时网易会返回 Unsafe Login 拒绝连接。
     *
     * @param store   IMAP 存储
     * @param session 邮件会话
     */
    private void sendImapIdIfRequired(Store store, Session session) {
        try {
            if (store instanceof IMAPStore) {
                IMAPStore imapStore = (IMAPStore) store;
                if (imapStore.hasCapability("ID")) {
                    Map<String, String> clientInfo = new java.util.HashMap<>();
                    clientInfo.put("name", "hmdp-mail-alert");
                    clientInfo.put("version", "1.0");
                    imapStore.id(clientInfo);
                }
            }
        } catch (Exception ex) {
            // ID 命令失败不阻断主流程，由后续登录结果决定
            log.debug("IMAP ID 命令发送失败（忽略）: {}", ex.getMessage());
        }
    }

    /**
     * 将服务器邮件转换为链路消息模型。
     *
     * @param uid     邮件 UID
     * @param message 服务器邮件
     * @return MailMessage，解析失败返回 null
     */
    private MailMessage toMailMessage(long uid, Message message) {
        try {
            String subject = message.getSubject() == null ? "（无主题）" : message.getSubject();
            String from = message.getFrom() == null || message.getFrom().length == 0
                    ? "unknown" : ((InternetAddress) message.getFrom()[0]).getAddress();
            return new MailMessage("imap-" + uid, from, subject,
                    extractText(message), toLocalDateTime(message.getSentDate()));
        } catch (Exception ex) {
            log.warn("解析邮件 uid={} 失败: {}", uid, ex.getMessage());
            return null;
        }
    }

    /**
     * 提取邮件正文文本。
     * 优先 text/plain；multipart 递归取第一个文本段；text/html 做简单标签剥离。
     *
     * @param part 邮件部件
     * @return 正文文本
     * @throws Exception 解析异常
     */
    protected String extractText(Part part) throws Exception {
        if (part.isMimeType("text/plain")) {
            return String.valueOf(part.getContent());
        }
        if (part.isMimeType("text/html")) {
            return String.valueOf(part.getContent()).replaceAll("<[^>]+>", " ");
        }
        if (part.isMimeType("multipart/*")) {
            MimeMultipart multipart = (MimeMultipart) part.getContent();
            for (int i = 0; i < multipart.getCount(); i++) {
                String text = extractText(multipart.getBodyPart(i));
                if (!text.isEmpty()) {
                    return text;
                }
            }
        }
        return "";
    }

    /**
     * 读取 SMTP 账号信息。
     * 发送器未初始化时返回 null（视为未配置），测试可覆写。
     *
     * @return [用户名, 授权码]，未初始化时为 null
     */
    protected String[] smtpAccount() {
        if (!(mailSender instanceof JavaMailSenderImpl)) {
            return null;
        }
        JavaMailSenderImpl impl = (JavaMailSenderImpl) mailSender;
        return new String[]{impl.getUsername(), impl.getPassword()};
    }

    /**
     * 获取 JavaMailSenderImpl 实例。
     *
     * @return 发送器实现
     * @throws IllegalStateException 类型不符时抛出
     */
    private JavaMailSenderImpl requireImpl() {
        if (!(mailSender instanceof JavaMailSenderImpl)) {
            throw new IllegalStateException("JavaMailSender 未按预期初始化");
        }
        return (JavaMailSenderImpl) mailSender;
    }

    /**
     * Date 转 LocalDateTime，空值回退当前时间。
     *
     * @param date 邮件时间
     * @return 本地时间
     */
    private LocalDateTime toLocalDateTime(Date date) {
        return date == null
                ? LocalDateTime.now()
                : LocalDateTime.ofInstant(date.toInstant(), ZoneId.systemDefault());
    }

    /**
     * 静默关闭文件夹。
     *
     * @param folder 文件夹，可为 null
     */
    private void closeQuietly(Folder folder) {
        if (folder != null && folder.isOpen()) {
            try {
                folder.close(false);
            } catch (Exception ex) {
                log.debug("关闭 IMAP 文件夹失败（忽略）: {}", ex.getMessage());
            }
        }
    }

    /**
     * 判断字符串是否为空白。
     *
     * @param value 待检查字符串
     * @return true 表示空白
     */
    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
