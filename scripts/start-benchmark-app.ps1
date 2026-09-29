param(
    [string]$BaseUrl = 'http://127.0.0.1:8081',
    [string]$ShopMode = 'local-redis',
    [string]$SeckillMode = 'redis-stream-kafka',
    [string]$DbPassword = '123456',
    [string]$RedisHost = '127.0.0.1',
    [int]$RedisPort = 6380,
    [string]$RedisPassword = '',
    [string]$KafkaBootstrapServers = '127.0.0.1:9092',
    [string]$KafkaDefaultTopic = '',
    [string]$KafkaConsumerGroup = '',
    [string]$KafkaRelayConsumerName = '',
    [int]$KafkaConsumerConcurrency = 8,
    [int]$KafkaRelayBatchSize = 100,
    [int]$KafkaRelayBlockTimeoutMs = 2000,
    [int]$KafkaLagIntervalMs = 2000,
    [switch]$SkipBuild
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$repoRoot = Split-Path -Parent $PSScriptRoot
Push-Location $repoRoot
try {
    $appClasspathFile = Join-Path $repoRoot '.tmp.classpath'

    function Write-Log([string]$Message) {
        $ts = Get-Date -Format 'yyyy-MM-dd HH:mm:ss'
        Write-Host "[$ts] $Message"
    }

    function Ensure-AppClasspathFile() {
        if (-not (Test-Path -LiteralPath $appClasspathFile)) {
            Write-Log "[build] 生成 runtime classpath -> $appClasspathFile"
            & mvn -q -DskipTests dependency:build-classpath "-Dmdep.outputFile=$appClasspathFile" '-Dmdep.pathSeparator=;' '-Dmdep.includeScope=runtime'
        }
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
            Write-Log "Stopping existing app pid=$procId"
            Stop-Process -Id $procId -Force
            Start-Sleep -Seconds 2
        }
    }

    if (-not $SkipBuild) {
        Write-Log '[build] mvn -q -DskipTests package'
        mvn -q -DskipTests package
    }

    Ensure-AppClasspathFile

    if (-not (Test-Path -LiteralPath $appClasspathFile)) {
        throw "应用 runtime classpath 文件不存在：$appClasspathFile"
    }
    if (-not (Test-Path -LiteralPath 'target\classes\com\hmdp\HmDianPingApplication.class')) {
        throw '应用主类不存在：target\classes\com\hmdp\HmDianPingApplication.class'
    }

    $env:DB_PASSWORD = $DbPassword
    $env:REDIS_HOST = $RedisHost
    $env:REDIS_PORT = [string]$RedisPort
    $env:REDIS_PASSWORD = $RedisPassword
    $env:KAFKA_BOOTSTRAP_SERVERS = $KafkaBootstrapServers
    if (-not [string]::IsNullOrWhiteSpace($KafkaDefaultTopic)) {
        $env:KAFKA_DEFAULT_TOPIC = $KafkaDefaultTopic
    }
    else {
        Remove-Item Env:KAFKA_DEFAULT_TOPIC -ErrorAction SilentlyContinue
    }
    if (-not [string]::IsNullOrWhiteSpace($KafkaConsumerGroup)) {
        $env:KAFKA_CONSUMER_GROUP = $KafkaConsumerGroup
    }
    else {
        Remove-Item Env:KAFKA_CONSUMER_GROUP -ErrorAction SilentlyContinue
    }
    if (-not [string]::IsNullOrWhiteSpace($KafkaRelayConsumerName)) {
        $env:KAFKA_RELAY_CONSUMER_NAME = $KafkaRelayConsumerName
    }
    else {
        Remove-Item Env:KAFKA_RELAY_CONSUMER_NAME -ErrorAction SilentlyContinue
    }
    $env:SHOP_CACHE_MODE = $ShopMode
    $env:SECKILL_MODE = $SeckillMode
    $env:KAFKA_CONSUMER_CONCURRENCY = [string]$KafkaConsumerConcurrency
    $env:KAFKA_RELAY_BATCH_SIZE = [string]$KafkaRelayBatchSize
    $env:KAFKA_RELAY_BLOCK_TIMEOUT_MS = [string]$KafkaRelayBlockTimeoutMs
    $env:APP_KAFKA_MONITOR_INTERVAL_MS = [string]$KafkaLagIntervalMs

    Stop-App
    Remove-Item -LiteralPath 'bench-app.log', 'bench-app.log.stdout', 'bench-app-error.log' -ErrorAction SilentlyContinue

    $originalAppJar = 'target\\hm-dianping-0.0.1-SNAPSHOT.jar.original'
    $appClasspathEntries = @('target\\classes')
    if (Test-Path -LiteralPath $originalAppJar) {
        $appClasspathEntries += $originalAppJar
    }
    $appClasspathEntries += (Get-Content -LiteralPath $appClasspathFile -Raw).Trim()
    $appClasspath = $appClasspathEntries -join ';'
    $appArgsFile = Join-Path $repoRoot '.tmp.app.args'
    @(
        '--add-opens=java.base/java.lang=ALL-UNNAMED',
        '--add-opens=java.base/java.lang.invoke=ALL-UNNAMED',
        '--add-opens=java.base/java.lang.reflect=ALL-UNNAMED',
        '--add-opens=java.base/java.util=ALL-UNNAMED',
        '-cp',
        $appClasspath,
        'com.hmdp.HmDianPingApplication',
        '--logging.file.name=bench-app.log'
    ) | Set-Content -LiteralPath $appArgsFile -Encoding ASCII

    $process = Start-Process -FilePath 'java' `
        -ArgumentList @(("@" + $appArgsFile)) `
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
            }
            catch {
                $httpReady = $false
            }
        }
        if ($httpReady -or ($portReady -and $logReady)) {
            Start-Sleep -Seconds 2
            Write-Log "App ready pid=$($process.Id) shopMode=$ShopMode seckillMode=$SeckillMode kafkaConcurrency=$KafkaConsumerConcurrency kafkaTopic=$($env:KAFKA_DEFAULT_TOPIC) kafkaGroup=$($env:KAFKA_CONSUMER_GROUP) relayConsumer=$($env:KAFKA_RELAY_CONSUMER_NAME)"
            Write-Host "APP_PID=$($process.Id)"
            exit 0
        }
        Start-Sleep -Seconds 2
    }

    throw "App start timeout. shopMode=$ShopMode seckillMode=$SeckillMode"
}
finally {
    Pop-Location
}

