param(
    [string]$BaseUrl = 'http://127.0.0.1:8081',
    [string]$OutputRoot = 'benchmarks\p4-split-machine',
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
    [switch]$SkipBuild,
    [switch]$Append,
    [switch]$UseBenchProbeApi,
    [switch]$SkipRemotePreflight,
    [switch]$PreflightOnly
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$repoRoot = Split-Path -Parent $PSScriptRoot
$scriptPath = Join-Path $PSScriptRoot 'run-seckill-bottleneck-benchmark.ps1'

function Write-Log([string]$Message) {
    $ts = Get-Date -Format 'yyyy-MM-dd HH:mm:ss'
    Write-Host "[$ts] $Message"
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

function Save-JsonFile([string]$Path, $Record) {
    $parent = Split-Path -Parent $Path
    if ($parent -and -not (Test-Path -LiteralPath $parent)) {
        New-Item -ItemType Directory -Force -Path $parent | Out-Null
    }
    $plain = ConvertTo-PlainJsonValue $Record
    ($plain | ConvertTo-Json -Depth 12) | Set-Content -LiteralPath $Path -Encoding UTF8
}

function Get-EndpointUrl([string]$Path) {
    return ($BaseUrl.TrimEnd('/') + $Path)
}

function Invoke-EndpointCheck([string]$Name, [string]$Path) {
    $url = Get-EndpointUrl -Path $Path
    $watch = [System.Diagnostics.Stopwatch]::StartNew()
    try {
        $resp = Invoke-WebRequest -UseBasicParsing -Uri $url -TimeoutSec 10
        $watch.Stop()
        return [ordered]@{
            name = $Name
            path = $Path
            url = $url
            success = $true
            status_code = [int]$resp.StatusCode
            elapsed_ms = [int]$watch.ElapsedMilliseconds
            content_type = $resp.Headers['Content-Type']
        }
    }
    catch {
        $watch.Stop()
        $statusCode = $null
        $response = $_.Exception.Response
        if ($null -ne $response -and $response.StatusCode) {
            $statusCode = [int]$response.StatusCode
        }
        return [ordered]@{
            name = $Name
            path = $Path
            url = $url
            success = $false
            status_code = $statusCode
            elapsed_ms = [int]$watch.ElapsedMilliseconds
            error = $_.Exception.Message
        }
    }
}

function Invoke-RemotePreflight([string]$ReportPath) {
    $checks = @(
        @{ name = 'shop endpoint'; path = '/shop/1' },
        @{ name = 'consumer metric'; path = '/actuator/metrics/benchmark.consumer.max.cost.ms' },
        @{ name = 'order metric'; path = '/actuator/metrics/benchmark.order.total.max.cost.ms' },
        @{ name = 'mysql snapshot probe'; path = '/bench/mysql/snapshot' },
        @{ name = 'mysql top digest probe'; path = '/bench/mysql/top-digest' },
        @{ name = 'kafka lag probe'; path = '/bench/kafka/lag' }
    )

    $results = New-Object System.Collections.Generic.List[object]
    $failed = New-Object System.Collections.Generic.List[string]
    foreach ($check in $checks) {
        $result = Invoke-EndpointCheck -Name $check.name -Path $check.path
        $results.Add($result)
        if (-not $result.success) {
            $failed.Add($check.name)
        }
        Write-Log ("[remote-preflight] {0} -> success={1} status={2} elapsedMs={3}" -f $check.name, $result.success, $result.status_code, $result.elapsed_ms)
    }

    $record = [ordered]@{
        generated_at = (Get-Date).ToString('yyyy-MM-dd HH:mm:ss')
        base_url = $BaseUrl
        use_bench_probe_api = [bool]$UseBenchProbeApi
        success = ($failed.Count -eq 0)
        failed_checks = @($failed.ToArray())
        checks = $results.ToArray()
    }

    Save-JsonFile -Path $ReportPath -Record $record
    if ($failed.Count -gt 0) {
        throw "Remote preflight failed. baseUrl=$BaseUrl failedChecks=$($failed -join ', ') report=$ReportPath"
    }
    return $record
}

if ($PreflightOnly -and $SkipRemotePreflight) {
    throw 'PreflightOnly cannot be used together with SkipRemotePreflight.'
}

$preflightReportPath = Join-Path $repoRoot (Join-Path (Join-Path $OutputRoot $DateStamp) 'remote-preflight.json')
if (-not $SkipRemotePreflight) {
    $preflight = Invoke-RemotePreflight -ReportPath $preflightReportPath
    Write-Log "Remote preflight passed. report=$preflightReportPath"
    Write-Host "REMOTE_PREFLIGHT_REPORT=$preflightReportPath"
    if ($PreflightOnly) {
        exit 0
    }
}

& $scriptPath `
    -BaseUrl $BaseUrl `
    -OutputRoot $OutputRoot `
    -DateStamp $DateStamp `
    -ResultFileName $ResultFileName `
    -Threads $Threads `
    -Repeat $Repeat `
    -Requests $Requests `
    -Stock $Stock `
    -TokensFile $TokensFile `
    -DbPassword $DbPassword `
    -RedisHost $RedisHost `
    -RedisPort $RedisPort `
    -RedisPassword $RedisPassword `
    -KafkaBootstrapServers $KafkaBootstrapServers `
    -KafkaDefaultTopic $KafkaDefaultTopic `
    -KafkaConsumerGroup $KafkaConsumerGroup `
    -KafkaRelayConsumerName $KafkaRelayConsumerName `
    -MySqlPath $MySqlPath `
    -ShopMode $ShopMode `
    -SeckillMode $SeckillMode `
    -KafkaConsumerConcurrency $KafkaConsumerConcurrency `
    -KafkaRelayBatchSize $KafkaRelayBatchSize `
    -KafkaRelayBlockTimeoutMs $KafkaRelayBlockTimeoutMs `
    -KafkaLagIntervalMs $KafkaLagIntervalMs `
    -SampleIntervalMs $SampleIntervalMs `
    -DrainPollSeconds $DrainPollSeconds `
    -DrainStableRounds $DrainStableRounds `
    -DrainTimeoutSeconds $DrainTimeoutSeconds `
    -SkipAppLifecycle `
    -RequireKafkaLagZero `
    -SkipBuild:$SkipBuild `
    -Append:$Append `
    -UseBenchProbeApi:$UseBenchProbeApi



