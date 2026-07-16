# 项目首轮安全与测试稳定性修复

## 基本信息

- 类型：bug
- 日期：2026-07-16
- 相关模块：测试基础设施、后台鉴权、图片上传、分页边界、配置安全
- 相关文件：
  - `src/test/java/com/hmdp/service/ReviewServiceTest.java`
  - `src/main/java/com/hmdp/config/MvcConfig.java`
  - `src/main/java/com/hmdp/utils/AdminInterceptor.java`
  - `src/main/java/com/hmdp/controller/UploadController.java`
  - `src/main/java/com/hmdp/config/UploadProperties.java`
  - `src/main/java/com/hmdp/utils/PageUtils.java`
  - `src/main/resources/application.yaml`
  - `heima_qianduan/nginx-1.18.0/conf/nginx.conf`

## 问题现象

项目执行 `mvn test` 时首先因 `ReviewServiceTest` 调用不存在的 `Result.isSuccess()` 而无法编译。代码审计还发现 `/admin/**` 和 `/upload/**` 存在认证绕过，失败任务接口只校验登录未校验管理员权限；上传功能信任扩展名并允许外部路径参与删除，分页参数也可以直接传入超大 `size`。此外，公共配置与示例配置保留了弱口令默认值。

## 根因分析

这些问题来自多个局部实现缺少统一边界：测试没有遵循现有 DTO 契约；权限模型只有“是否登录”而没有后台授权；上传仍沿用开发机静态目录和客户端文件名思路；分页校验散落在业务代码中；开发便利口令被写入了版本库配置。外置上传目录改造后还需要同时处理 Nginx 到 Spring Boot 的资源访问链路，否则会出现保存成功但浏览器访问 404。

## 解决方案

1. 将 7 处测试断言改为现有的 `getSuccess()` API，恢复测试源码编译。
2. 从匿名白名单移除后台和上传接口，新增基于配置化用户 ID 白名单的 `AdminInterceptor`，对后台审核和失败任务运维接口默认拒绝授权。
3. 将上传目录改为可配置外置目录，增加空文件、大小、扩展名、MIME 和文件魔数校验，使用 UUID 服务端重命名，并将删除接口改为 DELETE 和规范化路径校验。
4. 在 Spring MVC 映射外置目录，并在 Nginx 中代理 `/imgs/**`、`/blogs/**`，闭合前端访问链路。
5. 新增 `PageUtils`，统一页码下限、默认每页数量和最大 50 条限制，并适配 MyBatis-Plus 与 Spring Data 的页码语义。
6. 移除公共配置弱口令回退值，扩展配置回归测试。

## 为什么选这个方案

当前用户模型没有角色字段或 RBAC 表，因此先采用“登录校验 + 配置化管理员白名单 + 空配置默认拒绝”作为可快速落地的最小权限边界。上传采用外置目录而不是继续写源码或 JAR 内资源，兼顾本地运行与部署持久化；同时保留历史 `/blogs/**` 路径，减少已有数据和前端展示的兼容风险。分页边界收敛到无状态工具类，避免每个服务重复实现并出现不一致。

## 其他方案与为什么没选

- 完整引入 Spring Security 与数据库 RBAC：长期更合理，但需要新增角色、权限和迁移脚本，超出本轮高风险缺陷的最小修复范围。
- 直接执行 `npm audit fix --force`：会将 Vite 4 强制升级到 Vite 8，属于破坏性升级，需要独立的前端兼容性验证，因此本轮只记录风险。
- 继续将图片写入 Nginx 或源码目录：部署节点耦合强，JAR 场景不可持续，也不利于后续对象存储迁移，因此未采用。

## 验证与结果

- `mvn -q -DskipTests test`：通过，主代码和测试代码均可编译。
- 定向执行配置、上传、管理员、分页和商家申请测试：通过，共覆盖 26 个测试方法。
- `nginx.exe -t -p <nginx目录> -c conf/nginx.conf`：通过。
- `npm run build`：通过，主包约 1,095.46 kB，gzip 约 364.45 kB。
- `mvn -q test`：共 75 个测试，0 failures、17 errors；剩余错误均由本机 Redis `127.0.0.1:6379` 未启动导致。
- `npm audit --audit-level=moderate`：发现 2 个漏洞，修复需要破坏性升级到 Vite 8，已记录为下一阶段任务。

## 面试表达版本

我接手项目后先恢复了测试编译链路，然后通过审计发现后台接口、失败任务和文件上传的权限边界不完整。我在没有现成 RBAC 的情况下，用配置化管理员白名单做了默认拒绝的最小授权方案，同时把上传改成外置存储，补齐 MIME、魔数、路径穿越和 Nginx 访问链路。分页边界也统一收敛到工具类，避免超大查询。最后我用定向单测、测试源码编译、Nginx 配置检查和前端构建验证了改动，并把 Redis 环境依赖和 Vite 破坏性升级风险明确留给后续阶段。
