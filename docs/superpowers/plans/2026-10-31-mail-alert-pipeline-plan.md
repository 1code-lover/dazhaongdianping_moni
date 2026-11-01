# 实施方案：邮件告警链路（Mock 版）+ 多渠道提醒

> 日期：2026-10-31
> 关联设计：docs/design/2026-10-31-mail-alert-pipeline-requirements.md

## 1. 新增文件清单

### 1.1 主代码 `src/main/java/com/hmdp/mail/`

| 文件 | 职责 |
|------|------|
| `MailLevel.java` | 邮件分级枚举（enums 包风格放 mail 包内，独立自洽） |
| `MailMessage.java` | 邮件消息 POJO |
| `MailAlert.java` | 分级后的提醒事件 POJO |
| `ChannelResult.java` | 渠道执行结果（SENT/SKIPPED/FAILED + 备注） |
| `NotificationChannel.java` | 渠道接口 |
| `MailClassifier.java` | 关键词分级（读取配置关键词，优先级判定） |
| `MailAlertDeduper.java` | 内存去重（LinkedHashSet 保序） |
| `MockMailSource.java` | 预置样例邮件 + 按需构造新邮件 |
| `MailAlertProperties.java` | `app.notify.*` 配置绑定（参照 UploadProperties 风格） |
| `channel/PopupNotificationChannel.java` | SSE 弹窗渠道（调用 SseAlertBroadcaster） |
| `channel/DingTalkNotificationChannel.java` | 钉钉 webhook 渠道 |
| `channel/PhoneAlarmNotificationChannel.java` | adb 闹钟渠道 + 降级 |
| `SseAlertBroadcaster.java` | SseEmitter 管理 + 广播 |
| `MailAlertPipelineService.java` | 编排：分级 → 去重 → 分发，返回明细 |
| `MailAlertController.java` | REST 接口（5 个端点） |

### 1.2 资源

| 文件 | 说明 |
|------|------|
| `application.yaml` | 追加 `app.notify` 配置段 |
| `static/mail-alert-demo.html` | 弹窗 demo 页（SSE + Notification API） |

### 1.3 测试 `src/test/java/com/hmdp/mail/`

| 文件 | 覆盖点 |
|------|--------|
| `MailClassifierTest.java` | 各级关键词命中、优先级、大小写/空值边界 |
| `MailAlertDeduperTest.java` | 重复 key 去重、不同 key 放行、保序 |
| `MailAlertPipelineServiceTest.java` | 渠道路由、渠道失败不影响其他渠道、去重生效 |
| `PhoneAlarmNotificationChannelTest.java` | enabled=false 跳过、命令拼接、失败降级（ProcessBuilder mock） |
| `MailAlertControllerTest.java` | 造数接口、一键跑通接口返回结构 |

## 2. 实现顺序（Task 拆分）

1. **T1 数据与枚举**：MailLevel / MailMessage / MailAlert / ChannelResult / NotificationChannel
2. **T2 配置**：MailAlertProperties + application.yaml 追加
3. **T3 核心逻辑**：MailClassifier / MailAlertDeduper / MockMailSource / MailAlertPipelineService
4. **T4 渠道**：SseAlertBroadcaster + Popup / DingTalk / PhoneAlarm 三个渠道
5. **T5 接口**：MailAlertController（造数、预置列表、一键跑通、SSE、弹窗测试）
6. **T6 前端**：mail-alert-demo.html
7. **T7 测试**：测试方案文档 + 5 个测试类
8. **T8 执行**：`mvn -Dtest="com.hmdp.mail.*" test` + 编译全量验证
9. **T9 文档**：测试报告

## 3. 关键实现细节

### 3.1 渠道路由

```java
// PipelineService 内：只通知 supports(level) 的渠道，渠道异常相互隔离
for (NotificationChannel channel : channels) {
    if (!channel.supports(alert.getLevel())) continue;
    results.put(channel.name(), safeNotify(channel, alert)); // try-catch 兜底
}
```

### 3.2 PhoneAlarm 闹钟时间

不解析邮件时间，统一设为「当前时间 + 5 分钟」的提醒（告警场景语义），文本带级别与主题。

### 3.3 DingTalk webhook

POST `{"msgtype":"text","text":{"content":"[级别] 主题"}}`；未配置 webhook → SKIPPED。

### 3.4 SSE

`/mail-alert/events/stream` 返回 `SseEmitter(0L)`（不超时）；断开时从注册表移除；广播失败移除该连接。

## 4. 单测计划

全部纯 Mock（Mockito），不起 Spring 上下文、不依赖 DB/Redis/网络，保证可重复执行。
测试命名遵循 `test{方法}{场景}{结果}` 风格（项目内 mq 测试为简化命名，取两者折中：类内小写下划线自由命名但断言完整覆盖场景）。
