# HTTP 压测：POST /voucher-order/seckill/{id}
# - 单 token：-Token 或 $env:SECKILL_TOKEN（大量并发会主要为「重复下单」，适合压接口吞吐）
# - 多用户：-TokensFile 每行一个 authorization（# 开头为注释），请求按序号轮询 token
#
# 示例：
#   $env:SECKILL_TOKEN="单用户token"
#   .\loadtest-seckill.ps1 -VoucherId 10 -Workers 30 -PerWorker 40
#
#   .\loadtest-seckill.ps1 -TokensFile "d:\Java\hm-dianping\scripts\my-tokens.txt" -VoucherId 10 -Workers 30 -PerWorker 40

param(
    [string]$BaseUrl = "http://127.0.0.1:8081",
    [string]$Token = "",
    [string]$TokensFile = "",
    [long]$VoucherId = 10,
    [int]$Workers = 20,
    [int]$PerWorker = 30
)

$resolvedFile = $TokensFile
if (-not $resolvedFile) {
    if (-not $Token) { $Token = $env:SECKILL_TOKEN }
    if (-not $Token) {
        Write-Error "请设置 -TokensFile（多行 token）或 -Token / `$env:SECKILL_TOKEN（单 token）"
    }
    $resolvedFile = [System.IO.Path]::GetTempFileName()
    Set-Content -LiteralPath $resolvedFile -Value $Token -Encoding utf8
}

$tokenLines = Get-Content -LiteralPath $resolvedFile -Encoding utf8 | ForEach-Object { $_.Trim() } | Where-Object { $_ -ne "" -and $_ -notmatch "^\s*#" }
if ($tokenLines.Count -lt 1) {
    if ($TokensFile) { Write-Error "Tokens file empty: $TokensFile" }
    else { Write-Error "No token" }
}
$isTempFile = -not $TokensFile

$uri = "$BaseUrl/voucher-order/seckill/$VoucherId"
Write-Host "URI=$uri  Workers=$Workers  PerWorker=$PerWorker  tokenLines=$($tokenLines.Count)"

$scriptBlock = {
    param($U, $Toks, $Offset, $N)
    $L = $Toks.Count
    $ok = 0; $httpErr = 0; $bizFail = 0
    for ($j = 0; $j -lt $N; $j++) {
        $g = $Offset + $j
        $idx = $g % $L
        $Tok = $Toks[$idx]
        $h = @{ authorization = $Tok }
        try {
            $r = Invoke-WebRequest -Uri $U -Method POST -Headers $h -UseBasicParsing -TimeoutSec 30
            if ($r.StatusCode -ne 200) { $httpErr++; continue }
            $body = $r.Content
            if ($body -match '"success"\s*:\s*true') { $ok++ }
            elseif ($body -match '"success"\s*:\s*false') { $bizFail++ }
            else { $httpErr++ }
        } catch {
            $httpErr++
        }
    }
    [pscustomobject]@{ OK = $ok; BizFail = $bizFail; HttpErr = $httpErr }
}

$sw = [System.Diagnostics.Stopwatch]::StartNew()
$jobs = @()
for ($w = 0; $w -lt $Workers; $w++) {
    $offset = $w * $PerWorker
    $jobs += Start-Job -ScriptBlock $scriptBlock -ArgumentList $uri, $tokenLines, $offset, $PerWorker
}
$results = $jobs | Wait-Job | Receive-Job
Remove-Job $jobs
$sw.Stop()

if ($isTempFile) {
    Remove-Item -LiteralPath $resolvedFile -Force -ErrorAction SilentlyContinue
}

$tOk = ($results | Measure-Object -Property OK -Sum).Sum
$tBiz = ($results | Measure-Object -Property BizFail -Sum).Sum
$tHttp = ($results | Measure-Object -Property HttpErr -Sum).Sum
$total = $Workers * $PerWorker

Write-Host "----"
Write-Host "DurationMs: $($sw.ElapsedMilliseconds)"
Write-Host "TotalRequests: $total"
$sec = [math]::Max($sw.ElapsedMilliseconds / 1000.0, 0.001)
$approxRps = [math]::Round($total / $sec, 2)
Write-Host "ApproxRps (total/duration, 含业务失败): $approxRps"
Write-Host "success=true (approx): $tOk"
Write-Host "success=false (库存不足/重复下单等): $tBiz"
Write-Host "http/other errors: $tHttp"
