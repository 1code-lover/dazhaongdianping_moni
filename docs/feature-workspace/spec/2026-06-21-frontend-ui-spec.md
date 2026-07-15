## 前端页面美化需求

### 1. 背景

当前前端页面存在以下问题：

- 首页、个人中心、订单页等页面视觉层次弱，整体观感接近默认组件堆叠。
- 个人中心左侧菜单宽度不足，导致菜单文字被挤压并出现横向滚动条。
- 页面之间缺少统一的色彩、卡片、按钮和间距体系，浏览体验割裂。
- 多个页面未充分利用商户图片数据，列表与详情页信息展示质感不足。
- 移动端和窄屏下的布局适配能力弱，存在显示不自然的问题。

### 2. 目标

- 页面整体观感更精致，符合“本地生活平台”的产品定位。
- 核心页面在桌面端和移动端都能正常显示。
- 修复个人中心菜单挤压和错位问题。
- 统一首页、列表页、详情页、订单页、个人中心的视觉风格。

### 3. 范围

- 前端全局样式文件：`heima_qianduan/nginx-1.18.0/html/hmdp/src/assets/styles.css`
- 应用壳层：`heima_qianduan/nginx-1.18.0/html/hmdp/src/App.vue`
- 首页：`heima_qianduan/nginx-1.18.0/html/hmdp/src/views/Home.vue`
- 商户列表页：`heima_qianduan/nginx-1.18.0/html/hmdp/src/views/ShopList.vue`
- 商户详情页：`heima_qianduan/nginx-1.18.0/html/hmdp/src/views/ShopDetail.vue`
- 订单页：`heima_qianduan/nginx-1.18.0/html/hmdp/src/views/OrderList.vue`
- 个人中心：`heima_qianduan/nginx-1.18.0/html/hmdp/src/views/UserCenter.vue`

### 4. 设计原则

- 采用暖色调主色，避免默认蓝白页面的廉价感。
- 使用统一的玻璃卡片、圆角、阴影和间距系统。
- 首页突出平台价值和分类入口，商户列表与详情页优先展示真实图片。
- 个人中心采用稳定双栏布局，彻底移除原有菜单被压缩问题。
- 所有关键页面补充移动端断点样式。

### 5. 验收标准

- 个人中心菜单文字完整显示，不再出现横向滚动条。
- 首页分类、推荐商户、顶部导航具备统一视觉风格。
- 商户列表与详情页能展示图片，布局层级清晰。
- 订单页操作区与状态展示清晰可读。
- `npm run build` 构建通过。
