# 邮箱验证码登录 Plan

- 日期：2026-10-01
- 状态：待实施
- 关联 Spec：`docs/feature-workspace/spec/2026-10-01-email-verify-code-spec.md`

## 1. 总体设计

```
Login.vue (手机号/邮箱自动识别)
   │  POST /user/code?target=xxx
   ▼
UserController ──@RateLimit(已有, 60s/3次/30s冷却)──▶ UserServiceImpl.sendCode
   │                                                      │
   │  1. 正则识别通道 PHONE / EMAIL                         ├─ 重发冷却 SETNX 60s
   │  2. 频控：重发冷却 / target日限 / IP日限                ├─ 日限 INCR + expire 24h
   │  3. 生成6位码 → Redis login:code:{target} (2min)      └─ VerifyCodeSender 发送
   │  4. EmailSender(JavaMail) / LogSender
   ▼
POST /user/login {target, code}
   │
   ├─ 取码 → 错5次作废(attempt计数器) → 成功即删(一次性)
   ├─ 按 phone 或 email 查询用户，不存在则注册
   └─ 签发 token（机制不变）
```

## 2. 改动清单

### 2.1 后端

| 文件 | 动作 | 说明 |
|------|------|------|
| `pom.xml` | 修改 | 新增 `spring-boot-starter-mail` |
| `src/main/resources/db/user_email_column.sql` | 新增 | `ALTER TABLE tb_user ADD COLUMN email` + 普通索引 |
| `src/main/java/com/hmdp/entity/User.java` | 修改 | 新增 `email` 字段 |
| `src/main/java/com/hmdp/dto/LoginFormDTO.java` | 修改 | 新增 `target` 字段（保留 `phone` 兼容） |
| `src/main/java/com/hmdp/service/verify/VerifyCodeChannel.java` | 新增 | 通道枚举 + `detect(target)` 正则识别 |
| `src/main/java/com/hmdp/service/verify/VerifyCodeSender.java` | 新增 | 发送接口：`supports(channel)` / `send(target, code)` |
| `src/main/java/com/hmdp/service/verify/LogVerifyCodeSender.java` | 新增 | 日志通道（默认兜底） |
| `src/main/java/com/hmdp/service/verify/EmailVerifyCodeSender.java` | 新增 | 邮箱通道（JavaMailSender，channel=email 时装配） |
| `src/main/java/com/hmdp/config/VerifyCodeProperties.java` | 新增 | `app.verify-code.*` 配置绑定 |
| `src/main/java/com/hmdp/config/VerifyCodeSenderConfig.java` | 新增 | 按 channel 装配 Sender 的 @Configuration |
| `src/main/java/com/hmdp/utils/RedisConstants.java` | 修改 | 新增重发冷却/日限/尝试次数 key 前缀 |
| `src/main/java/com/hmdp/service/IUserService.java` | 修改 | `sendCode(String target, ...)` 签名语义更新 |
| `src/main/java/com/hmdp/service/impl/UserServiceImpl.java` | 修改 | sendCode 频控+通道发送；login 双通道+防爆破+一次性 |
| `src/main/java/com/hmdp/controller/UserController.java` | 修改 | `code` 接口接收 `target`（兼容 `phone`） |
| `src/main/resources/application.yaml` | 修改 | 新增 `app.verify-code.*` 与 `spring.mail.*` 占位 |

### 2.2 前端

| 文件 | 动作 | 说明 |
|------|------|------|
| `src/api/user.js` | 修改 | `sendCode(target)` / `login(target, code)` |
| `src/views/Login.vue` | 修改 | 输入框支持手机号/邮箱识别；标签/校验/提示动态化 |

### 2.3 文档与数据

| 文件 | 动作 |
|------|------|
| `docs/feature-workspace/INDEX.md` | 登记 spec/plan |
| 数据库 | 执行 `user_email_column.sql` |

## 3. 关键实现要点

1. **兼容策略**：`/user/code` 同时接受 `target` 与 `phone` 参数（取非空者）；`LoginFormDTO.target` 为空时回退 `phone`。老前端/老脚本不断链。
2. **频控键设计**：
   - 重发冷却：`verify:resend:{target}` SETNX EX 60，存在即拒绝
   - target 日限：`verify:day:{target}` INCR，>10 拒绝，首次设置 EX 86400
   - IP 日限：`verify:dayip:{ip}` INCR，>20 拒绝
   - 尝试次数：`verify:attempt:{target}` INCR 随码生命周期（2min），≥5 删除码与计数
3. **邮箱发送**：`MimeMessage` 简单文本邮件，主题「【黑马点评】登录验证码」，正文含码与有效期提示；发送异常捕获后返回 `Result.fail("验证码发送失败，请稍后重试")`（**此时不保留验证码**，避免用户收不到却拿着旧码试）。
4. **EmailSender 装配**：`VerifyCodeSenderConfig` 按 `app.verify-code.channel` 返回对应 Bean；`channel=log` 时不创建 JavaMailSender 依赖，避免无配置启动报错。
5. **注册逻辑**：邮箱登录的新用户 `phone` 留空（DB 允许），`nick_name` 前缀 `user_` + 随机；头像用默认 icon。

## 4. 验证计划

1. `mvn clean package -Dmaven.test.skip=true` 编译通过
2. 执行迁移 SQL，重启后端
3. `channel=log`：
   - 手机号发送 → Redis 有码，60s 内重发被拒
   - 连续输错 5 次 → 验证码作废
   - 登录成功 → 码被删除，token 正常返回
4. `channel=email`（用户填入授权码后）：QQ 邮箱真实收码验证
5. 前端：登录页输入邮箱 → 校验通过 → 发送倒计时生效
6. 回归：旧手机号登录、签到、个人中心不受影响

## 5. 风险与回滚

| 风险 | 应对 |
|------|------|
| 邮件进垃圾箱 | 文案提示用户检查垃圾箱；发件人昵称设为「黑马点评」 |
| 用户不填邮件配置却切到 email 通道 | 启动时装配 JavaMailSender 不校验凭据（懒连接），发送时才失败并明确报错 |
| 兼容期旧参数 | controller 双参数兜底，service 统一以 target 语义处理 |
| 回滚 | 代码回滚 + `ALTER TABLE tb_user DROP COLUMN email`；配置默认 channel=log 即恢复原行为 |
