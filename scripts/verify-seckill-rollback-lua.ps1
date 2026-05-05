# Verify seckill_rollback.lua idempotence (requires local Redis; password matches application.yaml)
# Usage: powershell -ExecutionPolicy Bypass -File scripts/verify-seckill-rollback-lua.ps1
# Env: REDIS_CLI, REDIS_HOST, REDIS_PORT, REDIS_PASSWORD

$ErrorActionPreference = "Stop"

$redisCli = if ($env:REDIS_CLI) { $env:REDIS_CLI } else { "redis-cli" }
# Do not use $host (PowerShell built-in read-only)
$redisHost = if ($env:REDIS_HOST) { $env:REDIS_HOST } else { "127.0.0.1" }
$redisPort = if ($env:REDIS_PORT) { $env:REDIS_PORT } else { "6379" }
$redisPassword = if ($env:REDIS_PASSWORD) { $env:REDIS_PASSWORD } else { "123456" }

# PSScriptRoot = ...\hm-dianping\scripts -> project root is one level up
$root = Split-Path -Parent $PSScriptRoot
$luaPath = Join-Path $root "src\main\resources\seckill_rollback.lua"
if (-not (Test-Path $luaPath)) {
    Write-Error "Lua script not found: $luaPath"
}

$vid = "999999"
$uid = "888888"
$stockKey = "seckill:stock:$vid"
$orderKey = "seckill:order:$vid"

# Use REDISCLI_AUTH instead of -a so redis-cli does not write stderr warnings (PowerShell treats them as errors when $ErrorActionPreference=Stop)
function Invoke-Redis {
    param([string[]]$RedisArgs)
    $prevAuth = $env:REDISCLI_AUTH
    try {
        if ($redisPassword) {
            $env:REDISCLI_AUTH = $redisPassword
        }
        & $redisCli -h $redisHost -p $redisPort @RedisArgs
    } finally {
        if ($redisPassword) {
            if ($null -ne $prevAuth) {
                $env:REDISCLI_AUTH = $prevAuth
            } else {
                Remove-Item Env:\REDISCLI_AUTH -ErrorAction SilentlyContinue
            }
        }
    }
}

Write-Host "== setup keys: $stockKey / $orderKey"
Invoke-Redis @("SET", $stockKey, "5") | Out-Null
Invoke-Redis @("SADD", $orderKey, $uid) | Out-Null

Write-Host "== first rollback (expect script return 0)"
$r1 = Invoke-Redis @("--eval", $luaPath, ",", $vid, $uid)
Write-Host "script result: $r1"

$sAfter = Invoke-Redis @("GET", $stockKey)
$inSet = Invoke-Redis @("SISMEMBER", $orderKey, $uid)
Write-Host "after rollback: stock=$sAfter userInOrderSet=$inSet"

Write-Host "== second rollback idempotent (expect script return 1)"
$r2 = Invoke-Redis @("--eval", $luaPath, ",", $vid, $uid)
Write-Host "script result: $r2"

Write-Host "== cleanup keys"
Invoke-Redis @("DEL", $stockKey) | Out-Null
Invoke-Redis @("DEL", $orderKey) | Out-Null

if ($r1 -eq "0" -and $r2 -eq "1" -and $sAfter -eq "6" -and $inSet -eq "0") {
    Write-Host "RESULT: OK"
    exit 0
}
Write-Host "RESULT: FAIL (check redis-cli, password, port)"
exit 1
