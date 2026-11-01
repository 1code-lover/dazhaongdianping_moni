# 测试方案：邮件告警链路（Mock 版）+ 多渠道提醒

> 日期：2026-10-31
> 关联设计：docs/design/2026-10-31-mail-alert-pipeline-requirements.md

## 1. 测试范围

只测 `com.hmdp.mail` 包，全部纯单元测试（Mockito），不依赖 Spring 上下文、DB、Redis、网络、真实 adb，保证可重复执行。

| 类 | 覆盖场景 |
|----|----------|
| `MailClassifierTest` | CRITICAL/WARNING 命中、INFO 兜底、大小写不敏感、空主题正文、空关键词配置 |
| `MailAlertDeduperTest` | 首次放行、重复拦截、空键放行、reset、size |
| `MockMailSourceTest` | 预置邮件键唯一、造数键自增、造数字段保留 |
| `MailAlertPipelineServiceTest` | 三级渠道路由、渠道异常隔离、去重集成、总开关关闭 |
| `PhoneAlarmNotificationChannelTest` | 开关关闭跳过、命令构造（含序列号）、无 adb 失败降级、级别路由 |
| `DingTalkNotificationChannelTest` | 未配置 webhook 跳过、级别路由 |
| `SseAlertBroadcasterTest` | 注册/注销计数、向无连接 emitter 广播安全移除 |
| `MailAlertControllerTest` | 造数接口返回处理结果、一键跑通返回列表、预置列表接口 |

## 2. 关键断言

- CRITICAL → popup、dingtalk、phone-alarm 三渠道全部参与；
- WARNING → popup、dingtalk 参与，phone-alarm 不参与；
- INFO → 仅 popup 参与；
- 渠道抛异常时结果为 FAILED 且其他渠道仍 SENT；
- 重复 messageKey 第二次处理状态为 DUPLICATED 且不触达渠道；
- `app.notify.enabled=false` 时全部返回 DISABLED 且不触达渠道。

## 3. 执行方式

```bash
mvn "-Dtest=com.hmdp.mail.*" test
```

（本机无全局 mvn，使用 IDEA 内置 Maven + JDK8，见测试报告执行记录。）

## 4. 覆盖率

按项目规范目标 ≥80%（mail 包新增类）。执行后以 jacoco 报告为准记录实际值。
