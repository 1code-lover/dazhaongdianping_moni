package com.hmdp.mail.reminder;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Redis 日程提醒存储测试。
 * Redis 模板全部 Mockito 模拟，不依赖真实 Redis。
 *
 * @author ethan
 * @date 2026-11-06
 */
@SuppressWarnings("unchecked")
class RedisReminderStoreTest {

    private static final String ZSET_KEY = "app:notify:reminder:zset";

    private static final String HASH_KEY = "app:notify:reminder:hash";

    private StringRedisTemplate template;

    private ZSetOperations<String, String> zsetOps;

    private HashOperations<String, Object, Object> hashOps;

    private ObjectMapper objectMapper;

    private RedisReminderStore store;

    /**
     * 构造依赖与被测对象。
     */
    @BeforeEach
    void setUp() {
        template = mock(StringRedisTemplate.class);
        zsetOps = mock(ZSetOperations.class);
        hashOps = mock(HashOperations.class);
        when(template.opsForZSet()).thenReturn(zsetOps);
        when(template.opsForHash()).thenReturn(hashOps);
        objectMapper = new ObjectMapper().findAndRegisterModules();
        store = new RedisReminderStore(template, objectMapper);
    }

    /**
     * 构造测试提醒。
     *
     * @param id     id
     * @param status 状态
     * @return 提醒
     */
    private Reminder reminder(String id, String status) {
        return new Reminder(id, "检查库存", "内容", LocalDateTime.now().plusMinutes(5),
                com.hmdp.mail.MailLevel.WARNING, Reminder.SOURCE_MANUAL, status, LocalDateTime.now());
    }

    @Test
    void testSaveWritesHashAndZset() {
        Reminder reminder = reminder("r1", Reminder.STATUS_PENDING);
        double expectedScore = reminder.getRemindAt()
                .atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();

        store.save(reminder);

        verify(hashOps).put(eq(HASH_KEY), eq("r1"), eq(objectMapper.valueToTree(reminder).toString()));
        verify(zsetOps).add(eq(ZSET_KEY), eq("r1"), eq(expectedScore));
    }

    @Test
    void testClaimDueClaimsAndMarksFired() throws Exception {
        Reminder pending = reminder("r1", Reminder.STATUS_PENDING);
        when(zsetOps.rangeByScore(eq(ZSET_KEY), eq(0.0), anyDouble()))
                .thenReturn(new HashSet<>(Collections.singletonList("r1")));
        when(hashOps.get(HASH_KEY, "r1")).thenReturn(objectMapper.writeValueAsString(pending));
        when(zsetOps.remove(ZSET_KEY, "r1")).thenReturn(1L);

        List<Reminder> due = store.claimDue(LocalDateTime.now().plusMinutes(10));

        assertEquals(1, due.size());
        assertEquals(Reminder.STATUS_FIRED, due.get(0).getStatus());
        verify(hashOps).put(eq(HASH_KEY), eq("r1"), anyString());
    }

    @Test
    void testClaimDueSkipsWhenClaimedByOther() throws Exception {
        Reminder pending = reminder("r1", Reminder.STATUS_PENDING);
        when(zsetOps.rangeByScore(eq(ZSET_KEY), eq(0.0), anyDouble()))
                .thenReturn(new HashSet<>(Collections.singletonList("r1")));
        when(hashOps.get(HASH_KEY, "r1")).thenReturn(objectMapper.writeValueAsString(pending));
        // ZREM 返回 0 表示被其他实例抢先
        when(zsetOps.remove(ZSET_KEY, "r1")).thenReturn(0L);

        assertTrue(store.claimDue(LocalDateTime.now().plusMinutes(10)).isEmpty());
    }

    @Test
    void testClaimDueWithEmptyQueueReturnsEmpty() {
        when(zsetOps.rangeByScore(eq(ZSET_KEY), eq(0.0), anyDouble())).thenReturn(null);
        assertTrue(store.claimDue(LocalDateTime.now()).isEmpty());
    }

    @Test
    void testCancelRemovesZsetAndHash() {
        when(hashOps.get(HASH_KEY, "r1")).thenReturn("{\"id\":\"r1\"}");

        assertTrue(store.cancel("r1"));
        verify(zsetOps).remove(ZSET_KEY, "r1");
        verify(hashOps).delete(HASH_KEY, "r1");
    }

    @Test
    void testCancelMissingReturnsFalse() {
        when(hashOps.get(HASH_KEY, "rX")).thenReturn(null);
        assertFalse(store.cancel("rX"));
        verify(zsetOps, never()).remove(ZSET_KEY, "rX");
    }

    @Test
    void testListAllParsesAllEntries() throws Exception {
        Reminder first = reminder("r1", Reminder.STATUS_PENDING);
        Reminder second = reminder("r2", Reminder.STATUS_FIRED);
        when(hashOps.values(HASH_KEY)).thenReturn(Arrays.asList(
                objectMapper.writeValueAsString(first), objectMapper.writeValueAsString(second)));

        List<Reminder> all = store.listAll();

        assertEquals(2, all.size());
        assertEquals("r1", all.get(0).getId());
        assertEquals(Reminder.STATUS_FIRED, all.get(1).getStatus());
    }
}
