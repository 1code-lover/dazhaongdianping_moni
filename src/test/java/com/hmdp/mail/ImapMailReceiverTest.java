package com.hmdp.mail;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * IMAP 读取器测试。
 * 通过覆写 openFolder 与 smtpAccount 注入模拟文件夹与账号，不依赖真实邮箱。
 * UID 期望值：FolderStub 的 UID 从 1 开始按追加顺序递增。
 *
 * @author ethan
 * @date 2026-10-31
 */
class ImapMailReceiverTest {

    /**
     * 构造注入模拟文件夹与账号的读取器。
     *
     * @param folder   模拟文件夹
     * @param username SMTP 用户名
     * @param password SMTP 授权码
     * @return 读取器
     */
    private ImapMailReceiver receiver(FolderStub folder, String username, String password) {
        return new ImapMailReceiver(null, new MailAlertProperties()) {
            @Override
            protected javax.mail.Folder openFolder(MailAlertProperties.MailReaderProps config) {
                return folder.folder();
            }

            @Override
            protected String[] smtpAccount() {
                return new String[]{username, password};
            }
        };
    }

    @Test
    void testIsConfiguredRequiresUsernameAndPassword() {
        MailAlertProperties props = new MailAlertProperties();
        ImapMailReceiver receiver = new ImapMailReceiver(null, props);
        assertFalse(receiver.isConfigured());
    }

    @Test
    void testFetchWithoutConfiguredAccountThrows() {
        ImapMailReceiver receiver = receiver(new FolderStub(0), "", "");
        assertThrows(IllegalStateException.class, receiver::fetchNewMails);
    }

    @Test
    void testFirstFetchEstablishesBaselineReturnsEmpty() {
        FolderStub folder = new FolderStub(2);
        ImapMailReceiver receiver = receiver(folder, "a@163.com", "code");

        List<MailMessage> mails = receiver.fetchNewMails();

        assertTrue(mails.isEmpty());
        assertEquals(2L, receiver.getLastSeenUid());
    }

    @Test
    void testFetchReturnsNewMailsAfterBaseline() {
        FolderStub folder = new FolderStub(3);
        ImapMailReceiver receiver = receiver(folder, "a@163.com", "code");
        receiver.fetchNewMails();
        folder.appendMessage("[严重告警] 库存超卖", "boss@163.com", "检测到超卖，请处理");

        List<MailMessage> mails = receiver.fetchNewMails();

        assertEquals(1, mails.size());
        assertEquals("imap-4", mails.get(0).getMessageKey());
        assertEquals("boss@163.com", mails.get(0).getFrom());
        assertTrue(mails.get(0).getSubject().contains("超卖"));
        assertTrue(mails.get(0).getContent().contains("超卖"));
    }

    @Test
    void testFetchCapsToMaxMailsPerPollAndResumesNextRound() {
        MailAlertProperties props = new MailAlertProperties();
        props.getMailReader().setMaxMailsPerPoll(2);
        FolderStub folder = new FolderStub(5, props);
        ImapMailReceiver receiver = new ImapMailReceiver(null, props) {
            @Override
            protected javax.mail.Folder openFolder(MailAlertProperties.MailReaderProps config) {
                return folder.folder();
            }

            @Override
            protected String[] smtpAccount() {
                return new String[]{"a@163.com", "code"};
            }
        };
        // 基线 = 5 封历史邮件，UID=5
        receiver.fetchNewMails();
        folder.appendMessage("积压告警一", "m@163.com", "积压1");
        folder.appendMessage("积压告警二", "m@163.com", "积压2");
        folder.appendMessage("积压告警三", "m@163.com", "积压3");

        // 单次上限 2 封：第一批 UID 6、7，基线推进到 7
        List<MailMessage> firstBatch = receiver.fetchNewMails();
        assertEquals(2, firstBatch.size());
        assertEquals(7L, receiver.getLastSeenUid());

        // 第二批补上剩余的 UID 8
        List<MailMessage> secondBatch = receiver.fetchNewMails();
        assertEquals(1, secondBatch.size());
        assertEquals("imap-8", secondBatch.get(0).getMessageKey());
        assertEquals(8L, receiver.getLastSeenUid());
    }

    @Test
    void testResetBaselineClearsLastSeenUid() {
        ImapMailReceiver receiver = receiver(new FolderStub(2), "a@163.com", "code");
        receiver.fetchNewMails();
        receiver.resetBaseline();
        assertEquals(0L, receiver.getLastSeenUid());
    }

    @Test
    void testSelfSentMailSkippedButBaselineAdvances() {
        // 发信渠道发给自己的告警回执：不触发告警，但基线推进防止回环
        FolderStub folder = new FolderStub(3);
        ImapMailReceiver receiver = receiver(folder, "a@163.com", "code");
        receiver.fetchNewMails();
        folder.appendMessage("[CRITICAL] 库存超卖", "a@163.com", "自发回执");

        List<MailMessage> mails = receiver.fetchNewMails();

        assertTrue(mails.isEmpty());
        assertEquals(4L, receiver.getLastSeenUid());
    }

    @Test
    void testSelfSentSkippedWhileOthersStillProcessed() {
        // 自发邮件与外部邮件混合时，只告警外部邮件
        FolderStub folder = new FolderStub(3);
        ImapMailReceiver receiver = receiver(folder, "a@163.com", "code");
        receiver.fetchNewMails();
        folder.appendMessage("[CRITICAL] 回执", "a@163.com", "自发回执");
        folder.appendMessage("[严重告警] 外部超卖告警", "monitor@other.com", "检测到超卖");

        List<MailMessage> mails = receiver.fetchNewMails();

        assertEquals(1, mails.size());
        assertEquals("monitor@other.com", mails.get(0).getFrom());
        assertEquals(5L, receiver.getLastSeenUid());
    }

    @Test
    void testExtractTextFromPlainPart() throws Exception {
        ImapMailReceiver receiver = receiver(new FolderStub(0), "a@163.com", "code");
        javax.mail.Part part = org.mockito.Mockito.mock(javax.mail.Part.class);
        org.mockito.Mockito.when(part.isMimeType("text/plain")).thenReturn(true);
        org.mockito.Mockito.when(part.getContent()).thenReturn("磁盘告警，使用率95%");

        assertEquals("磁盘告警，使用率95%", receiver.extractText(part));
    }

    @Test
    void testExtractTextFromHtmlPartStripsTags() throws Exception {
        ImapMailReceiver receiver = receiver(new FolderStub(0), "a@163.com", "code");
        javax.mail.Part part = org.mockito.Mockito.mock(javax.mail.Part.class);
        org.mockito.Mockito.when(part.isMimeType("text/plain")).thenReturn(false);
        org.mockito.Mockito.when(part.isMimeType("text/html")).thenReturn(true);
        org.mockito.Mockito.when(part.getContent()).thenReturn("<b>慢查询</b>告警");

        String text = receiver.extractText(part);

        assertFalse(text.contains("<b>"));
        assertTrue(text.contains("慢查询"));
    }
}
