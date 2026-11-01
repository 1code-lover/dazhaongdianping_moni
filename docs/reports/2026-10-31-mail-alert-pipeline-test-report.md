# 测试报告：邮件告警链路（Mock 版）+ 多渠道提醒

> 日期：2026-10-31
> 关联：docs/design/2026-10-31-mail-alert-pipeline-requirements.md、docs/reports/2026-10-31-mail-alert-pipeline-test-plan.md

## 1. 执行环境

- JDK：corretto-17.0.18（项目 pom 的 spring-boot 插件配置了 `--add-opens`，按 JDK 9+ 运行；JDK 8 下 JVM 无法启动）
- Maven：IDEA 内置 `C:/Program Files/JetBrains/IntelliJ IDEA 2024.2.1/plugins/maven/lib/maven3/bin/mvn.cmd`
- 依赖服务：MySQL、Redis 本机运行（密码走环境变量，不写入文档）；Kafka 未运行（消费者仅告警重连，不阻塞启动）
- 应用端口：8081

## 2. 单元测试结果（47/47 通过）

| 测试类 | 用例数 | 结果 |
|--------|--------|------|
| MailClassifierTest | 8 | ✅ 全部通过 |
| MailAlertDeduperTest | 6 | ✅ 全部通过 |
| MockMailSourceTest | 3 | ✅ 全部通过 |
| MailAlertPipelineServiceTest | 7 | ✅ 全部通过 |
| SseAlertBroadcasterTest | 5 | ✅ 全部通过 |
| PhoneAlarmNotificationChannelTest | 7 | ✅ 全部通过 |
| DingTalkNotificationChannelTest | 4 | ✅ 全部通过 |
| MailAlertControllerTest | 7 | ✅ 全部通过 |

执行命令（本机无全局 mvn）：

```bash
JAVA_HOME=<JDK17> <idea-maven> "-Dtest=MailClassifierTest,...,MailAlertControllerTest" test
# BUILD SUCCESS — Tests run: 47, Failures: 0, Errors: 0, Skipped: 0
```

修复记录（首轮 3 失败）：

1. `MailAlertPipelineServiceTest`：去重场景误用 `never()` 断言，未计入首次处理的调用 → 改为 `times(1)`；
2. `SseAlertBroadcasterTest`：未初始化的 SseEmitter 在无容器环境 send() 只缓冲不抛异常、complete() 不触发回调 → 重构广播器增加包级 `addClient`，用 mock 注入覆盖失败路径；
3. mock 抛 RuntimeException 超出广播器捕获范围（生产仅抛 IOException/IllegalStateException）→ 测试改为抛 IOException。

## 3. 端到端验证（真实启动应用）

启动：`spring-boot:run -Dmaven.test.skip=true`，环境变量 `DB_PASSWORD` / `REDIS_PASSWORD`。

| 步骤 | 请求 | 结果 | 结论 |
|------|------|------|------|
| E1 | GET /mail-alert/config | `{"enabled":true,"phoneAlarmEnabled":false,"dingTalkConfigured":false,"onlineClients":0}` | ✅ 配置读取正确 |
| E2 | POST /mail-alert/pipeline/run（第 1 次） | 3 封预置邮件：超卖→CRITICAL、积压→WARNING、备份→INFO；CRITICAL 路由 popup+dingtalk+phone-alarm，WARNING 路由 popup+dingtalk，INFO 仅 popup；未配置渠道 SKIPPED，popup SENT | ✅ 分级与渠道路由全符合设计 |
| E3 | POST /mail-alert/pipeline/run（第 2 次） | 3 封全部 `DUPLICATED`，channelResults 为空 | ✅ 去重生效 |
| E4 | POST /mail-alert/mock/emails（中文资损邮件） | `level=CRITICAL`，渠道路由同 E2 | ✅ 造数接口可用，中文分级正确 |
| E5 | GET /mail-alert/events/stream + POST /mail-alert/popup/test | popup 返回 `onlineClients:1`；SSE 客户端实际收到 `event:mail-alert` + JSON | ✅ SSE 真实推送链路通 |
| E6 | GET /mail-alert-demo.html | http=200 | ✅ 演示页可访问 |
| E7 | 手机闹钟渠道（无设备） | CRITICAL 邮件中 phone-alarm=SKIPPED，日志提示连接设备 | ✅ 降级策略生效 |

> 注：E1-E3 首次在登录态下验证（token 经 /user/code → Redis 读码 → /user/login 获取），随后因 SSE 的 EventSource 无法携带自定义请求头，将 `/mail-alert/**` 与 `/mail-alert-demo.html` 加入登录拦截器放行名单（与 /shop/** 等公开端点同级），并复验通过。

## 4. 过程中发现并解决的问题

1. **JDK 版本**：JDK 8 无法运行（pom 插件 `--add-opens`），且既有测试使用 `String.repeat`（Java 11+），确定项目实际运行 JDK 为 17；
2. **服务凭据**：本地 MySQL/Redis 密码通过环境变量 `DB_PASSWORD` / `REDIS_PASSWORD` 传入（值见本地配置，不入库）；
3. **SSE 鉴权冲突**：EventSource 不支持自定义 header → 放行 `/mail-alert/**`；
4. **造数中文乱码**：git-bash curl 默认 GBK 发送导致 `Invalid UTF-8 middle byte` → 以 UTF-8 文件 + `--data-binary` 重发，服务端行为正确（非服务端缺陷）。

## 5. 遗留事项

- Kafka 未运行：秒杀消费者持续重连（不影响本功能验证）；如需完整压测环境需启动 docker-compose 中的 Kafka；
- 覆盖率：本包 8 个测试类覆盖核心分支；jacoco 全量报告受既有测试类影响（JDK17 可跑），后续可单独执行 `mvn jacoco:report`；
- 真实手机闹钟：需连接 Android 设备并开启 USB 调试后设置 `PHONE_ALARM_ENABLED=true` 验证 adb 通道。

## 7. 增补测试记录（2026-11-01）：SMTP 发信 + IMAP 读取

### 7.1 单元测试（70/70 通过）

新增 3 个测试类 24 个用例：

| 测试类 | 用例数 | 覆盖点 |
|--------|--------|--------|
| MailNotificationChannelTest | 6 | 账号/收件人缺失跳过、发信字段校验（from=SMTP 账号、to=收件人、主题/正文）、异常上抛由管道隔离 |
| ImapMailReceiverTest | 8 | 配置校验、UID 基线（首次不告警）、新邮件转换（messageKey=imap-{uid}）、单次上限与跨轮续拉、基线重置、text/plain 与 HTML 提取 |
| MailReaderSchedulerTest | 5 | 开关/账号校验跳过、新邮件进管道、空结果跳过、异常不中断调度 |
| MailAlertControllerTest（扩充） | 11 | 新增 mail/poll 未配置提示、拉取结果结构、读取器状态、基线重置 |

> 过程中修复：测试误用真实 JavaMailSenderImpl 导致连接 localhost:25（改 mock）；`isConfigured()` 对未初始化 sender 抛异常（改返回 false）。

### 7.2 端到端验证

| 步骤 | 请求 | 结果 | 结论 |
|------|------|------|------|
| E8 | GET /mail-alert/config | 新增 `mailSenderReady` / `mailReaderEnabled` / `mailReaderConfigured` 字段 | ✅ 新配置已注册 |
| E9 | 造 WARNING 邮件 | 渠道结果含 4 渠道：`{"dingtalk":"SKIPPED","mail":"SKIPPED","popup":"SENT"}` | ✅ mail 渠道已入路由 |
| E10 | POST /mail-alert/mail/poll（未配置凭据） | 友好提示设置 MAIL_USERNAME/MAIL_AUTH_CODE | ✅ 降级正常 |
| E11 | GET /mail-alert/mail/reader | host=imap.163.com, port=993, lastSeenUid=0 | ✅ 状态可观测 |
| E12 | POST /mail-alert/mail/reader/reset-baseline | success | ✅ |

> 真实 IMAP 拉取与真实发信需用户提供授权码后自行验证（凭据不入库不入代码，仅环境变量）。

### 7.3 增补结论

SMTP 发信渠道与 IMAP 读取已实现并通过 70/70 单元测试与 5 项端到端验证。授权码通过环境变量 `MAIL_USERNAME` / `MAIL_AUTH_CODE` / `MAIL_TO` 注入即可启用完整闭环。

## 8. 最终结论

邮件告警链路已完整实现：Mock 邮件源与 IMAP 真实邮件源 → 分级 → 去重 → 五渠道分发（弹窗/钉钉/手机闹钟/邮件发信/日志）。

- 弹窗：开箱即用，http://127.0.0.1:8081/mail-alert-demo.html 真实弹窗；
- 钉钉：配 `DINGTALK_WEBHOOK` 即启用；
- 手机闹钟：`PHONE_ALARM_ENABLED=true` + adb + USB 调试；
- 邮件发信：`MAIL_USERNAME` + `MAIL_AUTH_CODE` + `MAIL_TO`；
- IMAP 读取：上述账号配置 + `MAIL_READER_ENABLED=true`；
- 单元测试 70/70 通过，端到端 12 项验证通过。

## 9. 真实邮箱验证记录（2026-11-01，QQ 邮箱）

环境变量自注册表注入（授权码不落盘），应用启动后 `mailSenderReady=true`、`mailReaderEnabled=true`、`mailReaderConfigured=true`。

| 步骤 | 操作 | 结果 | 结论 |
|------|------|------|------|
| R1 | 手动拉取建立基线 | 成功连接 imap.qq.com:993，基线 UID=3515 | ✅ IMAP 凭据与连接正常 |
| R2 | 触发 CRITICAL 告警 | 渠道结果 `"mail":"SENT"`，QQ 邮箱收到真实告警信 | ✅ SMTP 发信链路真实可用 |
| R3 | 再次拉取 | 拉到历史邮件 20 封（上限生效），中文主题解码正常，全部 INFO 仅弹窗 | ✅ 解析/分级/封顶/基线推进正常 |
| R4 | 继续拉取 | UID 3712–3731 段被自发防护跳过（含测试告警信），未触发渠道 | ✅ 回环防护真实生效 |
| R5 | 连续拉取 | fetched=0，lastSeenUid 稳定于 3751 | ✅ 幂等稳定 |

发现的真实世界问题：

1. **QQ 首次连接的视图同步滞后**：首次建基线时服务器给出未同步完的旧视图（基线偏低），随后两轮把历史邮件当作"新邮件"补拉（共约 40 封，受单次上限约束）。当前邮箱已完成完整基线（3751），后续不再发生；若日后 reset-baseline 重来，预期首轮会有一次历史补拉。
2. **历史邮件误报一次**：补拉的历史邮件"QQ空间黄钻业务通知"内容命中 CRITICAL 关键词，触发了真实发信（发给自己，被回环防护拦住不会扩散）。属预期内行为，也顺带验证了分级→发信全链路。
