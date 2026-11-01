# 邮件告警链路（Mock 版）+ 多渠道提醒 设计方案

> 状态：待审核
> 日期：2026-10-31
> 作者：ethan

## 1. 需求背景

本项目当前没有邮件依赖（pom 中无 `spring-boot-starter-mail`），也不存在授权码配置。为了让
「告警邮件 → 解析分级 → 多渠道提醒」整条链路可以先行跑通，需要一个 **Mock 邮件源**：
假装有几份邮件（接口造数 + 预置样例），驱动后续所有环节。

用户同时要求补充两类提醒渠道，作为"链路跑通后还能加什么"的答案：

1. **浏览器弹窗提醒**：可以直接真实弹出，验证效果直观；
2. **手机闹钟提醒**：给出完整设计（ADB 适配器 + 降级 + 扩展方向），本次实现 ADB 通道与可开关配置。

## 2. 需求分析

| 编号 | 需求 | 说明 |
|------|------|------|
| R1 | Mock 邮件源 | 内置预置邮件样例 + `POST /mail-alert/mock/emails` 造数接口，邮件含发件人、主题、正文、收件时间 |
| R2 | 邮件分级 | 按主题关键词将邮件分为 `CRITICAL` / `WARNING` / `INFO` 三级（例：超卖、熔断 → CRITICAL） |
| R3 | 去重 | 同一封邮件（按 messageKey）重复投递只提醒一次 |
| R4 | 多渠道分发 | 按级别路由渠道：CRITICAL → 弹窗+钉钉+闹钟；WARNING → 弹窗+钉钉；INFO → 弹窗 |
| R5 | 弹窗提醒 | SSE 推送 + demo 页面 `Notification` API 真实弹出，可测试推送 |
| R6 | 手机闹钟 | ADB 设置系统闹钟（Postman 4.x 以上的时钟组件 `com.android.deskclock`），无设备/无 adb 时降级为"模拟设置"并记录日志，可开关 |
| R7 | 钉钉 Webhook | CRITICAL/WARNING 级别的扩展渠道示例，未配置时自动跳过 |
| R8 | 全链路演示 | `POST /mail-alert/pipeline/run` 一键跑通全链路并返回每封邮件的分级与渠道结果 |
| R9 | 预留真实邮件接入 | 保留 `MailReceiver` 接口，将来只需实现 IMAP 实现类 + 配置授权码即可替换 Mock |

## 3. 业务流程

```
MockMailSource（预置样例 / 造数接口）
        │  MailMessage{messageKey, from, subject, content, receivedAt}
        ▼
MailClassifier（关键词规则分级 CRITICAL / WARNING / INFO）
        ▼
MailAlertDeduper（内存去重，同 messageKey 只处理一次）
        ▼
MailAlertPipelineService（编排：分级 → 去重 → 分发）
        ▼
NotificationChannel（接口） ── 按 level 路由
   ├── PopupNotificationChannel  → SSE 广播给浏览器（demo 页真实弹窗）
   ├── DingTalkNotificationChannel → webhook POST（未配置则跳过）
   └── PhoneAlarmNotificationChannel → adb shell alarm 命令（可开关，失败降级）
```

## 4. 数据模型

不落库（内存即可跑通链路，后续接真实邮件时再考虑持久化）：

```java
/** 邮件消息 */
public class MailMessage {
    private String messageKey;   // 唯一键，用于去重
    private String from;         // 发件人
    private String subject;      // 主题
    private String content;      // 正文
    private LocalDateTime receivedAt; // 收件时间
}

/** 邮件分级枚举 */
public enum MailLevel { CRITICAL, WARNING, INFO }

/** 提醒渠道接口 */
public interface NotificationChannel {
    String name();
    boolean supports(MailLevel level);
    ChannelResult notify(MailAlert alert); // 返回 SENT / SKIPPED / FAILED
}

/** 提醒事件（分级后） */
public class MailAlert {
    private MailMessage mail;
    private MailLevel level;
    private LocalDateTime alertTime;
}
```

## 5. 接口设计

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/mail-alert/mock/emails` | 返回预置 Mock 邮件（不触发链路） |
| POST | `/mail-alert/mock/emails` | 造一封邮件并立即走全链路，body: `{from, subject, content}` |
| POST | `/mail-alert/pipeline/run` | 一键：加载预置邮件 → 分级 → 去重 → 分发，返回明细 |
| GET | `/mail-alert/events/stream` | SSE 订阅，浏览器接收弹窗推送 |
| POST | `/mail-alert/popup/test` | 手动触发一条测试弹窗推送 |

统一返回 `Result` 结构，与项目其他接口一致。

## 6. 分级规则（关键词可配置）

```yaml
app:
  notify:
    enabled: ${NOTIFY_ENABLED:true}
    mail:
      # 逗号分隔关键词，命中即该级别，优先级 CRITICAL > WARNING > INFO
      critical-keywords: 超卖,库存不一致,熔断,宕机,资损,OOM
      warning-keywords: 积压,延迟升高,重试失败,慢查询,磁盘告警
    popup:
      enabled: true
    dingtalk:
      enabled: true
      webhook: ${DINGTALK_WEBHOOK:}
    phone-alarm:
      enabled: ${PHONE_ALARM_ENABLED:false}
      adb-path: adb
      device-serial: ${PHONE_ALARM_SERIAL:}
      clock-package: com.android.deskclock
```

分级优先级：主题或正文任一命中 CRITICAL 关键词 → CRITICAL；否则命中 WARNING → WARNING；否则 INFO。

## 7. 渠道路由

| 级别 | 弹窗(SSE) | 钉钉 | 手机闹钟 |
|------|-----------|------|----------|
| CRITICAL | ✅ | ✅ | ✅ |
| WARNING | ✅ | ✅ | ❌ |
| INFO | ✅ | ❌ | ❌ |

每个渠道 `supports(level)` 决定是否参与；`enabled=false` 或依赖缺失时返回 `SKIPPED`，不让单渠道失败影响其他渠道。

## 8. 手机闹钟设计（R6 完整设计）

### 8.1 通道实现

通过 `adb shell` 调用系统时钟应用设置闹钟（以 AOSP/多数国产 ROM 的 `com.android.deskclock` 为例）：

```
adb [-s serial] shell am start -n com.android.deskclock/.HandleSetAlarm \
    --es android.intent.extra.alarm.HOUR <h> \
    --es android.intent.extra.alarm.MINUTES <m> \
    --es android.intent.extra.alarm.MESSAGE "<告警文本>"
```

`HandleSetAlarm` activity 会接收参数并创建一个"距现在 +N 分钟"的闹钟，弹窗可手动确认关闭。

### 8.2 降级链

1. `phone-alarm.enabled=false` → SKIPPED（默认关闭，避免 CI 环境误触发）；
2. adb 不在 PATH / 执行失败 → FAILED，记录 warn 日志，不影响其他渠道；
3. 未检测到设备（`adb devices` 为空）→ SKIPPED，提示连接手机并开启 USB 调试。

### 8.3 扩展方向（面试可讲）

- **Push 通道**：接入极光/FCM/APNs，服务端发推送 → 手机本地起 AlarmManager 精确闹钟，解决 adb 只适合"自己手机 + 开发调试"的问题；
- **短信/电话通道**：阿里云语音通知，适合 CRITICAL 级"睡觉也要叫醒"场景；
- **企业微信/飞书**：与钉钉同构，实现同一个 `NotificationChannel` 即可插拔。

## 9. 弹窗提醒设计（R5）

- 后端：`SseEmitter` 管理订阅连接，广播 JSON `{level, subject, content, time}`；
- 前端：`src/main/resources/static/mail-alert-demo.html` demo 页，
  `new EventSource('/mail-alert/events/stream')` 订阅，
  `Notification` API 请求权限后真实弹出系统级通知，页面内同时保留历史列表兜底。

## 10. 非功能要求

- 函数 ≤30 行、参数 ≤4 个、单一职责（AGENTS.md 规范）；
- 所有新增类带文件头注释与函数注释（中文）；
- 外部调用（adb、HTTP webhook）必须有异常处理与超时；
- 单测覆盖率 ≥80%，Service 层 mock 外部依赖；
- 不引入新依赖（webhook 用 spring-web 自带 RestTemplate/WebClient 均可，选 RestTemplate 简单可靠）。

## 10. 增补设计（2026-11-01）：真实邮箱接入

授权码就绪后新增两个能力，与 Mock 链路共存：

### 10.1 SMTP 发信渠道（第 4 个通知渠道）

- `MailNotificationChannel`：CRITICAL/WARNING 级别告警通过 SMTP 发到配置的收件邮箱；
- SMTP 参数在 `spring.mail` 下配置：`host=${MAIL_HOST:smtp.163.com}`、`username=${MAIL_USERNAME:}`、`password=${MAIL_AUTH_CODE:}`（授权码走环境变量，不入库不入代码）；
- 收件人 `app.notify.mail-sender.to=${MAIL_TO:}`；账号或收件人缺失时渠道 SKIPPED；
- 发件人强制取 SMTP 认证账号（网易等服务商要求 from 与认证账号一致）。

### 10.2 IMAP 读取（真实邮件源，替换 Mock 的"进料口"）

- `MailReceiver` 接口 + `ImapMailReceiver` 实现：复用 spring.mail 账号，IMAPS 连接 `${MAIL_IMAP_HOST:imap.163.com}:993`；
- **UID 基线机制**：首次拉取只记录当前最新 UID 不产生告警（防止历史邮件轰炸），之后每次只取基线之后的新邮件；`messageKey=imap-{uid}` 天然幂等；
- **网易 ID 命令兼容**：登录前发送 IMAP ID 命令，否则网易返回 Unsafe Login；
- **自发邮件回环防护**：发件人等于 SMTP 认证账号的邮件（发信渠道发给自己时的告警回执）不触发告警，但同样标已读并推进基线，切断"发信给自己 → IMAP 再读到 → 再发信"的循环，因此支持 `MAIL_TO` 与发件邮箱相同（告警发回自己邮箱）的场景；
- 单次拉取上限 `max-mails-per-poll`，扫描窗口为 3 倍上限，漏拉可被后续轮次补上；拉取后可标记已读；
- `MailReaderScheduler` 按 `poll-interval-ms`（默认 60s）定时拉取，开关 `MAIL_READER_ENABLED`（默认关）；
- 手动接口：`POST /mail-alert/mail/poll`（不受开关限制）、`GET /mail-alert/mail/reader`（状态）、`POST /mail-alert/mail/reader/reset-baseline`。

### 10.3 用户填写指引（授权码去哪）

启动时设环境变量即可，无需改任何文件：

```
MAIL_USERNAME=你的邮箱@163.com
MAIL_AUTH_CODE=生成的授权码
MAIL_TO=接收告警的邮箱        # 发信渠道收件人
MAIL_READER_ENABLED=true     # 打开 IMAP 定时拉取
```

### 10.4 范围外（原第 11 节相应作废部分）

- ~~真实 IMAP/POP3 收件~~ → 本次已实现 IMAP 读取；
- 提醒历史持久化、Web 管理界面、多租户/用户级路由仍为范围外。

## 11. 范围外（原始范围）

- 真实 IMAP/POP3 收件与授权码配置（预留接口，本次不实现）；
- 提醒历史持久化、Web 管理界面；
- 多租户/用户级路由。
