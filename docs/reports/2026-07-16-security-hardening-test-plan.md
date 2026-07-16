# 项目首轮安全与稳定性优化测试方案

## 1. 测试目标

验证本轮测试编译修复、后台鉴权、上传安全、配置脱敏和分页边界治理不会破坏项目编译，并覆盖关键正常、异常和越权场景。

## 2. 测试范围

- `ReviewServiceTest` 测试 API 契约修复。
- `AdminInterceptor` 的认证与管理员授权。
- `UploadController` 的文件内容校验、外置存储和安全删除。
- `PageUtils` 的页码及每页数量边界。
- `application.yaml` 与 `application-local.example.yaml` 的弱口令治理。
- Nginx 对外置上传图片路径的反向代理。
- `ShopApplyServiceTest` 的 MyBatis-Plus 测试夹具。
- 后端完整 Maven 测试和前端生产构建。
- 前端 npm 依赖安全审计。

## 3. 测试用例

| 模块 | 场景 | 预期结果 |
|---|---|---|
| 管理员鉴权 | 未登录访问 | 返回 401 |
| 管理员鉴权 | 普通用户访问 | 返回 403 |
| 管理员鉴权 | 白名单管理员访问 | 放行 |
| 管理员鉴权 | 白名单为空 | 默认拒绝 |
| 管理员鉴权 | 白名单格式非法 | 启动阶段快速失败 |
| 图片上传 | 合法 PNG | 保存到外置目录并返回访问路径 |
| 图片上传 | 空文件/超限文件 | 拒绝 |
| 图片上传 | 非白名单扩展名 | 拒绝 |
| 图片上传 | MIME 或魔数不匹配 | 拒绝 |
| 图片删除 | 系统生成 Blog 路径 | 删除成功 |
| 图片删除 | `../` 目录穿越 | 拒绝且根目录外文件不受影响 |
| 分页 | 页码为空、0、负数 | 规范为第 1 页 |
| 分页 | size 为空或非正数 | 使用默认 10 |
| 分页 | size 超过 50 | 限制为 50 |
| 配置模板 | 公共配置或示例出现弱口令 | 测试失败 |
| Nginx 上传资源代理 | `/imgs/`、`/blogs/` 未代理后端 | 测试失败 |
| 商家申请测试 | 父类 baseMapper 注入 | 5 个用例正常执行 |

## 4. 执行命令

```bash
mvn -q -DskipTests test
mvn -q "-Dtest=PageUtilsTest,ConfigurationTemplateSanitizationTest,UploadControllerTest,AdminInterceptorTest,ShopApplyServiceTest" test
mvn -q test
npm run build
npm audit --audit-level=moderate
nginx.exe -t -p <nginx目录> -c conf/nginx.conf
```

## 5. 通过标准

- 主代码和测试代码编译通过。
- 本轮新增及修复的定向测试全部通过。
- 前端生产构建通过。
- 完整测试中的剩余失败必须能明确归因于未启动的外部依赖，不得存在新增代码编译错误或确定性单元测试失败。
- 依赖审计与大包告警记录到测试报告，作为下一阶段输入。
