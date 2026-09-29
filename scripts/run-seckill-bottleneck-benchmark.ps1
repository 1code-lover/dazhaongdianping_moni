param(
    [string]$BaseUrl = 'http://127.0.0.1:8081',
    [string]$OutputRoot = 'benchmarks\p3-bottleneck',
    [string]$DateStamp = (Get-Date -Format 'yyyyMMdd'),
    [string]$ResultFileName = 'results.jsonl',
    [int[]]$Threads = @(1500, 2000),
    [int]$Repeat = 3,
    [int]$Requests = 10000,
    [int]$Stock = 10000,
    [string]$TokensFile = 'tokens-10000.txt',
    [string]$DbPassword = '123456',
    [string]$RedisHost = '127.0.0.1',
    [int]$RedisPort = 6380,
    [string]$RedisPassword = '',
    [string]$KafkaBootstrapServers = '127.0.0.1:9092',
    [string]$KafkaDefaultTopic = '',
    [string]$KafkaConsumerGroup = '',
    [string]$KafkaRelayConsumerName = '',
    [string]$MySqlPath = 'C:\Program Files\MySQL\MySQL Server 8.4\bin\mysql.exe',
    [string]$ShopMode = 'local-redis',
    [string]$SeckillMode = 'redis-stream-kafka',
    [int]$KafkaConsumerConcurrency = 8,
    [int]$KafkaRelayBatchSize = 100,
    [int]$KafkaRelayBlockTimeoutMs = 2000,
    [int]$KafkaLagIntervalMs = 2000,
    [int]$SampleIntervalMs = 1000,
    [int]$DrainPollSeconds = 2,
    [int]$DrainStableRounds = 5,
    [int]$DrainTimeoutSeconds = 300,
    [switch]$RequireKafkaLagZero,
    [int]$LagZeroTimeoutSeconds = 180,
    [int]$LagZeroStableRounds = 2,
    [switch]$SkipBuild,
    [switch]$SkipAppLifecycle,
    [switch]$Append,
    [switch]$UseBenchProbeApi
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$repoRoot = Split-Path -Parent $PSScriptRoot
Push-Location $repoRoot
try {
    $appClasspathFile = Join-Path $repoRoot '.tmp.classpath'
    $classpathFile = Join-Path $repoRoot '.tmp.classpath.compile'
    $classpath = 'target\test-classes;target\classes'
    $outputDir = Join-Path $repoRoot (Join-Path $OutputRoot $DateStamp)
    New-Item -ItemType Directory -Force -Path $outputDir | Out-Null
    $resultFile = Join-Path $outputDir $ResultFileName

    if (-not $Append -and (Test-Path -LiteralPath $resultFile)) {
        Remove-Item -LiteralPath $resultFile -Force
    }

    function Write-Log([string]$Message) {
        $ts = Get-Date -Format 'yyyy-MM-dd HH:mm:ss'
        Write-Host "[$ts] $Message"
    }

    function Ensure-ClasspathFile([string]$OutputFile, [string]$IncludeScope) {
        $args = @(
            '-q',
            '-DskipTests',
            'dependency:build-classpath',
            "-Dmdep.outputFile=$OutputFile",
            '-Dmdep.pathSeparator=;'
        )
        if ($IncludeScope) {
            $args += "-Dmdep.includeScope=$IncludeScope"
        }
        & mvn @args
    }

    function Ensure-ClasspathArtifacts() {
        if (-not (Test-Path -LiteralPath $appClasspathFile)) {
            Write-Log "[build] 生成 runtime classpath -> $appClasspathFile"
            Ensure-ClasspathFile -OutputFile $appClasspathFile -IncludeScope 'runtime'
        }
        if (-not (Test-Path -LiteralPath $classpathFile)) {
            Write-Log "[build] 生成 test classpath -> $classpathFile"
            Ensure-ClasspathFile -OutputFile $classpathFile -IncludeScope 'test'
        }
    }

    function ConvertTo-PlainJsonValue($Value) {
        if ($null -eq $Value) {
            return $null
        }
        if ($Value -is [string] -or $Value -is [char] -or $Value -is [bool] -or
            $Value -is [byte] -or $Value -is [sbyte] -or $Value -is [int16] -or $Value -is [uint16] -or
            $Value -is [int32] -or $Value -is [uint32] -or $Value -is [int64] -or $Value -is [uint64] -or
            $Value -is [single] -or $Value -is [double] -or $Value -is [decimal] -or
            $Value -is [datetime] -or $Value -is [datetimeoffset] -or $Value -is [timespan] -or
            $Value -is [guid]) {
            return $Value
        }
        if ($Value -is [System.Collections.IDictionary]) {
            $map = [ordered]@{}
            foreach ($key in $Value.Keys) {
                $map["$key"] = ConvertTo-PlainJsonValue $Value[$key]
            }
            return $map
        }
        if (($Value -is [System.Collections.IEnumerable]) -and -not ($Value -is [string])) {
            $items = New-Object System.Collections.Generic.List[object]
            foreach ($item in $Value) {
                $items.Add((ConvertTo-PlainJsonValue $item))
            }
            return $items.ToArray()
        }
        $properties = @($Value.PSObject.Properties | Where-Object { $_.MemberType -match 'Property$' })
        if ($properties.Count -gt 0) {
            $map = [ordered]@{}
            foreach ($property in $properties) {
                $map[$property.Name] = ConvertTo-PlainJsonValue $property.Value
            }
            return $map
        }
        return "$Value"
    }

    function Save-JsonLine([string]$Path, $Record) {
        $parent = Split-Path -Parent $Path
        if ($parent -and -not (Test-Path -LiteralPath $parent)) {
            New-Item -ItemType Directory -Force -Path $parent | Out-Null
        }
        $targetPath = if ([System.IO.Path]::IsPathRooted($Path)) { $Path } else { Join-Path (Get-Location).Path $Path }
        $plain = ConvertTo-PlainJsonValue $Record
        $json = $plain | ConvertTo-Json -Depth 12 -Compress
        [System.IO.File]::AppendAllText($targetPath, $json + [Environment]::NewLine, [System.Text.UTF8Encoding]::new($false))
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

    function Start-App() {
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

    function Invoke-MySqlRaw([string]$Sql, [string]$OutputPath) {
        $oldPwd = $env:MYSQL_PWD
        try {
            $env:MYSQL_PWD = $DbPassword
            & $MySqlPath -uroot -D hmdp -e $Sql *> $OutputPath
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

    function Get-TabSeparatedColumns([string]$Row, [int]$ExpectedColumns) {
        if ([string]::IsNullOrWhiteSpace($Row)) {
            return @()
        }
        $parts = @("$Row" -split "`t")
        if ($parts.Count -lt $ExpectedColumns) {
            return @()
        }
        return $parts
    }

    function Get-MySqlTransactionSummary() {
        $row = Invoke-MySqlQuery @"
SELECT COALESCE(COUNT_STAR, 0),
       COALESCE(ROUND(SUM_TIMER_WAIT / 1000000000000, 6), 0),
       COALESCE(ROUND(AVG_TIMER_WAIT / 1000000000, 6), 0),
       COALESCE(ROUND(MAX_TIMER_WAIT / 1000000000, 6), 0)
FROM performance_schema.events_transactions_summary_global_by_event_name
WHERE EVENT_NAME = 'transaction';
"@ | Select-Object -First 1

        $parts = Get-TabSeparatedColumns -Row $row -ExpectedColumns 4
        if ($parts.Count -lt 4) {
            return [ordered]@{
                count = 0
                total_sec = 0
                avg_ms = 0
                max_ms = 0
            }
        }

        return [ordered]@{
            count = [double]$parts[0]
            total_sec = [double]$parts[1]
            avg_ms = [double]$parts[2]
            max_ms = [double]$parts[3]
        }
    }

    function Get-MySqlActiveTransactionSummary() {
        $row = Invoke-MySqlQuery @"
SELECT COUNT(*),
       COALESCE(ROUND(MAX(TIMESTAMPDIFF(MICROSECOND, trx_started, NOW(6))) / 1000000, 6), 0),
       COALESCE(SUM(CASE WHEN trx_state = 'LOCK WAIT' THEN 1 ELSE 0 END), 0)
FROM information_schema.innodb_trx;
"@ | Select-Object -First 1

        $parts = Get-TabSeparatedColumns -Row $row -ExpectedColumns 3
        if ($parts.Count -lt 3) {
            return [ordered]@{
                count = 0
                max_age_sec = 0
                lock_wait_count = 0
            }
        }

        return [ordered]@{
            count = [double]$parts[0]
            max_age_sec = [double]$parts[1]
            lock_wait_count = [double]$parts[2]
        }
    }

    function Get-GlobalStatus([string[]]$Names) {
        if ($Names.Count -eq 0) {
            return @{}
        }
        $escapedNames = $Names | ForEach-Object { "'$_'" }
        $sql = "SHOW GLOBAL STATUS WHERE Variable_name IN (" + ($escapedNames -join ',') + ");"
        $rows = Invoke-MySqlQuery $sql
        $map = @{}
        foreach ($row in $rows) {
            $parts = "$row" -split "`t"
            if ($parts.Length -ge 2) {
                $map[$parts[0]] = $parts[1]
            }
        }
        return $map
    }

    function Get-ActuatorMetric([string]$MetricName, [string[]]$Tags = @()) {
        $url = "$BaseUrl/actuator/metrics/$MetricName"
        if ($Tags.Count -gt 0) {
            $pairs = @()
            foreach ($tag in $Tags) {
                $pairs += 'tag=' + [uri]::EscapeDataString($tag)
            }
            $url += '?' + ($pairs -join '&')
        }
        try {
            return Invoke-RestMethod -Method Get -Uri $url -TimeoutSec 5
        }
        catch {
            return $null
        }
    }

    function Get-ActuatorMeasurementValue([string]$MetricName, [string]$Statistic = 'VALUE', [string[]]$Tags = @()) {
        $metric = Get-ActuatorMetric -MetricName $MetricName -Tags $Tags
        if ($null -eq $metric -or $null -eq $metric.measurements) {
            return $null
        }
        $measurement = $metric.measurements | Where-Object { $_.statistic -eq $Statistic } | Select-Object -First 1
        if ($null -eq $measurement) {
            return $null
        }
        return $measurement.value
    }

    function Invoke-BenchProbe([string]$Path) {
        $url = "$BaseUrl$Path"
        $resp = Invoke-RestMethod -Method Get -Uri $url -TimeoutSec 10
        if ($null -eq $resp -or -not $resp.success) {
            throw "Bench probe request failed: path=$Path resp=$($resp | ConvertTo-Json -Compress)"
        }
        return $resp.data
    }

    function Convert-ToNullableDouble($Value) {
        if ($null -eq $Value -or [string]::IsNullOrWhiteSpace("$Value")) {
            return $null
        }
        return [double]$Value
    }

    function Convert-BenchProbeMySqlSnapshot($Snapshot) {
        if ($null -eq $Snapshot) {
            return [ordered]@{}
        }
        return [ordered]@{
            threads_running = Convert-ToNullableDouble $Snapshot.threadsRunning
            threads_connected = Convert-ToNullableDouble $Snapshot.threadsConnected
            innodb_row_lock_current_waits = Convert-ToNullableDouble $Snapshot.innodbRowLockCurrentWaits
            innodb_row_lock_waits = Convert-ToNullableDouble $Snapshot.innodbRowLockWaits
            innodb_row_lock_time = Convert-ToNullableDouble $Snapshot.innodbRowLockTime
            innodb_row_lock_time_avg = Convert-ToNullableDouble $Snapshot.innodbRowLockTimeAvg
            queries = Convert-ToNullableDouble $Snapshot.queries
            innodb_trx_count = Convert-ToNullableDouble $Snapshot.innodbTrxCount
            innodb_trx_max_age_sec = Convert-ToNullableDouble $Snapshot.innodbTrxMaxAgeSec
            innodb_trx_lock_wait_count = Convert-ToNullableDouble $Snapshot.innodbTrxLockWaitCount
            data_lock_waits = if ($null -ne $Snapshot.dataLockWaits) { [double]$Snapshot.dataLockWaits } else { 0 }
            ps_tx_count = Convert-ToNullableDouble $Snapshot.psTxCount
            ps_tx_total_sec = Convert-ToNullableDouble $Snapshot.psTxTotalSec
            ps_tx_avg_ms = Convert-ToNullableDouble $Snapshot.psTxAvgMs
            ps_tx_max_ms = Convert-ToNullableDouble $Snapshot.psTxMaxMs
        }
    }

    function Get-BenchProbeSeckillSummary([long]$VoucherId) {
        return Invoke-BenchProbe -Path ("/bench/seckill/summary?voucherId=" + $VoucherId)
    }

    function Get-BenchProbeTopDigestSummary() {
        $data = Invoke-BenchProbe -Path '/bench/mysql/top-digest'
        if ($null -eq $data) {
            return $null
        }
        $topDigest = $data.topDigest
        if ($null -eq $topDigest) {
            $digests = @($data.topDigests)
            if ($digests.Count -gt 0) {
                $topDigest = $digests[0]
            }
        }
        if ($null -eq $topDigest) {
            return $null
        }
        return [ordered]@{
            digest_text = $topDigest.digestText
            count_star = Convert-ToNullableDouble $topDigest.countStar
            total_sec = Convert-ToNullableDouble $topDigest.totalSec
            avg_ms = Convert-ToNullableDouble $topDigest.avgMs
            max_ms = Convert-ToNullableDouble $topDigest.maxMs
        }
    }

    function Get-BenchProbeKafkaLagSnapshot() {
        return Invoke-BenchProbe -Path '/bench/kafka/lag'
    }

    function Get-KafkaLagObservation() {
        if ($UseBenchProbeApi) {
            $snapshot = Get-BenchProbeKafkaLagSnapshot
            return [ordered]@{
                source = 'bench-probe'
                enabled = [bool]$snapshot.enabled
                topic = $snapshot.topic
                group_id = $snapshot.groupId
                partition_count = if ($null -ne $snapshot.partitionCount) { [int]$snapshot.partitionCount } else { 0 }
                partitions_with_commit = if ($null -ne $snapshot.partitionsWithCommit) { [int]$snapshot.partitionsWithCommit } else { 0 }
                total_lag = if ($null -ne $snapshot.totalLag) { [long]$snapshot.totalLag } else { 0 }
                topic_missing = [bool]$snapshot.topicMissing
                has_committed_offsets = [bool]$snapshot.hasCommittedOffsets
                captured_at_epoch_ms = if ($null -ne $snapshot.capturedAtEpochMs) { [int64]$snapshot.capturedAtEpochMs } else { $null }
            }
        }

        return [ordered]@{
            source = 'actuator'
            enabled = $true
            topic = $null
            group_id = $null
            partition_count = Convert-ToNullableDouble (Get-ActuatorMeasurementValue 'benchmark.kafka.lag.partition.count')
            partitions_with_commit = Convert-ToNullableDouble (Get-ActuatorMeasurementValue 'benchmark.kafka.lag.partitions.with.commit')
            total_lag = Convert-ToNullableDouble (Get-ActuatorMeasurementValue 'benchmark.kafka.lag.total')
            topic_missing = $false
            has_committed_offsets = $true
            captured_at_epoch_ms = Convert-ToNullableDouble (Get-ActuatorMeasurementValue 'benchmark.kafka.lag.last.updated.epoch.ms')
        }
    }

    function Wait-ForKafkaLagZero([string]$Reason, [int]$TimeoutSeconds) {
        if (-not $RequireKafkaLagZero) {
            return [ordered]@{
                enabled = $false
                reason = $Reason
                success = $true
                polls = 0
                stable_rounds = 0
                elapsed_sec = 0
                lag = $null
                observation = $null
            }
        }

        $stable = 0
        $polls = 0
        $start = Get-Date
        $lastObservation = $null
        while ($true) {
            $polls++
            $lastObservation = Get-KafkaLagObservation
            $lagValue = if ($null -ne $lastObservation.total_lag) { [double]$lastObservation.total_lag } else { $null }
            if (($null -ne $lagValue) -and ($lagValue -le 0)) {
                $stable++
            }
            else {
                $stable = 0
            }
            if ($stable -ge $LagZeroStableRounds) {
                break
            }
            if (((Get-Date) - $start).TotalSeconds -ge $TimeoutSeconds) {
                break
            }
            Start-Sleep -Seconds 1
        }

        $elapsedSec = [math]::Round(((Get-Date) - $start).TotalSeconds, 3)
        $success = $false
        if ($null -ne $lastObservation -and $null -ne $lastObservation.total_lag) {
            $success = ([double]$lastObservation.total_lag -le 0) -and ($stable -ge $LagZeroStableRounds)
        }

        return [ordered]@{
            enabled = $true
            reason = $Reason
            success = $success
            polls = $polls
            stable_rounds = $stable
            elapsed_sec = $elapsedSec
            lag = if ($null -ne $lastObservation) { $lastObservation.total_lag } else { $null }
            observation = $lastObservation
        }
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

    function New-SeckillVoucher([int]$CurrentStock, [string]$Label) {
        $now = Get-Date
        $body = [ordered]@{
            shopId = 1
            title = "压测券-$Label"
            subTitle = 'benchmark'
            rules = 'benchmark'
            payValue = 100
            actualValue = 1000
            type = 1
            stock = $CurrentStock
            beginTime = $now.AddMinutes(-5).ToString('yyyy-MM-ddTHH:mm:ss')
            endTime = $now.AddDays(1).ToString('yyyy-MM-ddTHH:mm:ss')
        } | ConvertTo-Json

        $resp = Invoke-RestMethod -Method Post -Uri "$BaseUrl/voucher/seckill" -ContentType 'application/json' -Body $body
        if (-not $resp.success) {
            throw "Create voucher failed: $($resp | ConvertTo-Json -Compress)"
        }
        return [int64]$resp.data
    }

    function Get-Sample([double]$ElapsedSec) {
        if ($UseBenchProbeApi) {
            $mysqlSample = Convert-BenchProbeMySqlSnapshot (Invoke-BenchProbe -Path '/bench/mysql/snapshot')
        }
        else {
            $status = Get-GlobalStatus @(
                'Threads_running',
                'Threads_connected',
                'Innodb_row_lock_current_waits',
                'Innodb_row_lock_waits',
                'Innodb_row_lock_time',
                'Innodb_row_lock_time_avg',
                'Queries'
            )
            $activeTrx = Get-MySqlActiveTransactionSummary
            $txSummary = Get-MySqlTransactionSummary
            $dataLockWaits = Get-Scalar 'SELECT COUNT(*) FROM performance_schema.data_lock_waits;'
            $mysqlSample = [ordered]@{
                threads_running = if ($status.ContainsKey('Threads_running')) { [double]$status['Threads_running'] } else { $null }
                threads_connected = if ($status.ContainsKey('Threads_connected')) { [double]$status['Threads_connected'] } else { $null }
                innodb_row_lock_current_waits = if ($status.ContainsKey('Innodb_row_lock_current_waits')) { [double]$status['Innodb_row_lock_current_waits'] } else { $null }
                innodb_row_lock_waits = if ($status.ContainsKey('Innodb_row_lock_waits')) { [double]$status['Innodb_row_lock_waits'] } else { $null }
                innodb_row_lock_time = if ($status.ContainsKey('Innodb_row_lock_time')) { [double]$status['Innodb_row_lock_time'] } else { $null }
                innodb_row_lock_time_avg = if ($status.ContainsKey('Innodb_row_lock_time_avg')) { [double]$status['Innodb_row_lock_time_avg'] } else { $null }
                queries = if ($status.ContainsKey('Queries')) { [double]$status['Queries'] } else { $null }
                innodb_trx_count = $activeTrx.count
                innodb_trx_max_age_sec = $activeTrx.max_age_sec
                innodb_trx_lock_wait_count = $activeTrx.lock_wait_count
                data_lock_waits = if ($dataLockWaits) { [double]$dataLockWaits } else { 0 }
                ps_tx_count = $txSummary.count
                ps_tx_total_sec = $txSummary.total_sec
                ps_tx_avg_ms = $txSummary.avg_ms
                ps_tx_max_ms = $txSummary.max_ms
            }
        }

        return [ordered]@{
            timestamp = (Get-Date).ToString('s')
            elapsed_sec = [math]::Round($ElapsedSec, 3)
            actuator = [ordered]@{
                tomcat_threads_current = Get-ActuatorMeasurementValue 'tomcat.threads.current'
                tomcat_threads_busy = Get-ActuatorMeasurementValue 'tomcat.threads.busy'
                tomcat_threads_config_max = Get-ActuatorMeasurementValue 'tomcat.threads.config.max'
                hikaricp_active = Get-ActuatorMeasurementValue 'hikaricp.connections.active'
                hikaricp_idle = Get-ActuatorMeasurementValue 'hikaricp.connections.idle'
                hikaricp_pending = Get-ActuatorMeasurementValue 'hikaricp.connections.pending'
                hikaricp_max = Get-ActuatorMeasurementValue 'hikaricp.connections.max'
                hikaricp_min = Get-ActuatorMeasurementValue 'hikaricp.connections.min'
                hikaricp_acquire_count = Get-ActuatorMeasurementValue 'hikaricp.connections.acquire' 'COUNT'
                hikaricp_acquire_total_time = Get-ActuatorMeasurementValue 'hikaricp.connections.acquire' 'TOTAL_TIME'
                hikaricp_acquire_max = Get-ActuatorMeasurementValue 'hikaricp.connections.acquire' 'MAX'
                jvm_threads_live = Get-ActuatorMeasurementValue 'jvm.threads.live'
                process_cpu_usage = Get-ActuatorMeasurementValue 'process.cpu.usage'
                system_cpu_usage = Get-ActuatorMeasurementValue 'system.cpu.usage'
                jvm_heap_used = Get-ActuatorMeasurementValue 'jvm.memory.used' 'VALUE' @('area:heap')
                benchmark_kafka_lag_total = Get-ActuatorMeasurementValue 'benchmark.kafka.lag.total'
                benchmark_kafka_lag_partitions_with_commit = Get-ActuatorMeasurementValue 'benchmark.kafka.lag.partitions.with.commit'
                benchmark_kafka_lag_partition_count = Get-ActuatorMeasurementValue 'benchmark.kafka.lag.partition.count'
                benchmark_kafka_lag_last_updated_epoch_ms = Get-ActuatorMeasurementValue 'benchmark.kafka.lag.last.updated.epoch.ms'
                benchmark_relay_batches_total = Get-ActuatorMeasurementValue 'benchmark.relay.batches.total'
                benchmark_relay_pending_replay_batches_total = Get-ActuatorMeasurementValue 'benchmark.relay.pending.replay.batches.total'
                benchmark_relay_records_total = Get-ActuatorMeasurementValue 'benchmark.relay.records.total'
                benchmark_relay_records_sent_total = Get-ActuatorMeasurementValue 'benchmark.relay.records.sent.total'
                benchmark_relay_records_acked_total = Get-ActuatorMeasurementValue 'benchmark.relay.records.acked.total'
                benchmark_relay_records_failed_total = Get-ActuatorMeasurementValue 'benchmark.relay.records.failed.total'
                benchmark_relay_records_dropped_total = Get-ActuatorMeasurementValue 'benchmark.relay.records.dropped.total'
                benchmark_relay_batch_last_size = Get-ActuatorMeasurementValue 'benchmark.relay.batch.last.size'
                benchmark_relay_last_batch_cost_ms = Get-ActuatorMeasurementValue 'benchmark.relay.batch.last.cost.ms'
                benchmark_relay_max_batch_cost_ms = Get-ActuatorMeasurementValue 'benchmark.relay.batch.max.cost.ms'
                benchmark_relay_last_updated_epoch_ms = Get-ActuatorMeasurementValue 'benchmark.relay.last.updated.epoch.ms'
                benchmark_consumer_messages_total = Get-ActuatorMeasurementValue 'benchmark.consumer.messages.total'
                benchmark_consumer_success_total = Get-ActuatorMeasurementValue 'benchmark.consumer.success.total'
                benchmark_consumer_retryable_failure_total = Get-ActuatorMeasurementValue 'benchmark.consumer.retryable.failure.total'
                benchmark_consumer_non_recoverable_total = Get-ActuatorMeasurementValue 'benchmark.consumer.non.recoverable.total'
                benchmark_consumer_deserialize_failure_total = Get-ActuatorMeasurementValue 'benchmark.consumer.deserialize.failure.total'
                benchmark_consumer_last_cost_ms = Get-ActuatorMeasurementValue 'benchmark.consumer.last.cost.ms'
                benchmark_consumer_max_cost_ms = Get-ActuatorMeasurementValue 'benchmark.consumer.max.cost.ms'
                benchmark_order_attempts_total = Get-ActuatorMeasurementValue 'benchmark.order.attempts.total'
                benchmark_order_success_total = Get-ActuatorMeasurementValue 'benchmark.order.success.total'
                benchmark_order_duplicate_total = Get-ActuatorMeasurementValue 'benchmark.order.duplicate.total'
                benchmark_order_lock_failed_total = Get-ActuatorMeasurementValue 'benchmark.order.lock.failed.total'
                benchmark_order_no_stock_total = Get-ActuatorMeasurementValue 'benchmark.order.no.stock.total'
                benchmark_order_unexpected_failure_total = Get-ActuatorMeasurementValue 'benchmark.order.unexpected.failure.total'
                benchmark_order_total_last_cost_ms = Get-ActuatorMeasurementValue 'benchmark.order.total.last.cost.ms'
                benchmark_order_total_max_cost_ms = Get-ActuatorMeasurementValue 'benchmark.order.total.max.cost.ms'
                benchmark_order_save_last_cost_ms = Get-ActuatorMeasurementValue 'benchmark.order.save.last.cost.ms'
                benchmark_order_save_max_cost_ms = Get-ActuatorMeasurementValue 'benchmark.order.save.max.cost.ms'
                benchmark_order_stock_last_cost_ms = Get-ActuatorMeasurementValue 'benchmark.order.stock.last.cost.ms'
                benchmark_order_stock_max_cost_ms = Get-ActuatorMeasurementValue 'benchmark.order.stock.max.cost.ms'
            }
            mysql = $mysqlSample
        }
    }

    function Get-NumericValue($Value) {
        if ($null -eq $Value) {
            return $null
        }
        try {
            return [double]$Value
        }
        catch {
            return $null
        }
    }

    function Update-PeakValue([hashtable]$Peaks, [string]$Name, $Value) {
        $numeric = Get-NumericValue $Value
        if ($null -eq $numeric) {
            return
        }
        if (-not $Peaks.ContainsKey($Name) -or $numeric -gt [double]$Peaks[$Name]) {
            $Peaks[$Name] = $numeric
        }
    }

    function Update-Peaks([hashtable]$Peaks, [hashtable]$Sample) {
        foreach ($entry in $Sample.actuator.GetEnumerator()) {
            Update-PeakValue -Peaks $Peaks -Name ("actuator." + $entry.Key) -Value $entry.Value
        }
        foreach ($entry in $Sample.mysql.GetEnumerator()) {
            Update-PeakValue -Peaks $Peaks -Name ("mysql." + $entry.Key) -Value $entry.Value
        }
    }

    function Get-NumericDelta($Before, $After) {
        $beforeValue = Get-NumericValue $Before
        $afterValue = Get-NumericValue $After
        if (($null -eq $beforeValue) -or ($null -eq $afterValue)) {
            return $null
        }
        return [math]::Round(($afterValue - $beforeValue), 6)
    }

    function Get-MySqlDelta([hashtable]$PreMySql, [hashtable]$PostMySql) {
        $txCountDelta = Get-NumericDelta $PreMySql.ps_tx_count $PostMySql.ps_tx_count
        $txTotalSecDelta = Get-NumericDelta $PreMySql.ps_tx_total_sec $PostMySql.ps_tx_total_sec

        return [ordered]@{
            innodb_row_lock_waits_delta = Get-NumericDelta $PreMySql.innodb_row_lock_waits $PostMySql.innodb_row_lock_waits
            innodb_row_lock_time_delta_ms = Get-NumericDelta $PreMySql.innodb_row_lock_time $PostMySql.innodb_row_lock_time
            queries_delta = Get-NumericDelta $PreMySql.queries $PostMySql.queries
            ps_tx_count_delta = $txCountDelta
            ps_tx_total_sec_delta = $txTotalSecDelta
            ps_tx_avg_ms_delta = if (($null -ne $txCountDelta) -and ($null -ne $txTotalSecDelta) -and ($txCountDelta -gt 0)) {
                [math]::Round(($txTotalSecDelta * 1000) / $txCountDelta, 6)
            }
            else {
                $null
            }
        }
    }

    function Get-TopDigestSummary([string]$DigestFile) {
        if (-not (Test-Path -LiteralPath $DigestFile)) {
            return $null
        }
        $rows = Get-Content -LiteralPath $DigestFile | Where-Object { -not [string]::IsNullOrWhiteSpace($_) }
        if ($rows.Count -lt 2) {
            return $null
        }
        $parts = Get-TabSeparatedColumns -Row $rows[1] -ExpectedColumns 5
        if ($parts.Count -lt 5) {
            return $null
        }
        return [ordered]@{
            digest_text = $parts[0]
            count_star = [double]$parts[1]
            total_sec = [double]$parts[2]
            avg_ms = [double]$parts[3]
            max_ms = [double]$parts[4]
        }
    }

    function Capture-EngineArtifacts([string]$Prefix) {
        $processlistFile = Join-Path $outputDir ($Prefix + '-mysql-processlist.txt')
        $innodbStatusFile = Join-Path $outputDir ($Prefix + '-mysql-innodb-status.txt')
        $digestFile = Join-Path $outputDir ($Prefix + '-mysql-digest.txt')

        Invoke-MySqlRaw 'SHOW FULL PROCESSLIST;' $processlistFile
        Invoke-MySqlRaw 'SHOW ENGINE INNODB STATUS\G' $innodbStatusFile
        Invoke-MySqlRaw @"
SELECT DIGEST_TEXT,
       COUNT_STAR,
       ROUND(SUM_TIMER_WAIT / 1000000000000, 3) AS total_sec,
       ROUND(AVG_TIMER_WAIT / 1000000000, 3) AS avg_ms,
       ROUND(MAX_TIMER_WAIT / 1000000000, 3) AS max_ms
FROM performance_schema.events_statements_summary_by_digest
WHERE SCHEMA_NAME = 'hmdp'
ORDER BY SUM_TIMER_WAIT DESC
LIMIT 10;
"@ $digestFile

        return [ordered]@{
            processlist = $processlistFile
            innodb_status = $innodbStatusFile
            digest = $digestFile
        }
    }

    if (-not $SkipBuild) {
        Write-Log '[build] mvn -q -DskipTests test-compile package'
        mvn -q -DskipTests test-compile package
    }

    Ensure-ClasspathArtifacts

    if (-not (Test-Path -LiteralPath $appClasspathFile)) {
        throw "应用 runtime classpath 文件不存在：$appClasspathFile"
    }
    if (-not (Test-Path -LiteralPath $classpathFile)) {
        throw "测试 compile classpath 文件不存在：$classpathFile"
    }
    if (-not (Test-Path -LiteralPath 'target\classes\com\hmdp\HmDianPingApplication.class')) {
        throw '应用主类不存在：target\classes\com\hmdp\HmDianPingApplication.class'
    }
    if (-not (Test-Path -LiteralPath $TokensFile)) {
        throw "tokens 文件不存在：$TokensFile"
    }

    foreach ($threadCount in $Threads) {
        for ($round = 1; $round -le $Repeat; $round++) {
            $appPid = $null
            if (-not $SkipAppLifecycle) {
                $appPid = Start-App
            }

            $label = "t$threadCount-r$round"
            $sampleFile = Join-Path $outputDir ("samples-" + $label + '.jsonl')
            if (Test-Path -LiteralPath $sampleFile) {
                Remove-Item -LiteralPath $sampleFile -Force
            }
            $stdoutFile = Join-Path $outputDir ("stdout-" + $label + '.log')
            $stderrFile = Join-Path $outputDir ("stderr-" + $label + '.log')
            Remove-Item -LiteralPath $stdoutFile, $stderrFile -ErrorAction SilentlyContinue

            $preflightLag = Wait-ForKafkaLagZero -Reason ("before-" + $label) -TimeoutSeconds $LagZeroTimeoutSeconds
            if ($RequireKafkaLagZero -and -not $preflightLag.success) {
                throw "Kafka lag did not return to zero before round: label=$label lag=$($preflightLag.lag) elapsedSec=$($preflightLag.elapsed_sec)"
            }

            $voucherId = New-SeckillVoucher -CurrentStock $Stock -Label $label
            Write-Log "Seckill bench start threads=$threadCount total=$Requests round=$round voucherId=$voucherId"

            $preSample = Get-Sample -ElapsedSec 0
            Save-JsonLine -Path $sampleFile -Record $preSample

            $benchProc = Start-Process -FilePath 'java' `
                -ArgumentList @('-cp', $classpath, 'com.hmdp.loadtest.SeckillLoadBench', $BaseUrl, "$voucherId", "$threadCount", "$Requests", $TokensFile) `
                -WorkingDirectory $repoRoot `
                -RedirectStandardOutput $stdoutFile `
                -RedirectStandardError $stderrFile `
                -WindowStyle Hidden `
                -PassThru

            $samples = New-Object System.Collections.Generic.List[hashtable]
            $samples.Add($preSample)
            $peaks = @{}
            Update-Peaks -Peaks $peaks -Sample $preSample

            $watch = [System.Diagnostics.Stopwatch]::StartNew()
            $nextSampleAt = 0
            while (-not $benchProc.HasExited) {
                if ($watch.ElapsedMilliseconds -ge $nextSampleAt) {
                    $sample = Get-Sample -ElapsedSec $watch.Elapsed.TotalSeconds
                    $samples.Add($sample)
                    Save-JsonLine -Path $sampleFile -Record $sample
                    Update-Peaks -Peaks $peaks -Sample $sample
                    $nextSampleAt += $SampleIntervalMs
                }
                Start-Sleep -Milliseconds 200
                $benchProc.Refresh()
            }
            $watch.Stop()
            $benchProc.WaitForExit()

            $postBenchSample = Get-Sample -ElapsedSec $watch.Elapsed.TotalSeconds
            $samples.Add($postBenchSample)
            Save-JsonLine -Path $sampleFile -Record $postBenchSample
            Update-Peaks -Peaks $peaks -Sample $postBenchSample

            $stdoutLines = if (Test-Path -LiteralPath $stdoutFile) { Get-Content -LiteralPath $stdoutFile } else { @() }
            $stderrLines = if (Test-Path -LiteralPath $stderrFile) { Get-Content -LiteralPath $stderrFile } else { @() }
            $metrics = Parse-Metrics $stdoutLines
            $benchProc.Refresh()
            $benchExitCode = if ($benchProc.HasExited) { $benchProc.ExitCode } else { $null }
            $hasMetricOutput = $metrics.ContainsKey('duration_sec') -and $metrics.ContainsKey('total')
            if (($null -ne $benchExitCode) -and ($benchExitCode -ne 0)) {
                throw "Seckill bench failed (exitCode=$benchExitCode): $($stderrLines -join [Environment]::NewLine)"
            }
            if (($null -eq $benchExitCode) -and (-not $hasMetricOutput)) {
                throw "Seckill bench failed (exitCode unavailable): $($stderrLines -join [Environment]::NewLine)"
            }
            $success = if ($metrics.ContainsKey('success_true~')) { [int]$metrics['success_true~'] } else { 0 }
            $benchSec = if ($metrics.ContainsKey('duration_sec')) { [double]$metrics['duration_sec'] } else { [math]::Round($watch.Elapsed.TotalSeconds, 3) }

            $drainStart = Get-Date
            $lastCount = -1
            $stableTimes = 0
            $finalOrders = 0
            $drainEndReason = 'unknown'
            $lastDrainLagObservation = $null
            while ($true) {
                Start-Sleep -Seconds $DrainPollSeconds
                $sample = Get-Sample -ElapsedSec ($watch.Elapsed.TotalSeconds + ((Get-Date) - $drainStart).TotalSeconds)
                $samples.Add($sample)
                Save-JsonLine -Path $sampleFile -Record $sample
                Update-Peaks -Peaks $peaks -Sample $sample

                if ($UseBenchProbeApi) {
                    $summary = Get-BenchProbeSeckillSummary -VoucherId $voucherId
                    $finalOrders = if ($null -ne $summary.orderCount) { [int]$summary.orderCount } else { 0 }
                }
                else {
                    $countText = Get-Scalar "select count(*) from tb_voucher_order where voucher_id = $voucherId;"
                    $finalOrders = if ($countText) { [int]$countText } else { 0 }
                }

                $lagZeroSatisfied = $true
                if ($RequireKafkaLagZero) {
                    $lastDrainLagObservation = Get-KafkaLagObservation
                    $lagZeroSatisfied = ($null -ne $lastDrainLagObservation.total_lag) -and ([double]$lastDrainLagObservation.total_lag -le 0)
                }

                if (($finalOrders -eq $success) -and $lagZeroSatisfied) {
                    $drainEndReason = if ($RequireKafkaLagZero) { 'orders-matched-and-lag-zero' } else { 'orders-matched' }
                    break
                }
                if ($finalOrders -eq $lastCount) {
                    $stableTimes++
                }
                else {
                    $stableTimes = 0
                    $lastCount = $finalOrders
                }
                if (($stableTimes -ge $DrainStableRounds) -and $lagZeroSatisfied) {
                    $drainEndReason = if ($RequireKafkaLagZero) { 'stable-orders-and-lag-zero' } else { 'stable-orders' }
                    break
                }
                if (((Get-Date) - $drainStart).TotalSeconds -ge $DrainTimeoutSeconds) {
                    $drainEndReason = 'timeout'
                    break
                }
            }

            if (-not $RequireKafkaLagZero) {
                $lastDrainLagObservation = Get-KafkaLagObservation
            }
            $drainSec = [math]::Round(((Get-Date) - $drainStart).TotalSeconds, 3)
            $totalSec = $benchSec + $drainSec
            if ($UseBenchProbeApi) {
                $summary = Get-BenchProbeSeckillSummary -VoucherId $voucherId
                $stockLeft = $summary.stockLeft
                $failTaskCount = $summary.failTaskCount
            }
            else {
                $stockLeft = Get-Scalar "select stock from tb_seckill_voucher where voucher_id = $voucherId;"
                $failTaskCount = Get-Scalar "select count(*) from tb_voucher_order_fail_task where voucher_id = $voucherId;"
            }
            $postSample = Get-Sample -ElapsedSec ($benchSec + $drainSec)
            $samples.Add($postSample)
            Save-JsonLine -Path $sampleFile -Record $postSample
            Update-Peaks -Peaks $peaks -Sample $postSample

            if ($UseBenchProbeApi) {
                $artifacts = [ordered]@{
                    processlist = $null
                    innodb_status = $null
                    digest = $null
                }
                $topDigest = Get-BenchProbeTopDigestSummary
            }
            else {
                Write-Log "capture artifacts start label=$label"
                $artifacts = Capture-EngineArtifacts -Prefix $label
                Write-Log "capture artifacts done label=$label"
                $topDigest = Get-TopDigestSummary -DigestFile $artifacts.digest
            }
            $mysqlDelta = Get-MySqlDelta -PreMySql $preSample.mysql -PostMySql $postSample.mysql

            $record = [ordered]@{
                timestamp = (Get-Date).ToString('s')
                target = 'seckill-bottleneck'
                baseUrl = $BaseUrl
                mode = $SeckillMode
                threads = $threadCount
                total = $Requests
                stock = $Stock
                voucherId = $voucherId
                round = $round
                appPid = $appPid
                environment = [ordered]@{
                    shop_mode = $ShopMode
                    seckill_mode = $SeckillMode
                    kafka_consumer_concurrency = $KafkaConsumerConcurrency
                    kafka_relay_batch_size = $KafkaRelayBatchSize
                    kafka_relay_block_timeout_ms = $KafkaRelayBlockTimeoutMs
                    kafka_default_topic = if (-not [string]::IsNullOrWhiteSpace($KafkaDefaultTopic)) { $KafkaDefaultTopic } else { $null }
                    kafka_consumer_group = if (-not [string]::IsNullOrWhiteSpace($KafkaConsumerGroup)) { $KafkaConsumerGroup } else { $null }
                    kafka_relay_consumer_name = if (-not [string]::IsNullOrWhiteSpace($KafkaRelayConsumerName)) { $KafkaRelayConsumerName } else { $null }
                    kafka_lag_interval_ms = $KafkaLagIntervalMs
                    sample_interval_ms = $SampleIntervalMs
                    require_kafka_lag_zero = [bool]$RequireKafkaLagZero
                    lag_zero_timeout_seconds = $LagZeroTimeoutSeconds
                    lag_zero_stable_rounds = $LagZeroStableRounds
                    skip_app_lifecycle = [bool]$SkipAppLifecycle
                    use_bench_probe_api = [bool]$UseBenchProbeApi
                }
                metrics = $metrics
                extra = [ordered]@{
                    bench_sec = $benchSec
                    drain_sec = $drainSec
                    final_orders = $finalOrders
                    stock_left = if ($null -ne $stockLeft) { [int]$stockLeft } else { $null }
                    fail_task_count = if ($null -ne $failTaskCount) { [int]$failTaskCount } else { 0 }
                    end_to_end_tps = if ($totalSec -gt 0) { [math]::Round($finalOrders / $totalSec, 1) } else { $null }
                }
                observability = [ordered]@{
                    preflight_lag = $preflightLag
                    pre_sample = $preSample
                    post_sample = $postSample
                    peak = $peaks
                    drain = [ordered]@{
                        end_reason = $drainEndReason
                        final_lag = $lastDrainLagObservation
                    }
                    mysql_delta = $mysqlDelta
                    mysql_top_digest = $topDigest
                    sample_file = $sampleFile
                    sample_count = $samples.Count
                    processlist_file = $artifacts.processlist
                    innodb_status_file = $artifacts.innodb_status
                    digest_file = $artifacts.digest
                }
                raw = [ordered]@{
                    stdout = $stdoutLines
                    stderr = $stderrLines
                }
            }
            Write-Log "save result start label=$label"
            Save-JsonLine -Path $resultFile -Record $record
            Write-Log "save result done label=$label"
            Write-Log "Seckill bench done threads=$threadCount round=$round success=$success final=$finalOrders e2eTps=$($record.extra.end_to_end_tps)"

            if (-not $SkipAppLifecycle) {
                Stop-App
            }
        }
    }

    Write-Log "all benchmark loops done"
    Write-Host "RESULT_FILE=$resultFile"
}
finally {
    Pop-Location
}

