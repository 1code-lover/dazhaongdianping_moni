package com.hmdp.mail.channel;

import com.hmdp.mail.ChannelResult;
import com.hmdp.mail.MailAlert;
import com.hmdp.mail.MailAlertProperties;
import com.hmdp.mail.MailLevel;
import com.hmdp.mail.NotificationChannel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 手机闹钟通知渠道。
 * 通过 adb 调用系统时钟应用设置一条闹钟提醒，适合 CRITICAL 级别"必须叫醒"的场景。
 * 降级策略：开关关闭 / 无设备时跳过；adb 执行失败返回 FAILED，不影响其他渠道。
 *
 * @author ethan
 * @date 2026-10-31
 */
@Slf4j
@Component
public class PhoneAlarmNotificationChannel implements NotificationChannel {

    /** adb 命令执行超时秒数 */
    private static final int COMMAND_TIMEOUT_SECONDS = 5;

    private final MailAlertProperties properties;

    /**
     * 创建手机闹钟渠道。
     *
     * @param properties 告警配置
     */
    public PhoneAlarmNotificationChannel(MailAlertProperties properties) {
        this.properties = properties;
    }

    /**
     * 渠道名称。
     *
     * @return phone-alarm
     */
    @Override
    public String name() {
        return "phone-alarm";
    }

    /**
     * 仅 CRITICAL 级别设置闹钟。
     *
     * @param level 告警级别
     * @return 是否支持
     */
    @Override
    public boolean supports(MailLevel level) {
        return level == MailLevel.CRITICAL;
    }

    /**
     * 设置手机闹钟。
     *
     * @param alert 告警事件
     * @return SENT 设置成功；SKIPPED 开关关闭或无设备；FAILED 命令执行失败
     */
    @Override
    public ChannelResult notify(MailAlert alert) {
        MailAlertProperties.PhoneAlarm config = properties.getPhoneAlarm();
        if (!config.isEnabled()) {
            return ChannelResult.SKIPPED;
        }
        if (!hasDevice()) {
            log.info("手机闹钟渠道跳过：未检测到 adb 设备，请连接手机并开启 USB 调试");
            return ChannelResult.SKIPPED;
        }
        return executeAlarm(buildAlarmCommand(alert));
    }

    /**
     * 检查是否有可用的 adb 设备。
     *
     * @return true 表示至少一台设备在线
     */
    protected boolean hasDevice() {
        String adb = properties.getPhoneAlarm().getAdbPath();
        try {
            Process process = new ProcessBuilder(adb, "devices").start();
            if (!process.waitFor(COMMAND_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return false;
            }
            // 输出第二行起为设备列表，出现 device 状态即认为在线
            byte[] output = readAll(process);
            return new String(output, StandardCharsets.UTF_8).contains("\tdevice");
        } catch (IOException | InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.warn("adb devices 执行失败: {}", ex.getMessage());
            return false;
        }
    }

    /**
     * 执行设置闹钟命令。
     *
     * @param command 完整命令参数
     * @return SENT 成功；FAILED 失败
     */
    protected ChannelResult executeAlarm(List<String> command) {
        try {
            Process process = new ProcessBuilder(command).start();
            if (!process.waitFor(COMMAND_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                log.warn("设置闹钟命令超时");
                return ChannelResult.FAILED;
            }
            log.info("手机闹钟已设置: {}", command);
            return ChannelResult.SENT;
        } catch (IOException | InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.warn("设置闹钟命令执行失败: {}", ex.getMessage());
            return ChannelResult.FAILED;
        }
    }

    /**
     * 构造设置闹钟的 adb 命令。
     * 时间为当前时间 + 配置延迟分钟数，消息带告警标题。
     *
     * @param alert 告警事件
     * @return 命令参数列表
     */
    protected List<String> buildAlarmCommand(MailAlert alert) {
        MailAlertProperties.PhoneAlarm config = properties.getPhoneAlarm();
        LocalDateTime alarmTime = LocalDateTime.now().plusMinutes(config.getDelayMinutes());
        List<String> command = new ArrayList<>();
        command.add(config.getAdbPath());
        addSerialArg(command, config.getDeviceSerial());
        command.add("shell");
        command.add("am");
        command.add("start");
        command.add("-n");
        command.add(config.getClockPackage() + "/.HandleSetAlarm");
        command.add("--es");
        command.add("android.intent.extra.alarm.HOUR");
        command.add(String.valueOf(alarmTime.getHour()));
        command.add("--es");
        command.add("android.intent.extra.alarm.MINUTES");
        command.add(String.valueOf(alarmTime.getMinute()));
        command.add("--es");
        command.add("android.intent.extra.alarm.MESSAGE");
        command.add(alert.displayTitle());
        return command;
    }

    /**
     * 追加设备序列号参数。
     *
     * @param command 命令参数列表
     * @param serial  设备序列号，空则不追加
     */
    private void addSerialArg(List<String> command, String serial) {
        if (serial != null && !serial.trim().isEmpty()) {
            command.add("-s");
            command.add(serial.trim());
        }
    }

    /**
     * 读取进程标准输出。
     *
     * @param process 目标进程
     * @return 输出字节
     * @throws IOException 读取失败
     */
    private byte[] readAll(Process process) throws IOException {
        byte[] buffer = new byte[2048];
        int read = process.getInputStream().read(buffer);
        return read <= 0 ? new byte[0] : java.util.Arrays.copyOf(buffer, read);
    }
}
