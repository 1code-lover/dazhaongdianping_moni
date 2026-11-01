package com.hmdp.mail;

import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.mail.Folder;
import javax.mail.Message;
import javax.mail.UIDFolder;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

/**
 * 测试用模拟邮箱文件夹。
 * 基于 Mockito 动态桩实现 Folder 与 UIDFolder，邮件 UID 使用消息号模拟，追加后递增。
 *
 * @author ethan
 * @date 2026-10-31
 */
class FolderStub {

    private final Folder folder;

    private final List<Message> messages = new ArrayList<>();

    /**
     * 创建指定数量历史邮件的模拟文件夹。
     *
     * @param initialCount 初始邮件数，UID 从 1 开始
     */
    FolderStub(int initialCount) {
        this(initialCount, null);
    }

    /**
     * 创建指定数量历史邮件的模拟文件夹（带配置占位）。
     *
     * @param initialCount 初始邮件数
     * @param props        配置（当前仅保留签名一致性）
     */
    FolderStub(int initialCount, MailAlertProperties props) {
        folder = mock(Folder.class, withSettings().lenient().extraInterfaces(UIDFolder.class));
        for (int i = 1; i <= initialCount; i++) {
            appendMessage("历史邮件" + i, "old@163.com", "旧内容" + i);
        }
        stubBehaviors();
    }

    /**
     * 追加一封新邮件，UID 递增。
     *
     * @param subject 主题
     * @param from    发件人
     * @param content 正文
     */
    void appendMessage(String subject, String from, String content) {
        long uid = messages.size() + 1L;
        Message message = mock(Message.class, withSettings().lenient());
        Mockito.when(message.getMessageNumber()).thenReturn((int) uid);
        try {
            Mockito.when(message.getSubject()).thenReturn(subject);
            Mockito.when(message.getFrom()).thenReturn(new javax.mail.Address[]{new javax.mail.internet.InternetAddress(from)});
            Mockito.when(message.getSentDate()).thenReturn(new Date());
            Mockito.when(message.getContent()).thenReturn(content);
            Mockito.when(message.isMimeType("text/plain")).thenReturn(true);
        } catch (Exception ex) {
            throw new IllegalStateException("桩消息初始化失败", ex);
        }
        messages.add(message);
    }

    /**
     * 提供接收器使用的 Folder 模拟实例。
     *
     * @return Folder 模拟
     */
    javax.mail.Folder folder() {
        return folder;
    }

    /**
     * 桩定计数、窗口取信、UID 查询与开关状态。
     */
    private void stubBehaviors() {
        try {
            Mockito.when(folder.getMessageCount()).thenAnswer(inv -> messages.size());
            Mockito.when(folder.getMessages(anyInt(), anyInt())).thenAnswer(inv -> {
                int from = inv.getArgument(0);
                int to = inv.getArgument(1);
                return messages.subList(from - 1, to).toArray(new Message[0]);
            });
            Mockito.when(((UIDFolder) folder).getUID(any(Message.class)))
                    .thenAnswer(inv -> (long) ((Message) inv.getArgument(0)).getMessageNumber());
        } catch (Exception ex) {
            throw new IllegalStateException("桩文件夹初始化失败", ex);
        }
        Mockito.when(folder.isOpen()).thenReturn(true);
    }
}
