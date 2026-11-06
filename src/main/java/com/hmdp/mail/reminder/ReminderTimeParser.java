package com.hmdp.mail.reminder;

import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 邮件文本时间解析器。
 * 用正则覆盖常见的中文与 ISO 时间表述，解析不到返回空，不引入 NLP 依赖。
 * 优先级：ISO &gt; M月d日 &gt; 明天H点 &gt; 今天H点 &gt; N小时后 &gt; N分钟后。
 *
 * @author ethan
 * @date 2026-11-06
 */
@Component
public class ReminderTimeParser {

    /** ISO 格式：2026-11-06 14:30 */
    private static final Pattern ISO_PATTERN =
            Pattern.compile("(\\d{4})-(\\d{1,2})-(\\d{1,2})[ T](\\d{1,2}):(\\d{2})");

    /** 中文日期：3月5日 14点 / 3月5日14:30 */
    private static final Pattern MD_PATTERN =
            Pattern.compile("(\\d{1,2})月(\\d{1,2})日\\s*(\\d{1,2})?[点时:：]?(\\d{1,2})?分?");

    /** 明天 H 点 */
    private static final Pattern TOMORROW_PATTERN =
            Pattern.compile("明天\\s*(\\d{1,2})[点时:：](\\d{1,2})?分?");

    /** 今天 H 点 */
    private static final Pattern TODAY_PATTERN =
            Pattern.compile("今天\\s*(\\d{1,2})[点时:：](\\d{1,2})?分?");

    /** N 小时后 */
    private static final Pattern HOURS_LATER_PATTERN =
            Pattern.compile("(\\d{1,3})\\s*小时后");

    /** N 分钟后 */
    private static final Pattern MINUTES_LATER_PATTERN =
            Pattern.compile("(\\d{1,3})\\s*分钟后");

    /**
     * 从文本解析提醒时间。
     *
     * @param text 邮件主题或正文
     * @return 解析出的未来时间，未命中或时间在过去返回空
     */
    public Optional<LocalDateTime> parse(String text) {
        if (text == null || text.trim().isEmpty()) {
            return Optional.empty();
        }
        Optional<LocalDateTime> parsed = tryIso(text);
        if (!parsed.isPresent()) {
            parsed = tryMonthDay(text);
        }
        if (!parsed.isPresent()) {
            parsed = tryTomorrow(text);
        }
        if (!parsed.isPresent()) {
            parsed = tryToday(text);
        }
        if (!parsed.isPresent()) {
            parsed = tryRelative(text, HOURS_LATER_PATTERN, 60);
        }
        if (!parsed.isPresent()) {
            parsed = tryRelative(text, MINUTES_LATER_PATTERN, 1);
        }
        return parsed.filter(time -> time.isAfter(LocalDateTime.now()));
    }

    /**
     * 解析 ISO 格式时间。
     *
     * @param text 文本
     * @return 可能的时间
     */
    private Optional<LocalDateTime> tryIso(String text) {
        Matcher matcher = ISO_PATTERN.matcher(text);
        if (!matcher.find()) {
            return Optional.empty();
        }
        return Optional.of(LocalDateTime.of(
                Integer.parseInt(matcher.group(1)),
                Integer.parseInt(matcher.group(2)),
                Integer.parseInt(matcher.group(3)),
                Integer.parseInt(matcher.group(4)),
                Integer.parseInt(matcher.group(5))));
    }

    /**
     * 解析 M月d日 [H点M分] 时间，未带时刻默认 9 点。
     *
     * @param text 文本
     * @return 可能的时间
     */
    private Optional<LocalDateTime> tryMonthDay(String text) {
        Matcher matcher = MD_PATTERN.matcher(text);
        if (!matcher.find()) {
            return Optional.empty();
        }
        LocalDateTime result = atTime(
                LocalDateTime.now().getYear(),
                Integer.parseInt(matcher.group(1)),
                Integer.parseInt(matcher.group(2)),
                groupOrDefault(matcher.group(3), 9),
                groupOrDefault(matcher.group(4), 0));
        if (!result.isAfter(LocalDateTime.now())) {
            // 已过时间视为指下一年（如 12 月说"3月5日"指明年 3 月）
            result = result.plusYears(1);
        }
        return Optional.of(result);
    }

    /**
     * 解析明天 H 点 [M分]。
     *
     * @param text 文本
     * @return 可能的时间
     */
    private Optional<LocalDateTime> tryTomorrow(String text) {
        Matcher matcher = TOMORROW_PATTERN.matcher(text);
        if (!matcher.find()) {
            return Optional.empty();
        }
        LocalDateTime tomorrow = LocalDateTime.now().plusDays(1);
        return Optional.of(atTime(
                tomorrow.getYear(), tomorrow.getMonthValue(), tomorrow.getDayOfMonth(),
                Integer.parseInt(matcher.group(1)), groupOrDefault(matcher.group(2), 0)));
    }

    /**
     * 解析今天 H 点 [M分]。
     *
     * @param text 文本
     * @return 可能的时间
     */
    private Optional<LocalDateTime> tryToday(String text) {
        Matcher matcher = TODAY_PATTERN.matcher(text);
        if (!matcher.find()) {
            return Optional.empty();
        }
        LocalDateTime today = LocalDateTime.now();
        return Optional.of(atTime(
                today.getYear(), today.getMonthValue(), today.getDayOfMonth(),
                Integer.parseInt(matcher.group(1)), groupOrDefault(matcher.group(2), 0)));
    }

    /**
     * 解析相对时间（N 小时后 / N 分钟后）。
     *
     * @param text      文本
     * @param pattern   匹配模式
     * @param multiplier 数字单位（分钟）
     * @return 可能的时间
     */
    private Optional<LocalDateTime> tryRelative(String text, Pattern pattern, int multiplier) {
        Matcher matcher = pattern.matcher(text);
        if (!matcher.find()) {
            return Optional.empty();
        }
        long amount = Long.parseLong(matcher.group(1));
        return Optional.of(LocalDateTime.now().plusMinutes(amount * multiplier));
    }

    /**
     * 按字段构造时间，越界字段由 LocalDateTime 收敛。
     *
     * @param year   年
     * @param month  月
     * @param day    日
     * @param hour   时
     * @param minute 分
     * @return 构造的时间
     */
    private LocalDateTime atTime(int year, int month, int day, int hour, int minute) {
        return LocalDateTime.of(year, 1, 1, 0, 0)
                .plusMonths(month - 1L)
                .plusDays(day - 1L)
                .plusHours(hour)
                .plusMinutes(minute);
    }

    /**
     * 取分组值，空分组返回默认值。
     *
     * @param group        匹配分组
     * @param defaultValue 默认值
     * @return 数值
     */
    private int groupOrDefault(String group, int defaultValue) {
        return group == null ? defaultValue : Integer.parseInt(group);
    }
}
