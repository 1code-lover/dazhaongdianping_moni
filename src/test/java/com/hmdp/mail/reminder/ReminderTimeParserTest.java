package com.hmdp.mail.reminder;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 邮件文本时间解析器测试。
 *
 * @author ethan
 * @date 2026-11-06
 */
class ReminderTimeParserTest {

    private final ReminderTimeParser parser = new ReminderTimeParser();

    /**
     * 构造未来日期的 M月d日 文本与期望时间（次月 15 日 10 点，跨月安全）。
     *
     * @return [文本, 期望时间]
     */
    private String[] futureMonthDay() {
        LocalDateTime target = LocalDateTime.now().plusMonths(1).withDayOfMonth(15)
                .withHour(10).withMinute(0).withSecond(0).withNano(0);
        String text = target.getMonthValue() + "月" + target.getDayOfMonth() + "日10点发布";
        // 解析器默认当前年，若跨年（当前 12 月）则期望滚动到下一年
        return new String[]{text, target.toString()};
    }

    @Test
    void testParseIsoFormat() {
        LocalDateTime expected = LocalDateTime.now().plusDays(3).withHour(10).withMinute(30);
        String text = expected.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")) + " 检查库存";
        Optional<LocalDateTime> parsed = parser.parse(text);
        assertTrue(parsed.isPresent());
        assertEquals(expected.withSecond(0).withNano(0), parsed.get());
    }

    @Test
    void testParseIsoWithLetterT() {
        LocalDateTime expected = LocalDateTime.now().plusDays(2).withHour(8).withMinute(15);
        String text = expected.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm"));
        Optional<LocalDateTime> parsed = parser.parse(text);
        assertTrue(parsed.isPresent());
        assertEquals(expected.withSecond(0).withNano(0), parsed.get());
    }

    @Test
    void testParseMonthDayWithYearRoll() {
        String[] pair = futureMonthDay();
        Optional<LocalDateTime> parsed = parser.parse(pair[0]);
        assertTrue(parsed.isPresent());
        assertEquals(LocalDateTime.parse(pair[1]), parsed.get());
    }

    @Test
    void testParseTomorrowAtHour() {
        Optional<LocalDateTime> parsed = parser.parse("明天9点开站会");
        assertTrue(parsed.isPresent());
        LocalDateTime expected = LocalDateTime.now().plusDays(1).withHour(9).withMinute(0);
        assertEquals(expected.toLocalDate(), parsed.get().toLocalDate());
        assertEquals(9, parsed.get().getHour());
    }

    @Test
    void testParseTomorrowWithMinutes() {
        Optional<LocalDateTime> parsed = parser.parse("明天9点30分发布报告");
        assertTrue(parsed.isPresent());
        assertEquals(9, parsed.get().getHour());
        assertEquals(30, parsed.get().getMinute());
    }

    @Test
    void testParseTodayAtHour() {
        Optional<LocalDateTime> parsed = parser.parse("今天23点59分复盘");
        assertTrue(parsed.isPresent());
        assertEquals(23, parsed.get().getHour());
        assertEquals(59, parsed.get().getMinute());
    }

    @Test
    void testParseHoursLater() {
        LocalDateTime before = LocalDateTime.now();
        Optional<LocalDateTime> parsed = parser.parse("2小时后重启服务");
        assertTrue(parsed.isPresent());
        assertTrue(parsed.get().isAfter(before.plusHours(1).plusMinutes(59)));
        assertTrue(parsed.get().isBefore(before.plusHours(2).plusMinutes(1)));
    }

    @Test
    void testParseMinutesLater() {
        LocalDateTime before = LocalDateTime.now();
        Optional<LocalDateTime> parsed = parser.parse("15分钟后提醒我");
        assertTrue(parsed.isPresent());
        assertTrue(parsed.get().isAfter(before.plusMinutes(14)));
        assertTrue(parsed.get().isBefore(before.plusMinutes(16)));
    }

    @Test
    void testParseIsoPriorityOverRelative() {
        LocalDateTime expected = LocalDateTime.now().plusDays(1).withHour(7).withMinute(45);
        String text = expected.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")) + "，30分钟后还有一场";
        Optional<LocalDateTime> parsed = parser.parse(text);
        assertTrue(parsed.isPresent());
        assertEquals(expected.withSecond(0).withNano(0), parsed.get());
    }

    @Test
    void testParseNoTimeReturnsEmpty() {
        assertFalse(parser.parse("你好，世界").isPresent());
    }

    @Test
    void testParsePastTimeReturnsEmpty() {
        assertFalse(parser.parse("2020-01-01 10:00 回顾").isPresent());
    }

    @Test
    void testParseNullAndBlankReturnsEmpty() {
        assertFalse(parser.parse(null).isPresent());
        assertFalse(parser.parse("   ").isPresent());
    }
}
