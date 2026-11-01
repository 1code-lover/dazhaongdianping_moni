package com.hmdp.mail;

import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * 邮件分级器。
 * 按配置关键词对邮件主题与正文匹配分级，优先级 CRITICAL &gt; WARNING &gt; INFO。
 *
 * @author ethan
 * @date 2026-10-31
 */
@Component
public class MailClassifier {

    private final MailAlertProperties properties;

    /**
     * 创建邮件分级器。
     *
     * @param properties 告警配置
     */
    public MailClassifier(MailAlertProperties properties) {
        this.properties = properties;
    }

    /**
     * 对一封邮件分级。
     *
     * @param mail 邮件消息
     * @return 告警级别，未命中任何关键词时为 INFO
     */
    public MailLevel classify(MailMessage mail) {
        String text = joinMailText(mail);
        if (containsAny(text, splitKeywords(properties.getMail().getCriticalKeywords()))) {
            return MailLevel.CRITICAL;
        }
        if (containsAny(text, splitKeywords(properties.getMail().getWarningKeywords()))) {
            return MailLevel.WARNING;
        }
        return MailLevel.INFO;
    }

    /**
     * 拼接主题与正文用于关键词匹配。
     *
     * @param mail 邮件消息
     * @return 主题加正文的合并文本
     */
    private String joinMailText(MailMessage mail) {
        String subject = mail.getSubject() == null ? "" : mail.getSubject();
        String content = mail.getContent() == null ? "" : mail.getContent();
        return (subject + "\n" + content).toLowerCase(Locale.ROOT);
    }

    /**
     * 将逗号分隔的配置关键词拆分为小写列表。
     *
     * @param keywords 逗号分隔关键词
     * @return 关键词列表，空白配置返回空列表
     */
    private List<String> splitKeywords(String keywords) {
        if (keywords == null || keywords.trim().isEmpty()) {
            return Collections.emptyList();
        }
        return Arrays.stream(keywords.split(","))
                .map(String::trim)
                .filter(item -> !item.isEmpty())
                .map(item -> item.toLowerCase(Locale.ROOT))
                .collect(Collectors.toList());
    }

    /**
     * 判断文本是否命中任一关键词。
     *
     * @param text 小写化的邮件文本
     * @param keywords 小写化的关键词列表
     * @return true 表示命中
     */
    private boolean containsAny(String text, List<String> keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }
}
