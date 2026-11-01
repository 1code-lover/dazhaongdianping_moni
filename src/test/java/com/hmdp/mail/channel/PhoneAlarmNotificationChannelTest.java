package com.hmdp.mail.channel;

import com.hmdp.mail.ChannelResult;
import com.hmdp.mail.MailAlert;
import com.hmdp.mail.MailAlertProperties;
import com.hmdp.mail.MailLevel;
import com.hmdp.mail.MailMessage;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 手机闹钟渠道测试。
 * 不依赖真实 adb：命令构造直接断言，执行降级用不存在的 adb 路径触发异常路径。
 *
 * @author ethan
 * @date 2026-10-31
 */
class PhoneAlarmNotificationChannelTest {

    private final MailAlertProperties properties = new MailAlertProperties();

    private final PhoneAlarmNotificationChannel channel = new PhoneAlarmNotificationChannel(properties);

    /**
     * 构造 CRITICAL 告警。
     *
     * @return 告警事件
     */
    private MailAlert criticalAlert() {
        MailMessage mail = new MailMessage("k1", "monitor@hmdp.example", "库存超卖告警", "立即处理", LocalDateTime.now());
        return new MailAlert(mail, MailLevel.CRITICAL, LocalDateTime.now());
    }

    @Test
    void testSupportsOnlyCriticalLevel() {
        assertTrue(channel.supports(MailLevel.CRITICAL));
        assertFalse(channel.supports(MailLevel.WARNING));
        assertFalse(channel.supports(MailLevel.INFO));
    }

    @Test
    void testNotifyDisabledReturnsSkipped() {
        properties.getPhoneAlarm().setEnabled(false);
        assertEquals(ChannelResult.SKIPPED, channel.notify(criticalAlert()));
    }

    @Test
    void testBuildAlarmCommandContainsAdbShellAndClockIntent() {
        properties.getPhoneAlarm().setEnabled(true);
        properties.getPhoneAlarm().setDeviceSerial("EMU-123");

        List<String> command = channel.buildAlarmCommand(criticalAlert());

        assertEquals("adb", command.get(0));
        assertEquals("-s", command.get(1));
        assertEquals("EMU-123", command.get(2));
        assertTrue(command.contains("shell"));
        assertTrue(command.contains("am"));
        assertTrue(command.contains("start"));
        assertTrue(command.contains("com.android.deskclock/.HandleSetAlarm"));
        assertTrue(command.contains("android.intent.extra.alarm.HOUR"));
        assertTrue(command.contains("android.intent.extra.alarm.MINUTES"));
        assertTrue(command.contains("[CRITICAL] 库存超卖告警"));
    }

    @Test
    void testBuildAlarmCommandWithoutSerialOmitsSerialArgs() {
        properties.getPhoneAlarm().setEnabled(true);
        properties.getPhoneAlarm().setDeviceSerial("");

        List<String> command = channel.buildAlarmCommand(criticalAlert());

        assertEquals("adb", command.get(0));
        // 未配置序列号时第二个参数直接是 shell
        assertEquals("shell", command.get(1));
        assertFalse(command.contains("-s"));
    }

    @Test
    void testExecuteAlarmWithMissingAdbReturnsFailed() {
        // adb 可执行文件不存在时抛 IOException，渠道应降级为 FAILED 而非向外抛异常
        assertEquals(ChannelResult.FAILED,
                channel.executeAlarm(java.util.Arrays.asList("missing-adb-file-for-test", "devices")));
    }

    @Test
    void testHasDeviceWithMissingAdbReturnsFalse() {
        properties.getPhoneAlarm().setAdbPath("missing-adb-file-for-test");
        assertFalse(channel.hasDevice());
    }

    @Test
    void testNotifyWithMissingDeviceSkips() {
        // 开关打开但 adb 缺失（无设备）时应跳过
        properties.getPhoneAlarm().setEnabled(true);
        properties.getPhoneAlarm().setAdbPath("missing-adb-file-for-test");
        assertEquals(ChannelResult.SKIPPED, channel.notify(criticalAlert()));
    }
}
