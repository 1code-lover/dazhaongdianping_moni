# 邮箱验证码登录 Spec

- 日期：2026-10-01
- 状态：待评审
- 关联 Plan：`docs/feature-workspace/plan/2026-10-01-email-verify-code-plan.md`

## 1. 背景与目标

当前登录验证码仅在 `log.debug` 输出，生产日志级别下不可见，外部用户无法获取验证码完成登录。

目标：

1. 保持现有 Redis 验证码机制（`login:code:{target}`，2 分钟 TTL）不变
2. 新增**邮箱通道**免费下发验证码（SMTP，用户自备邮箱授权码，自行填入配置）
3. 登录标识支持**手机号或邮箱**（前端单输入框自动识别）
4. 补齐安全防护：重发冷却、单日上限、错误次数上限、验证码一次性使用
5. 预留短信通道扩展点（`VerifyCodeSender` 接口），后续可接入阿里云短信

## 2. 需求范围

### 2.1 后端

| 需求 | 说明 |
|------|------|
| 通道抽象 | `VerifyCodeSender` 接口，按 `app.verify-code.channel` 配置选择实现（`log` / `email`） |
| 邮箱发送 | 基于 `spring-boot-starter-mail`，QQ 邮箱 SMTP（ssl/465）默认配置占位，密钥走环境变量 `MAIL_PASSWORD` |
| 双通道登录 | `/user/code`、`/user/login` 接口参数由 `phone` 升级为 `target`（手机号或邮箱），旧参数 `phone` 兼容 |
| 频控 | 复用已有 `@RateLimit` 注解（60s/3次/30s冷却）；新增：同 target 60s 重发冷却、单 target 日限 10 条、单 IP 日限 20 条 |
| 防爆破 | 同一 target 连续输错 5 次，验证码作废（删除码 + 计数器） |
| 一次性 | 登录成功立即删除验证码 |
| 数据层 | `tb_user` 新增 `email` 字段（可空），邮箱登录用户按 email 查询/注册 |

### 2.2 前端

- 登录页输入框支持手机号/邮箱自动识别（正则），标签与校验规则动态切换
- 发送验证码按钮 60 秒倒计时（已有，保留）
- 提示文案更新：说明验证码将通过邮箱/日志下发

### 2.3 非目标（本期不做）

- 阿里云短信实际接入（仅预留接口与配置项）
- 图形验证码/滑块
- 邮箱绑定已有手机号账号的合并流程

## 3. 接口变更

### POST /user/code

```http
POST /user/code?target=13800138000
POST /user/code?target=foo@qq.com
```

响应不变（`Result.ok()`）；`phone` 参数保留兼容。

### POST /user/login

```json
{ "target": "foo@qq.com", "code": "123456" }
```

`phone` 字段保留兼容（旧前端不受影响）。响应不变（token）。

## 4. 配置项

```yaml
app:
  verify-code:
    channel: ${VERIFY_CODE_CHANNEL:log}   # log | email
    resend-interval-seconds: 60
    max-per-target-per-day: 10
    max-per-ip-per-day: 20
    max-verify-attempts: 5
spring:
  mail:
    host: ${MAIL_HOST:smtp.qq.com}
    port: ${MAIL_PORT:465}
    username: ${MAIL_USERNAME:}           # 用户自行填写
    password: ${MAIL_PASSWORD:}           # 用户自行填写（SMTP授权码）
```

## 5. 验收标准

1. `channel=log` 时行为与现状一致（验证码打日志 + Redis），旧手机号登录流程不受影响
2. `channel=email` 且邮件配置完整时，验证码真实发送到目标邮箱
3. 60 秒内重复发送被拒；单 target 单日超过 10 次被拒；单 IP 单日超过 20 次被拒
4. 连续输错 5 次后验证码作废，需重新获取
5. 登录成功后该验证码立即失效
6. 邮箱格式错误、手机号格式错误分别返回明确提示
