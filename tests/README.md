# HM-DianPing 自动化测试平台（PyTest + Selenium + Allure）

## 1. 这个平台测什么

- API 回归：核心接口可用性、返回结构、基本业务结果
- UI 回归：核心页面主流程（登录、查询、下单等）
- 统一报告：Allure 聚合 API/UI 的执行结果、截图、失败信息
- 稳定执行：失败自动重试（适合处理短暂网络抖动）
- 已对接本仓库前端：`heima_qianduan/nginx-1.18.0/html/hmdp`
- 自动鉴权：可自动发验证码并从 Redis 读取验证码完成登录拿 token
- 秒杀链路回归：自动发现可用秒杀券并覆盖鉴权、下单、重复下单、登出失效等关键场景
- 面试讲解文档：`tests/自动化测试平台实现方案-面试版.md`
- 面试口述讲稿：`tests/自动化测试平台面试讲稿.md`
- 面试口述讲稿（校招）：`tests/自动化测试平台面试讲稿-校招版.md`
- 面试速记卡（30s/60s/3min）：`tests/自动化测试平台面试速记卡-校招.md`

## 2. 目录说明

- `tests/api/`：接口测试
- `tests/ui/pages/`：Page Object（页面元素 + 业务动作）
- `tests/ui/cases/`：UI 用例层
- `tests/conftest.py`：fixture（浏览器、会话、参数、失败附件）
- `pytest.ini`：pytest 配置（标记、重试）

## 3. 安装依赖

```bash
pip install -r requirements-test.txt
```

## 4. 运行方式

先启动前端 nginx（默认监听 `8080`，`/api` 反代到后端）：

```bash
cd heima_qianduan/nginx-1.18.0
start nginx.exe
```

只跑 API：

```bash
pytest -m api --base-url http://127.0.0.1:8081
```

只跑 UI（需要前端可访问地址）：

```bash
pytest -m ui --ui-base-url http://127.0.0.1:8080 --headless
```

只跑秒杀链路（API + UI）：

```bash
pytest -k seckill --base-url http://127.0.0.1:8081 --ui-base-url http://127.0.0.1:8080
```

全部执行 + 生成 Allure 原始结果：

```bash
pytest --base-url http://127.0.0.1:8081 --ui-base-url http://127.0.0.1:8080 --alluredir reports/allure-results
```

一键端口检查 + 执行测试 + 产出 Allure 结果：

```bash
powershell -ExecutionPolicy Bypass -File scripts/run-tests.ps1 -Mode all
```

只跑秒杀：

```bash
powershell -ExecutionPolicy Bypass -File scripts/run-tests.ps1 -Mode seckill
```

要求 `8082` 必须可用（推荐与 nginx upstream 一致）：

```bash
powershell -ExecutionPolicy Bypass -File scripts/run-tests.ps1 -Mode all -Require8082
```

开启真实 UI 登录动作模板用例：

```bash
powershell -ExecutionPolicy Bypass -File scripts/run-tests.ps1 -Mode ui -EnableUiLoginE2E
```

如果你已经有 token，可直接覆盖自动鉴权：

```bash
pytest -m api --auth-token your_token_here
```

## 5. 查看 Allure 报告

先安装 allure 命令行（本机一次）后执行：

```bash
allure serve reports/allure-results
```

## 6. 环境变量（可选）

- `BASE_URL`：后端地址（默认 `http://127.0.0.1:8081`）
- `UI_BASE_URL`：前端地址（默认 `http://127.0.0.1:8080`）
- `AUTH_TOKEN`：手工指定 token（有值时跳过自动登录）
- `SECKILL_TOKEN`：秒杀接口测试 token
- `SECKILL_VOUCHER_ID`：秒杀券 ID
- `TEST_PHONE`：指定测试手机号（不传会自动生成）
- `REDIS_HOST`：默认 `127.0.0.1`
- `REDIS_PORT`：默认 `6379`
- `REDIS_PASSWORD`：默认 `123456`
- `REDIS_DB`：默认 `0`

## 7. 首次落地要改的地方

1. 保证测试环境后端和 Redis 可连通（自动鉴权依赖 Redis 读取验证码）。
2. 按业务优先级继续补 `Page Object`（店铺列表、店铺详情、秒杀下单）。
3. 根据你的真实业务补充断言（不仅是 `success` 字段存在）。
4. 在 CI 中将 `reports/allure-results` 设为工件，持续积累测试趋势。
5. 建议把 `8082` 节点一起启动，否则 `nginx` upstream 可能出现随机登录失败。

## 8. 当前已覆盖的高价值场景

- `shop-type` 基础可用性与数据非空校验
- `/user/me` 未登录拦截（401）
- 自动登录后 `/user/me` 鉴权校验
- 自动发现秒杀券并校验券列表可见
- 同一用户重复秒杀拦截
- 登出后 token 失效校验
- UI 未登录点击秒杀跳转登录页
- UI 已登录点击秒杀返回业务反馈（成功/重复/库存不足）
