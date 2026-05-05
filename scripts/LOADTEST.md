# 集成测试与压测说明

## 1. Java 集成测试（Redis 回滚）

需本地能启动完整 Spring 应用（MySQL、Redis、Kafka 等与 `application.yaml` 一致）。

```bash
mvn test -Dtest=SeckillRedisRollbackIntegrationTest
```

类：`src/test/java/com/hmdp/integration/SeckillRedisRollbackIntegrationTest.java`  
仅验证 `SeckillRedisRollbackService` + 真实 Redis，不覆盖整条 Kafka 链路。

---

## 2. HTTP 压测

秒杀接口：`POST /voucher-order/seckill/{voucherId}`，请求头需携带 **`authorization`**（登录后颁发，与现有 `秒杀抢购.jmx` 一致）。

### 单 token vs 多 token（重要）

| 方式 | 含义 |
|------|------|
| 只给一个 token | **单用户**，除第一次外大量为「重复下单」，主要测 **接口/QPS 与 Lua 挡重复**，**不是**多用户抢库存。 |
| **tokens 文件多行** | **多用户轮询**，更接近真实秒杀；需准备多个账号各自登录后复制 token 到 `scripts/tokens.txt`（可参考 `tokens.example.txt`）。 |

### 2.1 PowerShell（无需额外安装）

```powershell
# 单 token
$env:SECKILL_TOKEN = "粘贴你的 token"
powershell -ExecutionPolicy Bypass -File d:\Java\hm-dianping\scripts\loadtest-seckill.ps1 -VoucherId 10 -Workers 30 -PerWorker 40

# 多 token（每行一个 authorization）
powershell -ExecutionPolicy Bypass -File d:\Java\hm-dianping\scripts\loadtest-seckill.ps1 -TokensFile "d:\Java\hm-dianping\scripts\my-tokens.txt" -VoucherId 10 -Workers 30 -PerWorker 40
```

### 2.2 Python（需 `pip install requests`）

```bash
# 单 token
set SECKILL_TOKEN=你的token
python d:\Java\hm-dianping\scripts\loadtest_seckill.py --base http://127.0.0.1:8081 --voucher 10 --workers 20 --count 600

# 多 token（推荐）
python d:\Java\hm-dianping\scripts\loadtest_seckill.py --tokens-file d:\Java\hm-dianping\scripts\my-tokens.txt --voucher 10 --workers 32 --count 2000
```

### 2.3 k6（需单独安装 k6）

```bash
set K6_SECKILL_TOKEN=你的token
k6 run --vus 50 --duration 30s -e BASE=http://127.0.0.1:8081 -e VOUCHER_ID=10 d:\Java\hm-dianping\scripts\seckill-load.k6
```

### 2.4 Java 压测（不引入新依赖，JDK8，纯 HttpURLConnection）

类：`src/test/java/com/hmdp/loadtest/SeckillLoadBench.java`

在 IDEA 中对此类 `main` **右键 Run**，Program arguments 示例：

```text
http://127.0.0.1:8081 10 32 2000 d:/Java/hm-dianping/scripts/my-tokens.txt
```

依次为：`baseUrl` `voucherId` `线程数` `总请求数` `token文件路径`（每行一个 authorization）。请求按序号对 token **轮询**。

或用命令行（需先 `mvn test-compile` 再把 `target/test-classes` 等加入 classpath，略繁琐，IDE 运行最简单）。

### 2.5 已有 JMeter 工程

仓库内 `秒杀抢购.jmx` 含秒杀样例（含 `authorization` 示例头）；可在 JMeter GUI 中改成你的 token 与券 ID 后运行。多用户可在 JMeter 里用 CSV Data Set Config 喂多个 token。

---

## 3. 读结果时注意

- 高并发下大量 `success:false`（库存不足 / 重复下单）是预期行为，脚本里已区分 HTTP 错误与业务失败。
- 全链路（Kafka 堆积、Relay）需结合应用日志与 Kafka Consumer lag 观察。

---

## 4. 跑完把结果贴给人分析时，建议带上这些

1. **压测命令**（PowerShell / Python / k6 / JMeter 及参数）。
2. **脚本输出全文**（含 `DurationMs`、`TotalRequests`、`ApproxRps`、`success=true/false`、http 错误）。
3. **环境**：是否单机、应用与 MySQL/Redis/Kafka 是否同机。
4. **可选**：压测时段应用日志是否大量异常；Kafka consumer lag 或堆积数值。

说明：`ApproxRps` 为 **总请求数 ÷ 墙钟时间**（含所有结果），近似接口层 QPS，不是只统计 `success:true`。

**面试向汇总**：JMeter 多轮配置与主结果表述见仓库根目录 **`秒杀链路测试结果记录.md`** 的 **§4.4**。
