# 测试报告：日程提醒（Schedule Reminder）

> 日期：2026-11-06
> 关联设计：docs/design/2026-11-06-schedule-reminder-requirements.md

## 1. 执行环境

- JDK 17（corretto-17.0.18）+ IDEA 内置 Maven
- MySQL、Redis 本机运行（密码走环境变量）
- 应用端口 8081

## 2. 单元测试结果

新增 4 个测试类 + 既有管道测试扩充 1 例，共 35 个新用例：

| 测试类 | 用例数 | 覆盖点 |
|--------|--------|--------|
| ReminderTimeParserTest | 12 | ISO/M月d日/明天/今天/N小时后/N分钟后各模式、ISO 优先级、无时间、过去时间、空值、跨年滚动 |
| RedisReminderStoreTest | 7 | 保存双写、到期抢占（ZREM=1 归属）、被他实例抢占跳过、空队列、取消、列表解析 |
| ReminderSchedulerTest | 6 | 开关跳过、到期分发（级别映射/messageKey）、空级别回退 WARNING、无到期不分发、异常隔离、多条逐一分发 |
| ReminderControllerTest | 9 | delayMinutes 计算、显式 remindAt+级别、缺时间失败、非法级别回退、列表、取消存在/不存在、解析建提醒、解析失败 |
| MailAlertPipelineServiceTest（扩充） | 1 | dispatchDirect 按级别路由、可重复分发不受去重影响 |

**mail 相关套件合计 107/107 通过**；首次运行发现并修复 2 个测试自身问题（期望值含纳秒、`anyString` 缺 import）与 1 个解析器跨年边界（12 月说"3月5日"应指明年，已实现年份滚动）。

## 3. 端到端验证（真实应用 + 真实 Redis）

| 步骤 | 操作 | 结果 | 结论 |
|------|------|------|------|
| E1 | POST /mail-alert/reminders（delayMinutes=1，INFO） | 返回 PENDING 提醒，remindAt=+60s | ✅ 创建正常 |
| E2 | 等待到期后查询列表与日志 | 状态 PENDING→**FIRED**；日志"弹窗提醒已广播: [INFO] [日程提醒] 检查库存盘点" | ✅ 到期触发并走渠道分发 |
| E3 | POST /parse {"text":"明天9点检查库存"} | parsedTime=次日 09:00，创建 MAIL 来源提醒 | ✅ 中文时间解析可用 |
| E4 | POST /parse {"text":"你好世界"} | 友好失败"未能从文本中解析出未来的时间" | ✅ 边界处理正常 |
| E5 | DELETE /{id} | "已取消"，列表中移除 | ✅ 取消正常 |

## 4. 全量测试摸底与 CI

- 全量套件 176 个测试：**169 通过，7 个错误集中在既有集成测试 `ReviewServiceTest`**（需要数据库预先存在 `tb_order` 表的种子数据，与本次改动无关，本地库未建该表）。
- CI 方案：`.github/workflows/ci.yml` —— ubuntu + JDK17 + MySQL/Redis 服务容器，执行 `mvn -B test '-Dtest=!ReviewServiceTest'`，该命令本地实测 **169/169 全绿**（BUILD SUCCESS）。
- `ReviewServiceTest` 的修复（建表种子或迁 H2）记入遗留事项，不在本次范围。

## 5. 遗留事项

1. `ReviewServiceTest` 依赖种子表，建议后续迁 H2 或在 CI 中加 schema 初始化，恢复全量绿；
2. 邮件时间解析覆盖常见 6 类模式，复杂表述（"下周五"、"月底"）暂不支持；
3. 提醒无重复/持久化告警记录查询页，如需可视化可在 demo 页扩展。

## 6. 结论

日程提醒功能按设计完成：Redis ZSet 延迟队列 + 抢占语义 + 复用五渠道分发 + 中文时间解析。新增用例 35 个、mail 套件 107/107 通过、端到端 5 项验证通过、CI 流水线落地（本地等效命令 169/169 全绿）。
