package com.hmdp.mail.reminder;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 日程提醒存储接口。
 * 抽象提醒的持久化与到期抢占语义，便于测试替身替换。
 *
 * @author ethan
 * @date 2026-11-06
 */
public interface ReminderStore {

    /**
     * 保存提醒。
     *
     * @param reminder 提醒（id 必须已生成）
     */
    void save(Reminder reminder);

    /**
     * 列出全部提醒（含已触发/已取消）。
     *
     * @return 提醒列表
     */
    List<Reminder> listAll();

    /**
     * 抢占并返回已到期的提醒。
     * 抢占成功的提醒状态置为 FIRED；多实例部署时同一提醒只会被一个实例抢到。
     *
     * @param now 当前时间
     * @return 本实例抢到的到期提醒
     */
    List<Reminder> claimDue(LocalDateTime now);

    /**
     * 取消提醒。
     *
     * @param id 提醒 id
     * @return true 表示存在且已取消
     */
    boolean cancel(String id);
}
