package com.hmdp.mail;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * IMAP 定时拉取调度器测试。
 *
 * @author ethan
 * @date 2026-10-31
 */
class MailReaderSchedulerTest {

    private final MailReceiver mailReceiver = mock(MailReceiver.class);

    private final MailAlertPipelineService pipelineService = mock(MailAlertPipelineService.class);

    /**
     * 构造调度器。
     *
     * @return 调度器
     */
    private MailReaderScheduler scheduler() {
        return new MailReaderScheduler(mailReceiver, pipelineService, new MailAlertProperties());
    }

    /**
     * 构造一封测试邮件。
     *
     * @return 邮件消息
     */
    private MailMessage mail() {
        return new MailMessage("imap-1", "a@163.com", "超卖告警", "内容", LocalDateTime.now());
    }

    @Test
    void testPollDisabledSkipsFetch() {
        MailAlertProperties props = new MailAlertProperties();
        props.getMailReader().setEnabled(false);
        new MailReaderScheduler(mailReceiver, pipelineService, props).poll();
        verify(mailReceiver, never()).fetchNewMails();
    }

    @Test
    void testPollWithoutAccountSkipsFetch() {
        MailAlertProperties props = new MailAlertProperties();
        props.getMailReader().setEnabled(true);
        when(mailReceiver.isConfigured()).thenReturn(false);
        new MailReaderScheduler(mailReceiver, pipelineService, props).poll();
        verify(mailReceiver, never()).fetchNewMails();
    }

    @Test
    void testPollWithNewMailsFeedsPipeline() {
        MailAlertProperties props = new MailAlertProperties();
        props.getMailReader().setEnabled(true);
        when(mailReceiver.isConfigured()).thenReturn(true);
        when(mailReceiver.fetchNewMails()).thenReturn(Arrays.asList(mail(), mail()));

        new MailReaderScheduler(mailReceiver, pipelineService, props).poll();

        verify(pipelineService).process(anyList());
    }

    @Test
    void testPollWithoutNewMailsSkipsPipeline() {
        MailAlertProperties props = new MailAlertProperties();
        props.getMailReader().setEnabled(true);
        when(mailReceiver.isConfigured()).thenReturn(true);
        when(mailReceiver.fetchNewMails()).thenReturn(Collections.emptyList());

        new MailReaderScheduler(mailReceiver, pipelineService, props).poll();

        verify(pipelineService, never()).process(anyList());
    }

    @Test
    void testPollReceiverExceptionDoesNotPropagate() {
        MailAlertProperties props = new MailAlertProperties();
        props.getMailReader().setEnabled(true);
        when(mailReceiver.isConfigured()).thenReturn(true);
        when(mailReceiver.fetchNewMails()).thenThrow(new RuntimeException("imap down"));

        // 调度任务异常必须被吞掉，不允许中断调度线程
        new MailReaderScheduler(mailReceiver, pipelineService, props).poll();

        verify(pipelineService, never()).process(anyList());
    }
}
