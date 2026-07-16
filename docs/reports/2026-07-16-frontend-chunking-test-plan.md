# 前端依赖分包优化测试方案

## 1. 测试目标

验证 Vite 自定义分包配置能够成功生成生产构建产物，将业务入口、框架、HTTP 客户端、Element Plus 和其他第三方依赖分离，同时明确记录未解决的大分块告警与依赖安全风险。

## 2. 测试范围

- `vite.config.js` 的 Rollup `manualChunks` 配置。
- Vue、Pinia、Axios、Element Plus 和其余第三方依赖的产物归类。
- 现有路由页面的生产编译。
- 构建产物大小与 gzip 大小。
- npm 依赖安全审计。

## 3. 测试用例

| 场景 | 预期结果 |
|---|---|
| 执行生产构建 | Vite 构建成功，无编译错误 |
| 构建业务入口 | 入口不再包含全部第三方依赖 |
| 构建 Vue/Pinia 依赖 | 生成 `vue-vendor` 分块 |
| 构建 Axios 依赖 | 生成 `http-vendor` 分块 |
| 构建 Element Plus | 生成独立 `element-plus` JavaScript/CSS 分块 |
| 构建其他依赖 | 生成通用 `vendor` 分块 |
| 检查大分块告警 | 不隐藏告警，准确识别 Element Plus 剩余风险 |
| 执行依赖审计 | 记录漏洞数量和自动修复的破坏性升级影响 |

## 4. 执行命令

```bash
npm run build
npm audit --audit-level=moderate
git diff --check
```

## 5. 通过标准

- `npm run build` 返回 0。
- 业务入口与主要第三方依赖形成独立分块。
- 页面模块继续正常生成生产产物。
- 不通过调大 `chunkSizeWarningLimit` 隐藏问题。
- 审计失败时如实记录，不在缺少兼容性回归的情况下执行 `--force`。

## 6. 非本轮范围

- Element Plus 按需自动导入及全页面 UI 回归。
- Vite 8 大版本升级。
- 浏览器端首屏性能、网络瀑布和交互回归测试。
