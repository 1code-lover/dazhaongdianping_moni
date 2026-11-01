# SSE 订阅被登录拦截器 401（EventSource 无法携带请求头）

## 问题现象

邮件告警演示页的 SSE 订阅（`new EventSource('/mail-alert/events/stream')`）始终连接失败；用 curl 直接请求该端点返回 401。而其他告警接口在携带 token 后均正常。

## 排查过程

1. curl 直连 `/mail-alert/events/stream` 返回 401，确认是 `LoginInterceptor` 拦截而非 SSE 实现问题；
2. 查 `MvcConfig`，`/mail-alert/**` 未加入 `excludePathPatterns`，而 SSE 端点属于该前缀；
3. 尝试给 EventSource 加 header 发现 API 根本不支持——`EventSource` 只能发 GET、无法自定义请求头（浏览器规范限制，非实现问题）；
4. 结论：凡走 SSE 的浏览器端点都无法走常规 `authorization` 头鉴权。

## 根因

项目登录态从 `authorization` 请求头读取（`RefreshTokenInterceptor`），而浏览器原生 EventSource 按规范不支持自定义请求头，两者天然冲突；`/mail-alert/**` 又恰好没有像 `/shop/**` 一样加入放行名单。

## 解决方案

将 `/mail-alert/**` 与演示页 `/mail-alert-demo.html` 加入 `LoginInterceptor` 的 `excludePathPatterns`。理由：该组为告警演示端点（分级、去重、SSE 推送均为只读/演示性质），与项目已有的公开查询端点（`/shop/**`、`/combo/*`）同级；后台管理类端点不受影响。

若未来需要鉴权的 SSE，可选方案：token 通过查询参数传递（有日志泄漏风险，需短期一次性 token）、或改用轮询/Fetch 流式读取（fetch 可带 header）。

## 关键收获

浏览器 EventSource 按规范无法携带自定义请求头，SSE 端点要么放行匿名并做其他安全设计，要么改用支持鉴权的 fetch 流式方案——鉴权方案选型时必须把传输通道能力纳入考虑。
