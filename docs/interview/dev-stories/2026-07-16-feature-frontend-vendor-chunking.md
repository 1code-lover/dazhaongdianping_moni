# 前端第三方依赖分包优化

## 基本信息

- 类型：feature
- 日期：2026-07-16
- 相关模块：Vite 生产构建、前端依赖缓存策略
- 相关文件：
  - `heima_qianduan/nginx-1.18.0/html/hmdp/vite.config.js`
  - `docs/problems/2026-07-16-vite-main-chunk-too-large.md`
  - `docs/reports/2026-07-16-frontend-chunking-test-plan.md`
  - `docs/reports/2026-07-16-frontend-chunking-test-report.md`

## 需求背景

项目生产构建的主 JavaScript 文件约为 1,095.46 kB。业务代码和所有核心依赖共用一个入口产物，既触发 Vite 大分块告警，也会让普通业务改动造成整包缓存失效。当前工作区还存在其他前端功能开发，因此需要先选择不修改组件调用方式的低风险优化。

## 设计与实现方案

在 Vite 的 Rollup 输出配置中增加函数式 `manualChunks`，只对 `node_modules` 做依赖归类：Element Plus 独立为 `element-plus`，Vue/Pinia 归入 `vue-vendor`，Axios 归入 `http-vendor`，其余依赖进入 `vendor`。业务页面仍使用现有路由懒加载分块，不改变组件注册、请求逻辑或页面行为。

生产构建后，业务入口变为 8.37 kB；框架、HTTP 客户端、通用依赖和 UI 组件库形成了稳定的独立产物，降低业务发布导致核心依赖缓存失效的概率。

## 为什么选这个方案

本轮的约束是工作区已有未提交页面功能，直接改造 Element Plus 按需导入会同时影响入口、组件解析、样式加载和所有页面，回归范围过大。`manualChunks` 只改变构建产物组织，不改变运行时 API，能够先获得缓存隔离收益，也为后续定位最大依赖分块提供清晰数据。

## 其他方案与为什么没选

1. Element Plus 按需导入：能够进一步降低真实体积，但需要引入自动导入插件或逐页维护导入，并完成全页面样式与交互回归，留到独立迭代。
2. 调高 `chunkSizeWarningLimit`：只能消除提示，不能解决缓存或体积问题，因此没有采用。
3. 执行 `npm audit fix --force`：会从 Vite 4.5.14 跨大版本升级到 8.1.5，在缺少兼容性验证时风险过高，因此只记录审计结果。

## 风险与权衡

本次优化显著缩小的是业务入口，而不是等比例缩小首次加载总资源。Element Plus JavaScript 仍为 803.67 kB，CSS 为 357.51 kB；同时 Vite/esbuild 链路仍有 2 个审计漏洞。方案的主要价值是低风险建立稳定缓存边界，真实体积优化和依赖升级需要后续专项处理。

## 验证与结果

- `npm run build`：通过，1,678 个模块成功转换。
- 入口 JavaScript：8.37 kB，gzip 3.36 kB。
- `vue-vendor`：123.53 kB；`http-vendor`：45.44 kB；`vendor`：115.91 kB。
- `element-plus`：803.67 kB，构建仍如实提示大分块告警。
- `npm audit --audit-level=moderate`：未通过，报告 1 个 moderate 和 1 个 high 漏洞，未执行破坏性强制升级。

## 面试表达版本

我发现项目的 Vite 构建把业务代码和核心依赖都打进了一个约 1.1 MB 的入口包，任何业务发布都可能让整包缓存失效。考虑到当时还有页面功能在开发，我没有直接做高风险的 Element Plus 按需改造，而是先用 `manualChunks` 把 Vue、Axios、Element Plus 和其他依赖分层。构建后业务入口降到 8.37 kB，依赖缓存边界也更稳定。我同时明确保留了 Element Plus 803.67 kB 的告警，并把按需引入和 Vite 大版本安全升级拆成后续独立任务，没有用调高阈值或强制升级来掩盖风险。
