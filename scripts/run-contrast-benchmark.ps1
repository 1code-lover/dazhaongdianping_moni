param(
    [ValidateSet('shop', 'seckill', 'all')]
    [string]$Target = 'all',
    [string]$BaseUrl = 'http://127.0.0.1:8081',
    [string]$OutputRoot = 'benchmarks\contrast',
    [string]$DateStamp = (Get-Date -Format 'yyyyMMdd'),
    [string]$ResultFileName = 'results.jsonl',
    [string[]]$ShopModes = @('db-only', 'redis-only', 'local-redis'),
    [int[]]$ShopThreads = @(200, 500, 1000),
    [int]$ShopId = 1,
    [int]$ShopRequests = 10000,
    [int]$ShopWarmup = 500,
    [string[]]$SeckillModes = @('sync-db', 'redis-stream', 'redis-stream-kafka'),
    [int[]]$SeckillThreads = @(200, 300, 400, 500),
    [int[]]$KafkaUpperThreads = @(700, 1000),
    [bool]$IncludeKafkaUpperBound = $true,
    [int]$SeckillRequests = 10000,
    [int]$SeckillStock = 10000,
    [string]$TokensFile = 'tokens-10000.txt',
    [string]$DbPassword = '123456',
    [string]$RedisHost = '127.0.0.1',
    [int]$RedisPort = 6380,
    [string]$RedisPassword = '',
    [string]$KafkaBootstrapServers = '127.0.0.1:9092',
    [string]$MySqlPath = 'C:\Program Files\MySQL\MySQL Server 8.4\bin\mysql.exe',
    [int]$DrainPollSeconds = 2,
    [int]$DrainStableRounds = 5,
    [int]$DrainTimeoutSeconds = 300,
    [int]$Repeat = 1,
    [switch]$SkipBuild,
    [switch]$Append
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$repoRoot = Split-Path -Parent $PSScriptRoot
Push-Location $repoRoot
try {
    $jarPath = Join-Path $repoRoot 'target\hm-dianping-0.0.1-SNAPSHOT.jar'
    $classpath = 'target\test-classes;target\classes'
    $outputDir = Join-Path $repoRoot (Join-Path $OutputRoot $DateStamp)
    New-Item -ItemType Directory -Force -Path $outputDir | Out-Null
    $resultFile = Join-Path $outputDir $ResultFileName

    if (-not $Append -and (Test-Path -LiteralPath $resultFile)) {
        Remove-Item -LiteralPath $resultFile -Force
    }

    if (-not $SkipBuild) {
        Write-Host '[build] mvn -q -DskipTests package'
        mvn -q -DskipTests package
    }

    if (-not (Test-Path -LiteralPath $jarPath)) {
        throw "应用 Jar 不存在：$jarPath"
    }

    if (-not (Test-Path -LiteralPath $TokensFile) -and ($Target -in @('seckill', 'all'))) {
        throw "tokens 文件不存在：$TokensFile"
    }

    function Write-Log([string]$Message) {
        $ts = Get-Date -Format 'yyyy-MM-dd HH:mm:ss'
        Write-Host "[$ts] $Message"
    }

    function Get-PortPid([int]$Port) {
        $conn = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1
        if ($conn) {
            return [int]$conn.OwningProcess
        }
        return $null
    }

    function Stop-App() {
        $procId = Get-PortPid 8081
        if ($procId) {
            Write-Log "Stopping app pid=$procId"
            Stop-Process -Id $procId -Force
            Start-Sleep -Seconds 2
        }
    }

    function Start-App([string]$ShopMode, [string]$SeckillMode) {
        $env:DB_PASSWORD = $DbPassword
        $env:REDIS_HOST = $RedisHost
        $env:REDIS_PORT = [string]$RedisPort
        $env:REDIS_PASSWORD = $RedisPassword
        $env:KAFKA_BOOTSTRAP_SERVERS = $KafkaBootstrapServers
        $env:SHOP_CACHE_MODE = $ShopMode
        $env:SECKILL_MODE = $SeckillMode

        Stop-App
        Remove-Item -LiteralPath 'bench-app.log', 'bench-app.log.stdout', 'bench-app-error.log' -ErrorAction SilentlyContinue

        $process = Start-Process -FilePath 'java' `
            -ArgumentList @(
                '--add-opens=java.base/java.lang=ALL-UNNAMED',
                '--add-opens=java.base/java.lang.invoke=ALL-UNNAMED',
                '--add-opens=java.base/java.lang.reflect=ALL-UNNAMED',
                '--add-opens=java.base/java.util=ALL-UNNAMED',
                '-jar',
                $jarPath,
                '--logging.file.name=bench-app.log'
            ) `
            -WorkingDirectory $repoRoot `
            -RedirectStandardOutput 'bench-app.log.stdout' `
            -RedirectStandardError 'bench-app-error.log' `
            -WindowStyle Hidden `
            -PassThru

        for ($i = 0; $i -lt 120; $i++) {
            $portReady = (Get-PortPid 8081) -ne $null
            $logReady = (Test-Path 'bench-app.log') -and ((Get-Content 'bench-app.log' -Tail 80 -ErrorAction SilentlyContinue | Out-String) -match 'Started HmDianPingApplication')
            $httpReady = $false
            if ($portReady) {
                try {
                    $resp = Invoke-WebRequest -UseBasicParsing "$BaseUrl/shop/1" -TimeoutSec 2
                    $httpReady = $resp.StatusCode -eq 200
                } catch {
                    $httpReady = $false
                }
            }
            if ($httpReady -or ($portReady -and $logReady)) {
                Start-Sleep -Seconds 2
                Write-Log "App ready pid=$($process.Id) shopMode=$ShopMode seckillMode=$SeckillMode"
                return [int]$process.Id
            }
            Start-Sleep -Seconds 2
        }

        throw "App start timeout. shopMode=$ShopMode seckillMode=$SeckillMode"
    }

    function Invoke-MySqlQuery([string]$Sql) {
        $oldPwd = $env:MYSQL_PWD
        try {
            $env:MYSQL_PWD = $DbPassword
            & $MySqlPath -uroot -D hmdp -N -B -e $Sql 2>$null
        }
        finally {
            if ($null -eq $oldPwd) {
                Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue
            }
            else {
                $env:MYSQL_PWD = $oldPwd
            }
        }
    }

    function Get-Scalar([string]$Sql) {
        $raw = Invoke-MySqlQuery $Sql | Select-Object -First 1
        if ($null -eq $raw) {
            return $null
        }
        return "$raw".Trim()
    }

    function New-SeckillVoucher([int]$Stock, [string]$Label) {
        $now = Get-Date
        $body = [ordered]@{
            shopId = 1
            title = "压测券-$Label"
            subTitle = 'benchmark'
            rules = 'benchmark'
            payValue = 100
            actualValue = 1000
            type = 1
            stock = $Stock
            beginTime = $now.AddMinutes(-5).ToString('yyyy-MM-ddTHH:mm:ss')
            endTime = $now.AddDays(1).ToString('yyyy-MM-ddTHH:mm:ss')
        } | ConvertTo-Json

        $resp = Invoke-RestMethod -Method Post -Uri "$BaseUrl/voucher/seckill" -ContentType 'application/json' -Body $body
        if (-not $resp.success) {
            throw "Create voucher failed: $($resp | ConvertTo-Json -Compress)"
        }
        return [int64]$resp.data
    }

    function Parse-Metrics([string[]]$Lines) {
        $metrics = @{}
        foreach ($line in $Lines) {
            if ($line -match '^([A-Za-z0-9_~/:-]+):\s*(.+)$') {
                $metrics[$matches[1]] = $matches[2].Trim()
            }
        }
        return $metrics
    }

    function Save-Record([hashtable]$Record) {
        ($Record | ConvertTo-Json -Depth 8 -Compress) | Add-Content -LiteralPath $resultFile -Encoding UTF8
    }

    function Run-ShopBench([string]$Mode, [int]$Threads, [int]$Total, [int]$Warmup, [int]$Round) {
        $procId = Start-App -ShopMode $Mode -SeckillMode 'redis-stream-kafka'
        Write-Log "Shop bench start mode=$Mode threads=$Threads total=$Total round=$Round"
        $raw = & java -cp $classpath com.hmdp.loadtest.ShopQueryLoadBench $BaseUrl $ShopId $Threads $Total $Warmup 2>&1
        if ($LASTEXITCODE -ne 0) {
            throw "Shop bench failed: $($raw -join [Environment]::NewLine)"
        }

        $metrics = Parse-Metrics $raw
        $record = [ordered]@{
            timestamp = (Get-Date).ToString('s')
            target = 'shop'
            mode = $Mode
            threads = $Threads
            total = $Total
            warmup = $Warmup
            round = $Round
            appPid = $procId
            metrics = $metrics
            raw = $raw
        }
        Save-Record $record
        Write-Log "Shop bench done mode=$Mode threads=$Threads round=$Round rps=$($metrics['approx_rps']) p95=$($metrics['p95_ms'])"
    }

    function Run-SeckillBench([string]$Mode, [int]$Threads, [int]$Total, [int]$Stock, [string]$TokensPath, [int]$Round) {
        $procId = Start-App -ShopMode 'local-redis' -SeckillMode $Mode
        $voucherId = New-SeckillVoucher -Stock $Stock -Label "$Mode-$Threads-r$Round"
        Write-Log "Seckill bench start mode=$Mode threads=$Threads total=$Total round=$Round voucherId=$voucherId"

        $raw = & java -cp $classpath com.hmdp.loadtest.SeckillLoadBench $BaseUrl $voucherId $Threads $Total $TokensPath 2>&1
        if ($LASTEXITCODE -ne 0) {
            throw "Seckill bench failed: $($raw -join [Environment]::NewLine)"
        }

        $metrics = Parse-Metrics $raw
        $success = if ($metrics.ContainsKey('success_true~')) { [int]$metrics['success_true~'] } else { 0 }
        $benchSec = if ($metrics.ContainsKey('duration_sec')) { [double]$metrics['duration_sec'] } else { 0D }

        $drainStart = Get-Date
        $lastCount = -1
        $stableTimes = 0
        $finalOrders = 0
        while ($true) {
            Start-Sleep -Seconds $DrainPollSeconds
            $countText = Get-Scalar "select count(*) from tb_voucher_order where voucher_id = $voucherId;"
            $finalOrders = if ($countText) { [int]$countText } else { 0 }
            if ($finalOrders -eq $success) {
                break
            }
            if ($finalOrders -eq $lastCount) {
                $stableTimes++
            } else {
                $stableTimes = 0
                $lastCount = $finalOrders
            }
            if ($stableTimes -ge $DrainStableRounds) {
                break
            }
            if (((Get-Date) - $drainStart).TotalSeconds -ge $DrainTimeoutSeconds) {
                break
            }
        }

        $drainSec = [math]::Round(((Get-Date) - $drainStart).TotalSeconds, 3)
        $totalSec = $benchSec + $drainSec
        $stockLeft = Get-Scalar "select stock from tb_seckill_voucher where voucher_id = $voucherId;"
        $failTaskCount = Get-Scalar "select count(*) from tb_voucher_order_fail_task where voucher_id = $voucherId;"
        $record = [ordered]@{
            timestamp = (Get-Date).ToString('s')
            target = 'seckill'
            mode = $Mode
            threads = $Threads
            total = $Total
            stock = $Stock
            voucherId = $voucherId
            round = $Round
            appPid = $procId
            metrics = $metrics
            extra = [ordered]@{
                bench_sec = $benchSec
                drain_sec = $drainSec
                final_orders = $finalOrders
                stock_left = if ($stockLeft) { [int]$stockLeft } else { $null }
                fail_task_count = if ($failTaskCount) { [int]$failTaskCount } else { 0 }
                end_to_end_tps = if ($totalSec -gt 0) { [math]::Round($finalOrders / $totalSec, 1) } else { $null }
            }
            raw = $raw
        }
        Save-Record $record
        Write-Log "Seckill bench done mode=$Mode threads=$Threads round=$Round success=$success final=$finalOrders benchSec=$benchSec drainSec=$drainSec"
    }

    if ($Target -in @('shop', 'all')) {
        foreach ($mode in $ShopModes) {
            foreach ($threads in $ShopThreads) {
                for ($round = 1; $round -le $Repeat; $round++) {
                    Run-ShopBench -Mode $mode -Threads $threads -Total $ShopRequests -Warmup $ShopWarmup -Round $round
                }
            }
        }
    }

    if ($Target -in @('seckill', 'all')) {
        foreach ($mode in $SeckillModes) {
            $threadsToRun = @($SeckillThreads)
            if ($IncludeKafkaUpperBound -and $mode -eq 'redis-stream-kafka') {
                $threadsToRun = @($threadsToRun + $KafkaUpperThreads)
            }
            foreach ($threads in $threadsToRun) {
                for ($round = 1; $round -le $Repeat; $round++) {
                    Run-SeckillBench -Mode $mode -Threads $threads -Total $SeckillRequests -Stock $SeckillStock -TokensPath $TokensFile -Round $round
                }
            }
        }
    }

    Write-Host "RESULT_FILE=$resultFile"
}
finally {
    Pop-Location
}


