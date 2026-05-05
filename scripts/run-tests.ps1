param(
    [ValidateSet("all", "api", "ui", "seckill")]
    [string]$Mode = "all",
    [string]$BaseUrl = "http://127.0.0.1:8081",
    [string]$UiBaseUrl = "http://127.0.0.1:8080",
    [switch]$Require8082,
    [switch]$EnableUiLoginE2E,
    [switch]$ServeAllure
)

$ErrorActionPreference = "Stop"

function Test-Port([int]$Port) {
    try {
        return (Test-NetConnection -ComputerName 127.0.0.1 -Port $Port -WarningAction SilentlyContinue).TcpTestSucceeded
    }
    catch {
        return $false
    }
}

function Assert-Port([int]$Port, [string]$Name) {
    if (-not (Test-Port $Port)) {
        throw "$Name port $Port is unavailable. Please start service first."
    }
    Write-Host "[OK] $Name port $Port"
}

Write-Host "== Port pre-check =="
Assert-Port 8081 "Backend"
Assert-Port 8080 "Nginx"
Assert-Port 6379 "Redis"

if (Test-Port 8082) {
    Write-Host "[OK] Backend node2 port 8082"
}
else {
    $msg = "[WARN] Backend node2 port 8082 is down (nginx upstream may cause random failures)."
    if ($Require8082) {
        throw $msg
    }
    Write-Host $msg
}

if ($EnableUiLoginE2E) {
    $env:RUN_UI_LOGIN_E2E = "1"
    Write-Host "[INFO] Enabled real UI login action test."
}
else {
    Remove-Item Env:RUN_UI_LOGIN_E2E -ErrorAction SilentlyContinue
}

$resultsDir = "reports/allure-results"
if (Test-Path $resultsDir) {
    Remove-Item $resultsDir -Recurse -Force
}
New-Item -ItemType Directory -Path $resultsDir | Out-Null

$pytestArgs = @("--base-url", $BaseUrl, "--ui-base-url", $UiBaseUrl, "--alluredir", $resultsDir, "-q")
switch ($Mode) {
    "api" { $pytestArgs = @("-m", "api") + $pytestArgs }
    "ui" { $pytestArgs = @("-m", "ui", "--headless") + $pytestArgs }
    "seckill" { $pytestArgs = @("-k", "seckill", "--headless") + $pytestArgs }
    default { }
}

Write-Host "== Running pytest ($Mode) =="
python -m pytest @pytestArgs
if ($LASTEXITCODE -ne 0) {
    throw "pytest failed with exit code $LASTEXITCODE"
}

Write-Host "[OK] Pytest completed. Allure results: $resultsDir"
if ($ServeAllure) {
    Write-Host "== Opening Allure report =="
    allure serve $resultsDir
}
