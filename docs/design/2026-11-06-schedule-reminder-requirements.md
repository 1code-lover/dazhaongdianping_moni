# 日程提醒（Schedule Reminder）设计方案

> 状态：待审核
> 日期：2026-11-06
> 关联：docs/design/2026-10-31-mail-alert-pipeline-requirements.md（既有告警链路）

## 1. 需求背景

现有链路是**事件驱动**：邮件到达即告警。日程提醒补上**时间驱动**：到点才提醒。
典型场景：监控邮件说"明天 9 点前修复"→ 自动/手动建日程 → 到点弹窗 + 邮件提醒。

## 2. 需求分析

| 编号 | 需求 | 说明 |
|------|------|------|
| R1 | 创建提醒 | REST 接口：`{title, remindAt 或 delayMinutes, level, content}` |
| R2 | 到期触发 | 调度器轮询到期任务，复用现有 NotificationChannel 分发（弹窗/钉钉/邮件/闹钟） |
| R3 | 级别路由 | 提醒自带级别（默认 WARNING）：INFO→弹窗；WARNING→弹窗+钉钉+邮件；CRITICAL→再加闹钟 |
| R4 | 查询/取消 | 列出全部提醒、按 id 取消 |
| R5 | 邮件时间解析 | `POST /parse {text}`：从中文文本提取时间（"明天9点"、"30分钟后"、"3月5日14:00"、ISO 格式） |
| R6 | 持久化 | Redis ZSet 延迟队列 + Hash 明细，应用重启不丢失 |
| R7 | 开关 | `app.notify.reminder.enabled` 总开关，默认开启 |

## 3. 业务流程

```
入口 A：POST /mail-alert/reminders            入口 B：POST /mail-alert/reminders/parse {text}
{title, remindAt|delayMinutes, level}          → ReminderTimeParser 正则提取时间 → 建提醒
        │                                              │
        └────────────┬─────────────────────────────────┘
                     ▼
   Redis ZSet  app:notify:reminder:zset   member=id, score=触发时间戳
   Redis Hash  app:notify:reminder:hash   field=id, value=Reminder JSON
                     │
                     ▼  ReminderScheduler 每 30s：ZRANGEBYSCORE 取到期 id
                     ▼  HGET 取明细 → ZREM 抢占（返回 1 才归属本实例）
                     ▼
   MailAlertPipelineService.dispatchDirect(alert)   ← 新增：跳过分级/去重，直接按级别分发
                     ▼
   NotificationChannel 路由（与告警共用五渠道）
```

## 4. 数据模型

```java
Reminder {
    String id;              // UUID
    String title;           // 提醒标题
    String content;         // 补充说明
    LocalDateTime remindAt; // 触发时间
    MailLevel level;        // 级别，决定渠道路由，默认 WARNING
    String source;          // MANUAL / MAIL
    String status;          // PENDING / FIRED / CANCELLED
    LocalDateTime createdAt;
}
```

## 5. 接口设计

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/mail-alert/reminders` | 创建提醒，body: `{title, content?, level?, remindAt?, delayMinutes?}`，remindAt 与 delayMinutes 二选一 |
| GET | `/mail-alert/reminders` | 全部提醒（含已触发，便于观察） |
| DELETE | `/mail-alert/reminders/{id}` | 取消（ZREM + HDEL） |
| POST | `/mail-alert/reminders/parse` | `{text}` → 提取时间并直接建提醒（能解析才建，返回解析结果） |

## 6. 关键设计决策

1. **ZSet 延迟队列**：score=触发时间戳；`ZRANGEBYSCORE(key, 0, now)` 取到期 id；用 **ZREM 成功与否做抢占**，多实例部署也不会重复触发。
2. **复用渠道**：新增 `dispatchDirect(MailAlert)`，把 Reminder 包装成合成 MailAlert（from=reminder-system）直接分发，绕过分级与去重——提醒不该被关键词改写级别，也不存在重复告警问题。
3. **时间解析器**：纯正则实现 6 类常见中文/ISO 模式，解析不到返回空（接口明确提示），不引入 NLP 依赖。
4. **不做的东西**：不解析 .ics 附件（范围外）；不做自动从 CRITICAL 邮件建提醒（预测性差，后续按需加开关）。

## 7. 非功能要求

- 函数 ≤30 行、≤4 参数、单一职责、中文注释（AGENTS.md）；
- Redis 操作全部带异常处理，调度异常不中断轮询；
- 新增类单测覆盖率 ≥80%，Redis 层 Mockito 模拟，不依赖真实 Redis；
- 端到端验证：建 60s 提醒 → 弹窗触发 → 取消路径 → 解析接口。
