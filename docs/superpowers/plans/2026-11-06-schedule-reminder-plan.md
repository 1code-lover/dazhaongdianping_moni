# 实施方案：日程提醒

> 日期：2026-11-06
> 关联设计：docs/design/2026-11-06-schedule-reminder-requirements.md

## 1. 新增/修改文件

### 1.1 主代码 `src/main/java/com/hmdp/mail/reminder/`

| 文件 | 职责 |
|------|------|
| `Reminder.java` | 提醒 POJO（id/title/content/remindAt/level/source/status/createdAt） |
| `ReminderStore.java` | 存储接口：save / listAll / claimDue / cancel |
| `RedisReminderStore.java` | Redis ZSet+Hash 实现（ZREM 抢占语义） |
| `ReminderTimeParser.java` | 中文/ISO 时间正则解析 |
| `ReminderScheduler.java` | 30s 轮询到期提醒并分发 |
| `ReminderController.java` | 创建/列表/取消/解析 4 个端点 |

### 1.2 修改

| 文件 | 改动 |
|------|------|
| `MailAlertPipelineService.java` | 新增 `dispatchDirect(MailAlert)`（跳过分级/去重，直接分发） |
| `MailAlertProperties.java` | 新增 `Reminder` 配置项（enabled/pollIntervalMs） |
| `application.yaml` | `app.notify.reminder` 配置段 |

### 1.3 测试 `src/test/java/com/hmdp/mail/reminder/`

| 文件 | 覆盖点 |
|------|--------|
| `ReminderTimeParserTest` | 6 类模式命中、优先级、解析失败返回空 |
| `RedisReminderStoreTest` | 保存写入结构、claimDue 抢占与状态更新、取消 |
| `ReminderSchedulerTest` | 开关、到期分发、异常隔离 |
| `ReminderControllerTest` | 创建（remindAt/delayMinutes 二选一）、列表、取消、解析 |
| `MailAlertPipelineServiceTest`（扩充） | dispatchDirect 按级别路由且不走去重 |

## 2. 实现顺序

1. T1 模型与配置：Reminder + MailAlertProperties.Reminder + yaml
2. T2 基础设施：RedisReminderStore + ReminderTimeParser + PipelineService.dispatchDirect
3. T3 调度与接口：ReminderScheduler + ReminderController
4. T4 测试：5 个测试类
5. T5 端到端：重启应用 → 建 60s 提醒 → 验证触发 → 取消/解析路径
6. T6 CI：本地全量测试摸底 → .github/workflows/ci.yml
7. T7 测试报告 + 提交推送（沿用 feat/mail-alert-pipeline 分支，单一 PR）

## 3. 关键实现细节

- **抢占语义**：`claimDue()` 内 `rangeByScore` 后逐个 `remove(zset,id)`，返回 1 才读取明细并触发，防多实例重复；
- **触发分发**：Reminder → `new MailAlert(合成MailMessage("reminder-"+id, ...), level, now)` → `dispatchDirect`；
- **时间解析优先级**：ISO > `M月d日` > `明天H点` > `今天H点` > `N小时后` > `N分钟后`，解析结果必须在未来才有效；
- **JSON 序列化**：复用 spring-web 的 Jackson ObjectMapper（构造注入，不 new）。

## 4. 单测计划

全部 Mockito 纯单测；Redis 模板链式 mock（opsForZSet/opsForHash）；不启动 Spring 上下文。
