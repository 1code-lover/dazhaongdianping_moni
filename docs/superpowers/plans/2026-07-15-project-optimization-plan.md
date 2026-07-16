# 项目优化实施计划

## 1. 背景

本计划基于 2026-07-15 的项目体检结果制定，目标是在不扩大功能范围的前提下，优先解决影响项目可运行性、安全性、可维护性和面试展示质量的问题。

已验证事实：

- 后端主代码可编译：`mvn -q -DskipTests compile` 通过。
- 后端测试不可用：`mvn -q test` 因 `ReviewServiceTest` 调用不存在的 `Result.isSuccess()` 编译失败。
- 前端可构建：`npm run build` 通过。
- 前端构建有包体积警告：主 JS chunk 约 1,095 KB。
- 前端依赖审计失败：`vite/esbuild` 存在安全提示，自动修复涉及破坏性升级。
- 当前工作区已有大量未提交改动，实施前需先确认纳入本次优化的范围，避免混入历史整理改动。

## 2. 优化目标

### 2.1 核心目标

1. 恢复基础质量门禁：后端测试至少能完成编译并运行目标测试。
2. 消除高风险接口越权：后台审核、上传、失败任务管理等接口必须具备登录或权限保护。
3. 提升上传链路安全性：增加文件类型、大小、路径穿越、删除语义等保护。
4. 控制分页和查询风险：统一限制分页参数，避免大分页拖垮服务。
5. 统一文档目录和 README 指向，符合项目规范。
6. 规划前端依赖升级和包体积优化，降低安全与性能风险。

### 2.2 非目标

本轮不做以下事项，避免范围失控：

- 不重构完整权限系统为 Spring Security。
- 不一次性升级 Spring Boot 大版本。
- 不重写秒杀、Kafka、Redis Stream 核心链路。
- 不新增大功能，只做必要修复、加固和清理。

## 3. 优先级总览

| 优先级 | 主题 | 影响 | 建议处理阶段 |
|---|---|---|---|
| P0 | 修复 `mvn test` 编译失败 | CI / 回归测试不可用 | 第 1 阶段 |
| P0 | `/admin/**` 被登录拦截排除 | 后台审核接口越权 | 第 2 阶段 |
| P0 | `/upload/**` 被登录拦截排除且上传校验不足 | 任意上传 / 删除风险 | 第 2 阶段 |
| P1 | 失败任务管理接口缺少权限保护 | 可人为重试/忽略失败任务 | 第 2 阶段 |
| P1 | 分页 size 缺少统一上限 | 慢查询 / 内存压力 | 第 3 阶段 |
| P1 | 前端 npm audit 安全提示 | 依赖安全风险 | 第 5 阶段 |
| P1 | 前端主包过大 | 首屏性能与加载体验 | 第 5 阶段 |
| P2 | 文档目录与项目规范不一致 | 可维护性 / 面试材料混乱 | 第 4 阶段 |
| P2 | 调试输出、TODO、默认弱配置 | 工程规范不足 | 第 3/4 阶段 |

## 4. 分阶段实施方案

## 第 0 阶段：实施前保护

### 目标

确保当前大量本地改动不会被优化过程误覆盖，明确本轮改动边界。

### 任务

1. 执行 `git status --short --branch`，确认当前改动清单。
2. 对已有未提交改动做一次说明或临时分支保护。
3. 明确本轮只修改和优化计划相关的文件。
4. 每个阶段完成后记录变更点和验证命令。

### 验收标准

- 能清楚区分“优化计划新增/修改”和“原本已存在的本地改动”。
- 不误删已有文档、前端页面或后端代码。

---

## 第 1 阶段：恢复测试可运行性

### 目标

让 Maven 测试至少通过 testCompile，恢复基本回归能力。

### 相关文件

- `src/main/java/com/hmdp/dto/Result.java`
- `src/test/java/com/hmdp/service/ReviewServiceTest.java`

### 推荐方案

优先修改测试代码，将：

```java
result.isSuccess()
```

统一改为：

```java
result.getSuccess()
```

理由：项目其他测试已经使用 `getSuccess()`，保持一致性更好。

### 备选方案

在 `Result` 中补充兼容方法：

```java
public Boolean isSuccess() {
    return success;
}
```

此方案改动小，但会让 `Boolean` 字段同时存在 `getSuccess/isSuccess` 两种风格，长期一致性略差。

### 验证命令

```bash
mvn -q -DskipTests compile
mvn -q test
```

### 验收标准

- `mvn test` 不再因 `isSuccess()` 编译失败。
- 如果测试因 MySQL/Redis/Kafka 等外部服务失败，需要记录为环境依赖问题，而不是编译问题。

---

## 第 2 阶段：接口认证与权限加固

### 目标

优先修复高风险接口裸露问题。

### 相关文件

- `src/main/java/com/hmdp/config/MvcConfig.java`
- `src/main/java/com/hmdp/controller/AdminShopApplyController.java`
- `src/main/java/com/hmdp/controller/UploadController.java`
- `src/main/java/com/hmdp/controller/VoucherOrderFailTaskController.java`
- `src/main/java/com/hmdp/service/impl/ShopApplyServiceImpl.java`

### 任务 2.1：取消高风险路径匿名访问

从登录排除列表中移除或收窄：

```java
"/admin/**"
"/upload/**"
```

建议：

- `/admin/**` 必须登录。
- `/upload/**` 至少必须登录；图片静态访问和图片上传接口要区分处理。
- 只保留真正公开的读接口，例如商户列表、商户详情、热门博客等。

### 任务 2.2：补充管理员/商家权限校验

当前项目可能还没有完整角色模型，本阶段可以采用轻量方案：

1. 新增简单权限判断工具或注解。
2. 后台审核接口至少校验当前用户是否为管理员或约定白名单用户。
3. 商家侧接口只允许当前商家操作自己的店铺数据。

如果暂时没有角色表，先在计划中明确“临时白名单方案”，后续再演进为角色权限表。

### 任务 2.3：失败任务管理接口加保护

`VoucherOrderFailTaskController` 中以下接口应限制访问：

- 查询失败任务列表
- 手动重试
- 标记忽略

建议仅管理员可访问。

### 验证命令 / 用例

```bash
mvn -q -DskipTests compile
```

接口验证：

1. 未登录访问 `/admin/shop/apply/list` 应返回 401。
2. 普通用户访问审核接口应失败。
3. 管理员用户访问审核接口应成功。
4. 未登录上传图片应返回 401。

### 验收标准

- 高风险管理接口不再匿名可访问。
- 上传接口不再匿名可访问。
- 权限不足时返回明确错误。

---

## 第 3 阶段：上传安全、分页限制与代码规范清理

### 目标

降低常见安全和稳定性风险，提升代码规范。

### 任务 3.1：上传链路安全加固

相关文件：

- `src/main/java/com/hmdp/controller/UploadController.java`
- `src/main/java/com/hmdp/utils/SystemConstants.java`
- `src/main/resources/application.yaml`

建议改造：

1. 上传根目录改为配置项，例如：

```yaml
app:
  upload:
    dir: ${UPLOAD_DIR:./uploads/imgs/}
    max-size-mb: ${UPLOAD_MAX_SIZE_MB:5}
    allowed-extensions: jpg,jpeg,png,webp
```

2. 校验文件后缀白名单。
3. 校验文件大小。
4. 校验 `MultipartFile` 是否为空。
5. 删除接口从 `GET` 改为 `DELETE`。
6. 删除前做路径规范化：最终路径必须位于上传根目录内。
7. 上传失败不要直接抛裸 `RuntimeException`，应返回 `Result.fail` 或统一异常处理。

### 任务 3.2：统一分页参数限制

建议新增工具方法，例如：

- `PageUtils.normalizeCurrent(current)`
- `PageUtils.normalizeSize(size)`

或在 `SystemConstants` 中增加：

```java
public static final int MIN_PAGE_SIZE = 1;
public static final int MAX_PAGE_SIZE = 50;
```

重点覆盖：

- `ReviewServiceImpl#getMyReviews`
- `ReviewServiceImpl#getShopReviews`
- `ReviewServiceImpl#getShopReviewList`
- `OrderServiceImpl#queryMyOrders`
- `ComboServiceImpl#listByShopId`
- `ShopSearchServiceImpl#searchByKeyword`
- `VerifyServiceImpl#queryVerifyRecords`
- `ShopApplyServiceImpl#getApplyList`

### 任务 3.3：清理调试输出与 TODO

后端：

- 移除 `FollowServiceImpl` 中的 `System.out.println`。
- 对 `CanalSyncService` 中 TODO 做二选一：补最小实现或明确标记为后续计划文档，不留裸 TODO。

前端：

- 将生产代码中的 `console.error` 改为统一错误提示或环境判断。

### 验证命令

```bash
mvn -q -DskipTests compile
npm run build
```

### 验收标准

- 上传接口具备基本安全校验。
- 分页参数不会被用户传入超大值。
- 无明显 `System.out.println` 调试输出。

---

## 第 4 阶段：文档结构和 README 统一

### 目标

让文档结构符合项目规范，便于面试材料维护和仓库展示。

### 相关路径

- `README.md`
- `docs/interview/`
- `interview/qa/`
- `docs/superpowers/plans/`
- `docs/problems/`

### 任务

1. 面试材料统一放入 `docs/interview/`。
2. 处理重复 PDF：保留 `docs/interview/materials/项目面试资料总合并版.pdf`，移除或迁移根目录 `interview/qa/`。
3. README 中面试材料路径改为 `docs/interview/`。
4. 保持 `docs/interview/README.md` 作为入口说明。
5. 对本轮修复产生的问题记录，按规范写入 `docs/problems/{date}-{description}.md`。

### 验收标准

- README 不再指向 `interview/qa/`。
- 面试资料只保留在 `docs/interview/` 规范目录下。
- 文档导航可读、路径准确。

---

## 第 5 阶段：前端依赖安全和包体积优化

### 目标

降低前端依赖安全风险，并改善构建产物体积。

### 相关路径

- `heima_qianduan/nginx-1.18.0/html/hmdp/package.json`
- `heima_qianduan/nginx-1.18.0/html/hmdp/package-lock.json`
- `heima_qianduan/nginx-1.18.0/html/hmdp/vite.config.js`
- `heima_qianduan/nginx-1.18.0/html/hmdp/src/main.js`

### 任务 5.1：依赖安全升级评估

当前 `npm audit` 提示 `vite/esbuild` 存在漏洞，直接 `npm audit fix --force` 会升级到 Vite 8，属于破坏性升级。

建议流程：

1. 单独创建依赖升级分支。
2. 先尝试升级到兼容 Vite 4 的最新补丁版本。
3. 如果 audit 仍不通过，再评估 Vite 5/6/8 的迁移成本。
4. 每次升级后执行：

```bash
npm install
npm run build
npm audit --audit-level=moderate
```

### 任务 5.2：包体积分包

在 `vite.config.js` 中增加 `manualChunks`，例如：

```js
build: {
  rollupOptions: {
    output: {
      manualChunks: {
        vue: ['vue', 'vue-router', 'pinia'],
        element: ['element-plus'],
        axios: ['axios']
      }
    }
  }
}
```

同时评估 Element Plus 按需引入，减少主包体积。

### 验收标准

- `npm run build` 通过。
- 主 chunk 明显下降或警告减少。
- `npm audit` 风险有明确处理结果：已修复，或记录不可避免原因与后续升级路线。

---

## 第 6 阶段：长期技术债规划

### 目标

将短期不宜强改的问题纳入后续路线图。

### 建议路线

1. Spring Boot 2.3 → 2.7.x：先完成 Java 8 可兼容升级。
2. MySQL Connector 5.1 → 8.x：同步修改驱动类和连接参数。
3. Jackson 2.11 → Spring Boot 管理版本或安全版本。
4. Redisson 3.13 → 较新稳定版本，并回归分布式锁链路。
5. 权限模型从拦截器白名单演进为角色权限体系。
6. CI 增加：Maven testCompile、关键单测、前端 build、npm audit。

## 5. 推荐提交拆分

建议拆成多个小提交，便于回滚和面试讲解：

1. `test: 修复评价服务测试编译问题`
2. `fix: 加固后台和上传接口访问控制`
3. `fix: 增强图片上传安全校验`
4. `refactor: 统一分页参数限制`
5. `docs: 统一面试资料目录和README导航`
6. `chore: 清理调试输出和TODO占位`
7. `perf: 优化前端构建分包`
8. `chore: 评估并升级前端安全依赖`

## 6. 总体验收清单

完成全部阶段后，至少执行：

```bash
mvn -q -DskipTests compile
mvn -q test
cd heima_qianduan/nginx-1.18.0/html/hmdp
npm run build
npm audit --audit-level=moderate
```

接口级验收：

- 未登录访问后台审核接口返回 401。
- 普通用户不能审核商户申请。
- 未登录不能上传图片。
- 上传非法后缀文件失败。
- 上传超大文件失败。
- 删除图片不能跳出上传根目录。
- 分页 size 超大时被限制到最大值。

文档验收：

- README 路径准确。
- 面试资料位于 `docs/interview/`。
- 修复过程产生的问题记录位于 `docs/problems/`。

## 7. 风险与回滚

| 风险 | 说明 | 缓解 |
|---|---|---|
| 权限加固影响现有前端访问 | 原本匿名可访问的接口会变 401 | 同步调整前端登录态和测试用例 |
| 上传目录改造影响图片访问 | 旧图片路径可能变化 | 保留兼容映射或迁移已有图片 |
| Maven 测试依赖外部服务 | MySQL/Redis/Kafka 未启动可能导致测试失败 | 区分编译失败和环境失败，必要时引入 H2/Mock |
| Vite 大版本升级破坏兼容 | 插件/API 可能变化 | 单独分支升级，逐级验证 |
| 文档迁移误删资料 | PDF/面试材料重复且有删除记录 | 删除前确认目标目录已有完整副本 |

## 8. 建议立即执行的最小闭环

如果只做一轮 1-2 小时优化，建议先完成：

1. 修复 `ReviewServiceTest` 编译失败。
2. 移除 `/admin/**`、`/upload/**` 匿名放行。
3. 上传接口增加后缀白名单和路径穿越保护。
4. 分页 size 增加统一上限。
5. 跑 `mvn -q -DskipTests compile` 和 `npm run build`。

这组改动能最快提升项目安全性和可验证性，也最适合作为一段面试可讲的“质量加固”经历。
