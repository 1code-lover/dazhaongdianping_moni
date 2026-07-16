# Vite 生产构建入口包过大

## 问题现象

前端执行 `npm run build` 时，生产入口 JavaScript 文件约为 1,095.46 kB，gzip 后约 364.45 kB，并触发 Vite 的 500 kB 分块告警。业务代码、Vue 运行时、Axios、Element Plus 和其他第三方依赖集中在同一入口包中，任一业务代码变更都可能导致浏览器重新下载整包。

## 排查过程

1. 检查 `vite.config.js`，确认构建配置只有输出目录，没有自定义依赖分包策略。
2. 检查 `src/main.js`，确认 Element Plus 通过 `app.use(ElementPlus)` 和完整 CSS 全局引入。
3. 对生产构建产物分类，确认框架、HTTP 客户端、UI 组件库和其他依赖没有形成稳定的缓存边界。
4. 对比两种优化范围：仅调整 Rollup 分包不改变运行行为；Element Plus 按需引入需要新增插件、改造入口并回归全部页面，风险明显更高。
5. 检查 `npm audit`，确认当前 Vite 间接依赖的 esbuild 存在安全公告，但自动修复会将 Vite 强制升级到 8.1.5，属于破坏性升级。

## 根因

项目没有配置 `build.rollupOptions.output.manualChunks`，第三方依赖默认合并进主入口产物；同时 Element Plus 采用全量全局注册，使 UI 组件库本身仍占据较大体积。

## 解决方案

在 `heima_qianduan/nginx-1.18.0/html/hmdp/vite.config.js` 中增加函数式 `manualChunks`：

1. 将 Element Plus 独立为 `element-plus` 分块。
2. 将 Vue、Vue Router 和 Pinia 归入 `vue-vendor` 分块。
3. 将 Axios 独立为 `http-vendor` 分块。
4. 将剩余 `node_modules` 依赖归入通用 `vendor` 分块。
5. 业务模块继续沿用路由动态导入形成的页面分块。

本轮不提高 `chunkSizeWarningLimit` 来隐藏告警，也不直接执行 `npm audit fix --force`。

## 验证结果

- `npm run build`：通过，Vite 4.5.14 成功转换 1,678 个模块。
- 主入口 JavaScript：由约 1,095.46 kB（gzip 364.45 kB）调整为 8.37 kB（gzip 3.36 kB）。
- 新增稳定依赖分块：`http-vendor` 45.44 kB、`vendor` 115.91 kB、`vue-vendor` 123.53 kB、`element-plus` 803.67 kB。
- Element Plus 仍超过 500 kB，告警如实保留；本轮改善的是缓存边界和入口稳定性，不代表首次加载总下载体积大幅减少。
- `npm audit --audit-level=moderate`：未通过，仍报告 2 个漏洞（1 moderate、1 high）；建议另开兼容性任务评估 Vite 大版本升级。

## 关键收获

前端性能优化应区分“分包与缓存收益”和“真实下载体积下降”：先用低风险分包隔离稳定依赖，再通过独立迭代验证组件库按需引入，避免为了消除告警而隐藏阈值或盲目跨大版本升级。
