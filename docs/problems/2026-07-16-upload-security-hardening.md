# 图片上传接口缺少安全边界

## 问题现象

图片上传接口原先存在以下风险：

- 整个 `/upload/**` 被登录拦截器匿名放行。
- 直接信任原始文件扩展名，未校验 MIME 类型和真实文件头。
- 未显式校验空文件和业务大小上限。
- 上传目录固定写入源码目录 `src/main/resources/static/imgs/`，打包为 JAR 后不可可靠写入和持久化。
- Blog 图片删除使用 GET 请求，并将外部 `name` 直接拼接到本地文件路径，存在目录穿越风险。

## 排查过程

1. 检查 `MvcConfig`，确认上传路径位于匿名白名单。
2. 检查 `UploadController`，确认文件名后缀直接来自用户输入，且删除路径没有规范化和根目录边界校验。
3. 检查 `SystemConstants.IMAGE_UPLOAD_DIR`，确认上传位置依赖项目源码目录。
4. 检查评价上传前端，确认 `ReviewForm.vue` 已携带 `authorization` 请求头，移除匿名放行不会影响已登录评价上传。
5. 检查现有返回路径，保留 Blog 的 `/blogs/**` 兼容路径，并为外置目录增加对应静态资源映射。
6. 检查项目 Nginx 配置，确认浏览器请求 `/imgs/**`、`/blogs/**` 默认不会进入 Spring Boot，因此补充反向代理以闭合访问链路。

## 根因

上传功能沿用了本地开发阶段的静态目录方案，只完成了“保存文件”的功能闭环，没有建立认证、内容校验、存储边界和安全删除约束。

## 解决方案

1. 上传接口纳入登录校验。
2. 新增 `UploadProperties`，将目录、大小和扩展名白名单改为外部配置。
3. 默认上传目录调整为 `./uploads/imgs/`，通过 `UPLOAD_DIR` 支持部署环境覆盖。
4. 同时设置 Spring Multipart 请求大小限制和控制器业务大小限制。
5. 上传时校验空文件、扩展名、MIME 类型以及 JPEG/PNG/WebP 文件魔数。
6. 使用 UUID 和两级散列目录生成服务端文件名，不使用客户端文件名作为保存路径。
7. 将 Blog 删除接口调整为 DELETE，并仅允许删除符合系统生成规则的 `blogs/{hex}/{hex}/{uuid}.{ext}` 路径。
8. 对删除目标执行路径规范化及上传根目录边界校验，阻断 `../` 目录穿越。
9. 映射 `/imgs/**` 到外置目录，同时保留 `/blogs/**` 兼容访问路径。
10. 在项目 Nginx 中代理 `/imgs/**` 和 `/blogs/**` 到后端，避免上传成功后由前端站点直接访问时返回 404。

## 验证结果

- `mvn -q -DskipTests compile`：通过。
- `mvn -q "-Dtest=ConfigurationTemplateSanitizationTest,UploadControllerTest,AdminInterceptorTest,PageUtilsTest,ShopApplyServiceTest" test`：通过。
- `nginx.exe -t -p <nginx目录> -c conf/nginx.conf`：通过，配置语法和运行时路径检查成功。
- 上传测试覆盖：合法 PNG、空文件、非法扩展名、MIME 不匹配、伪造文件头、超限文件、安全删除和目录穿越，共 8 个场景。

## 已知限制

当前业务表没有记录图片所有者，删除接口只能校验“路径是否由系统生成”，还不能校验“当前用户是否是图片所有者”。后续应增加上传记录表或将图片与业务草稿绑定，实现资源级授权。

## 关键收获

文件上传安全不能只依赖扩展名，应同时建立认证、大小限制、MIME 与魔数校验、服务端重命名、外置存储和规范化路径边界。
