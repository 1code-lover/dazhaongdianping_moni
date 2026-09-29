param(
    [ValidateSet("shop", "seckill", "all")]
    [string]$Target = "all",
    [string]$BaseUrl = "http://127.0.0.1:8081",
    [string]$ShopMode = "local-redis",
    [string]$SeckillMode = "redis-stream-kafka",
    [long]$ShopId = 1,
    [long]$VoucherId = 10,
    [string]$TokensFile = "tokens-10000.txt",
    [int[]]$ShopThreads = @(50, 200, 500),
    [int]$ShopRequests = 5000,
    [int]$ShopWarmup = 200,
    [int[]]$SeckillThreads = @(100, 300, 500),
    [int]$SeckillRequests = 3000,
    [int]$Repeat = 1,
    [string]$OutputDir = "benchmarks\\matrix",
    [switch]$SkipCompile
)

$ErrorActionPreference = "Stop"
$repoRoot = Split-Path -Parent $PSScriptRoot
Push-Location $repoRoot
try {
    if (-not $SkipCompile) {
        Write-Host "[build] mvn -q -DskipTests test-compile"
        mvn -q -DskipTests test-compile
    }

    New-Item -ItemType Directory -Force -Path $OutputDir | Out-Null
    $timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
    $resultFile = Join-Path $OutputDir ("benchmark-matrix-" + $timestamp + ".jsonl")

    function Invoke-BenchJava {
        param(
            [string]$MainClass,
            [string[]]$Arguments,
            [hashtable]$Meta
        )

        $classpath = "target\\test-classes;target\\classes"
        $raw = & java -cp $classpath $MainClass @Arguments 2>&1
        if ($LASTEXITCODE -ne 0) {
            throw "执行失败: $MainClass`n$($raw -join [Environment]::NewLine)"
        }

        $metrics = @{}
        foreach ($line in $raw) {
            if ($line -match '^([A-Za-z0-9_~/:-]+):\s*(.+)$') {
                $metrics[$matches[1]] = $matches[2].Trim()
            }
        }

        $record = [ordered]@{
            timestamp = (Get-Date).ToString("s")
            mainClass = $MainClass
            meta = $Meta
            metrics = $metrics
            raw = $raw
        }
        ($record | ConvertTo-Json -Depth 6 -Compress) | Add-Content -Path $resultFile -Encoding utf8
        return $record
    }

    if ($Target -in @("shop", "all")) {
        foreach ($threads in $ShopThreads) {
            for ($round = 1; $round -le $Repeat; $round++) {
                Write-Host "[shop] mode=$ShopMode threads=$threads total=$ShopRequests warmup=$ShopWarmup round=$round"
                Invoke-BenchJava `
                    -MainClass "com.hmdp.loadtest.ShopQueryLoadBench" `
                    -Arguments @($BaseUrl, "$ShopId", "$threads", "$ShopRequests", "$ShopWarmup") `
                    -Meta @{ target = "shop"; shopMode = $ShopMode; shopId = $ShopId; threads = $threads; total = $ShopRequests; warmup = $ShopWarmup; round = $round } | Out-Null
            }
        }
    }

    if ($Target -in @("seckill", "all")) {
        if (-not (Test-Path -LiteralPath $TokensFile)) {
            throw "tokens 文件不存在: $TokensFile"
        }
        foreach ($threads in $SeckillThreads) {
            for ($round = 1; $round -le $Repeat; $round++) {
                Write-Host "[seckill] mode=$SeckillMode threads=$threads total=$SeckillRequests round=$round"
                Invoke-BenchJava `
                    -MainClass "com.hmdp.loadtest.SeckillLoadBench" `
                    -Arguments @($BaseUrl, "$VoucherId", "$threads", "$SeckillRequests", $TokensFile) `
                    -Meta @{ target = "seckill"; seckillMode = $SeckillMode; voucherId = $VoucherId; threads = $threads; total = $SeckillRequests; round = $round } | Out-Null
            }
        }
    }

    Write-Host "结果已写入: $resultFile"
    Write-Host "注意：请确保应用已按目标模式启动，例如："
    Write-Host "  SHOP_CACHE_MODE=$ShopMode"
    Write-Host "  SECKILL_MODE=$SeckillMode"
}
finally {
    Pop-Location
}

