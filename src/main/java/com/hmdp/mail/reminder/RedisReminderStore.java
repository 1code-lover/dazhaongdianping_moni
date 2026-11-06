package com.hmdp.mail.reminder;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 基于 Redis 的日程提醒存储实现。
 * ZSet（member=id，score=触发时间戳）做延迟队列，Hash（field=id）存明细 JSON；
 * 到期抢占通过 ZREM 的返回值保证多实例下不重复触发。
 *
 * @author ethan
 * @date 2026-11-06
 */
@Slf4j
@Component
public class RedisReminderStore implements ReminderStore {

    /** 延迟队列键 */
    private static final String ZSET_KEY = "app:notify:reminder:zset";

    /** 明细键 */
    private static final String HASH_KEY = "app:notify:reminder:hash";

    private final StringRedisTemplate redisTemplate;

    private final ObjectMapper objectMapper;

    /**
     * 创建 Redis 提醒存储。
     *
     * @param redisTemplate Redis 模板
     * @param objectMapper  JSON 序列化器
     */
    public RedisReminderStore(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * 保存提醒：写明细 Hash 并加入延迟 ZSet。
     *
     * @param reminder 提醒
     */
    @Override
    public void save(Reminder reminder) {
        String json = toJson(reminder);
        redisTemplate.opsForHash().put(HASH_KEY, reminder.getId(), json);
        redisTemplate.opsForZSet().add(ZSET_KEY, reminder.getId(), toEpochMilli(reminder.getRemindAt()));
    }

    /**
     * 列出全部提醒。
     *
     * @return 提醒列表
     */
    @Override
    public List<Reminder> listAll() {
        List<Object> values = redisTemplate.opsForHash().values(HASH_KEY);
        List<Reminder> reminders = new ArrayList<>();
        for (Object value : values) {
            Reminder reminder = fromJson(String.valueOf(value));
            if (reminder != null) {
                reminders.add(reminder);
            }
        }
        return reminders;
    }

    /**
     * 抢占到期提醒。
     * 逐个 ZREM，删除成功才归属本实例并把状态置为 FIRED。
     *
     * @param now 当前时间
     * @return 抢到的到期提醒
     */
    @Override
    public List<Reminder> claimDue(LocalDateTime now) {
        Set<String> ids = redisTemplate.opsForZSet()
                .rangeByScore(ZSET_KEY, 0, toEpochMilli(now));
        List<Reminder> due = new ArrayList<>();
        if (ids == null) {
            return due;
        }
        for (String id : ids) {
            claimOne(id, due);
        }
        return due;
    }

    /**
     * 抢占单条提醒。
     *
     * @param id  提醒 id
     * @param due 结果收集列表
     */
    private void claimOne(String id, List<Reminder> due) {
        Long claimed = redisTemplate.opsForZSet().remove(ZSET_KEY, id);
        if (claimed == null || claimed != 1L) {
            return;
        }
        Reminder reminder = fromJson(String.valueOf(redisTemplate.opsForHash().get(HASH_KEY, id)));
        if (reminder == null) {
            return;
        }
        reminder.setStatus(Reminder.STATUS_FIRED);
        redisTemplate.opsForHash().put(HASH_KEY, id, toJson(reminder));
        due.add(reminder);
    }

    /**
     * 取消提醒。
     *
     * @param id 提醒 id
     * @return true 表示存在且已取消
     */
    @Override
    public boolean cancel(String id) {
        Object value = redisTemplate.opsForHash().get(HASH_KEY, id);
        if (value == null) {
            return false;
        }
        redisTemplate.opsForZSet().remove(ZSET_KEY, id);
        redisTemplate.opsForHash().delete(HASH_KEY, id);
        return true;
    }

    /**
     * 触发时间转 epoch 毫秒。
     *
     * @param time 触发时间
     * @return 毫秒时间戳
     */
    private long toEpochMilli(LocalDateTime time) {
        return time.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    /**
     * 序列化提醒。
     *
     * @param reminder 提醒
     * @return JSON 字符串，失败返回空对象字符串
     */
    private String toJson(Reminder reminder) {
        try {
            return objectMapper.writeValueAsString(reminder);
        } catch (JsonProcessingException ex) {
            log.warn("提醒序列化失败: {}", ex.getMessage());
            return "{}";
        }
    }

    /**
     * 反序列化提醒。
     *
     * @param json JSON 字符串
     * @return 提醒，失败返回 null
     */
    private Reminder fromJson(String json) {
        try {
            return objectMapper.readValue(json, Reminder.class);
        } catch (JsonProcessingException ex) {
            log.warn("提醒反序列化失败: {}", ex.getMessage());
            return null;
        }
    }
}
