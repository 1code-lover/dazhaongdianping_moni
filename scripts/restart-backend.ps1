<#
    Backend restart script.
    Build the project, start the packaged jar in the background,
    and wait until the backend is ready.

    @author ethan
    @date 2026-06-16
#>

param(
    [int]$Port = 8081,
    [int]$StartupTimeoutSec = 120,
    [int]$BuildTimeoutSec = 600,
    [switch]$SkipCompile,
    [string]$LogFile = "app.log",
    [string]$ErrorLogFile = "app-error.log",
    [string]$BuildLogFile = ".restart-backend-build.log",
    [string]$BuildErrorLogFile = ".restart-backend-build-error.log"
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$ProjectRoot = Split-Path -Parent $PSScriptRoot
$LogPath = Join-Path $ProjectRoot $LogFile
$ErrorLogPath = Join-Path $ProjectRoot $ErrorLogFile
$BuildLogPath = Join-Path $ProjectRoot $BuildLogFile
$BuildErrorLogPath = Join-Path $ProjectRoot $BuildErrorLogFile
$StartupSuccessKeyword = "Started HmDianPingApplication"
$HealthCheckUrl = "http://127.0.0.1:$Port/actuator/health"
$JavaArguments = @(
    "--add-opens=java.base/java.lang=ALL-UNNAMED",
    "--add-opens=java.base/java.lang.invoke=ALL-UNNAMED",
    "--add-opens=java.base/java.lang.reflect=ALL-UNNAMED",
    "--add-opens=java.base/java.util=ALL-UNNAMED"
)

function Write-Step {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Message
    )

    Write-Host ("[{0}] {1}" -f (Get-Date -Format "yyyy-MM-dd HH:mm:ss"), $Message)
}

function Get-MavenCommand {
    $mavenCommand = Get-Command "mvn.cmd" -ErrorAction SilentlyContinue
    if ($null -eq $mavenCommand) {
        $mavenCommand = Get-Command "mvn" -ErrorAction SilentlyContinue
    }

    if ($null -eq $mavenCommand) {
        throw "Maven command was not found in PATH."
    }

    return $mavenCommand.Source
}

function Get-JavaCommand {
    $javaCommand = Get-Command "java.exe" -ErrorAction SilentlyContinue
    if ($null -eq $javaCommand) {
        $javaCommand = Get-Command "java" -ErrorAction SilentlyContinue
    }

    if ($null -eq $javaCommand) {
        throw "Java command was not found in PATH."
    }

    return $javaCommand.Source
}

function Get-PortProcessId {
    param(
        [Parameter(Mandatory = $true)]
        [int]$TargetPort
    )

    try {
        $connection = Get-NetTCPConnection -LocalPort $TargetPort -State Listen -ErrorAction Stop |
            Select-Object -First 1
        if ($null -ne $connection) {
            return [int]$connection.OwningProcess
        }
    }
    catch {
    }

    $netstatLines = netstat -ano -p tcp | Select-String -Pattern "LISTENING"
    foreach ($line in $netstatLines) {
        $parts = ($line.ToString() -replace "\s+", " ").Trim().Split(" ")
        if ($parts.Count -lt 5) {
            continue
        }

        $localAddress = $parts[1]
        $state = $parts[3]
        $pidText = $parts[4]
        if ($state -ne "LISTENING") {
            continue
        }

        if ($localAddress -match ":(\d+)$" -and [int]$Matches[1] -eq $TargetPort) {
            return [int]$pidText
        }
    }

    return $null
}

function Test-PortListening {
    param(
        [Parameter(Mandatory = $true)]
        [int]$TargetPort
    )

    return $null -ne (Get-PortProcessId -TargetPort $TargetPort)
}

function Clear-LogFile {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Path
    )

    $parent = Split-Path -Parent $Path
    if (-not (Test-Path $parent)) {
        New-Item -ItemType Directory -Path $parent | Out-Null
    }

    Set-Content -Path $Path -Value "" -Encoding UTF8
}

function Normalize-ProcessEnvironment {
    $pathValue = $env:Path
    Remove-Item Env:PATH -ErrorAction SilentlyContinue
    $env:Path = $pathValue
}

function Get-LogTail {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Path,
        [int]$Tail = 40
    )

    if (-not (Test-Path $Path)) {
        return "(log file not found)"
    }

    $content = Get-Content -Path $Path -Tail $Tail -ErrorAction SilentlyContinue
    if ($null -eq $content) {
        return "(log file is empty)"
    }

    $contentLines = @($content)
    if ($contentLines.Count -eq 0) {
        return "(log file is empty)"
    }

    return ($contentLines -join [Environment]::NewLine)
}

function Stop-BackendProcess {
    param(
        [Parameter(Mandatory = $true)]
        [int]$TargetPort
    )

    $existingPid = Get-PortProcessId -TargetPort $TargetPort
    if ($null -eq $existingPid) {
        Write-Step "No process is listening on port $TargetPort."
        return
    }

    $process = Get-Process -Id $existingPid -ErrorAction SilentlyContinue
    $processName = if ($null -ne $process) { $process.ProcessName } else { "unknown" }
    Write-Step ("Stopping process on port {0}. PID={1}, Name={2}" -f $TargetPort, $existingPid, $processName)

    Stop-Process -Id $existingPid -Force -ErrorAction Stop

    for ($i = 0; $i -lt 20; $i++) {
        Start-Sleep -Seconds 1
        if (-not (Test-PortListening -TargetPort $TargetPort)) {
            Write-Step "Port $TargetPort has been released."
            return
        }
    }

    throw "The process stopped but port $TargetPort is still occupied."
}

function Invoke-Build {
    param(
        [Parameter(Mandatory = $true)]
        [string]$MavenCommand,
        [Parameter(Mandatory = $true)]
        [string]$RootPath,
        [Parameter(Mandatory = $true)]
        [int]$TimeoutSec,
        [Parameter(Mandatory = $true)]
        [string]$StdoutPath,
        [Parameter(Mandatory = $true)]
        [string]$StderrPath
    )

    if ($SkipCompile) {
        Write-Step "SkipCompile is set. Build is skipped."
        return
    }

    Clear-LogFile -Path $StdoutPath
    Clear-LogFile -Path $StderrPath

    Normalize-ProcessEnvironment

    Write-Step "Building jar with mvn clean package -Dmaven.test.skip=true"
    Write-Step "Do not test port 8081 until 'Backend is ready' appears."

    $buildProcess = Start-Process -FilePath $MavenCommand `
        -ArgumentList "clean", "package", "-Dmaven.test.skip=true" `
        -WorkingDirectory $RootPath `
        -RedirectStandardOutput $StdoutPath `
        -RedirectStandardError $StderrPath `
        -WindowStyle Hidden `
        -PassThru

    $deadline = (Get-Date).AddSeconds($TimeoutSec)
    while (-not $buildProcess.HasExited) {
        if ((Get-Date) -ge $deadline) {
            try {
                Stop-Process -Id $buildProcess.Id -Force -ErrorAction SilentlyContinue
            }
            catch {
            }
            throw "Build timed out after $TimeoutSec seconds."
        }

        $elapsedSeconds = [int]((Get-Date) - $buildProcess.StartTime).TotalSeconds
        Write-Step ("Build is still running. Elapsed={0}s" -f $elapsedSeconds)
        Start-Sleep -Seconds 5
        $buildProcess.Refresh()
    }

    $buildLogContent = ""
    if (Test-Path $StdoutPath) {
        $buildLogContent = Get-Content -Path $StdoutPath -Raw -ErrorAction SilentlyContinue
    }

    $buildSucceeded = $false
    if ($null -ne $buildLogContent -and $buildLogContent.Contains("[INFO] BUILD SUCCESS")) {
        $buildSucceeded = $true
    }

    if (-not $buildSucceeded) {
        $exitCodeText = if ($null -ne $buildProcess.ExitCode) { [string]$buildProcess.ExitCode } else { "unknown" }
        throw ("Build failed. ExitCode={0}" -f $exitCodeText)
    }

    Write-Step "Build succeeded."
}

function Get-BuiltJarPath {
    param(
        [Parameter(Mandatory = $true)]
        [string]$RootPath
    )

    $jarFile = Get-ChildItem -Path (Join-Path $RootPath "target") -Filter "*.jar" -File -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -notmatch "^original-" } |
        Sort-Object LastWriteTime -Descending |
        Select-Object -First 1

    if ($null -eq $jarFile) {
        throw "No runnable jar was found in target directory."
    }

    return $jarFile.FullName
}

function Start-BackendProcess {
    param(
        [Parameter(Mandatory = $true)]
        [string]$JavaCommand,
        [Parameter(Mandatory = $true)]
        [string]$JarPath,
        [Parameter(Mandatory = $true)]
        [string]$RootPath,
        [Parameter(Mandatory = $true)]
        [string]$StdoutPath,
        [Parameter(Mandatory = $true)]
        [string]$StderrPath
    )

    Clear-LogFile -Path $StdoutPath
    Clear-LogFile -Path $StderrPath

    Normalize-ProcessEnvironment

    $javaArgs = @()
    $javaArgs += $JavaArguments
    $javaArgs += "-jar"
    $javaArgs += $JarPath

    Write-Step ("Starting packaged jar: {0}" -f $JarPath)
    $process = Start-Process -FilePath $JavaCommand `
        -ArgumentList $javaArgs `
        -WorkingDirectory $RootPath `
        -RedirectStandardOutput $StdoutPath `
        -RedirectStandardError $StderrPath `
        -WindowStyle Hidden `
        -PassThru

    Write-Step ("Start command submitted. PID={0}" -f $process.Id)
    return [PSCustomObject]@{
        Pid = $process.Id
        JarPath = $JarPath
    }
}

function Test-HealthEndpoint {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Url
    )

    try {
        $response = Invoke-WebRequest -UseBasicParsing -Uri $Url -TimeoutSec 3 -ErrorAction Stop
        return $response.StatusCode -eq 200
    }
    catch {
        return $false
    }
}

function Get-HealthStatusCode {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Url
    )

    try {
        $response = Invoke-WebRequest -UseBasicParsing -Uri $Url -TimeoutSec 3 -ErrorAction Stop
        return [int]$response.StatusCode
    }
    catch {
        if ($null -ne $_.Exception.Response -and $null -ne $_.Exception.Response.StatusCode) {
            return [int]$_.Exception.Response.StatusCode
        }

        return -1
    }
}

function Wait-BackendReady {
    param(
        [Parameter(Mandatory = $true)]
        [int]$TargetPort,
        [Parameter(Mandatory = $true)]
        [int]$TimeoutSec,
        [Parameter(Mandatory = $true)]
        [string]$StdoutPath,
        [Parameter(Mandatory = $true)]
        [string]$SuccessKeyword,
        [Parameter(Mandatory = $true)]
        [int]$BackendPid,
        [Parameter(Mandatory = $true)]
        [string]$HealthUrl
    )

    $deadline = (Get-Date).AddSeconds($TimeoutSec)
    $portReady = $false
    $logReady = $false
    $healthReady = $false
    $lastHeartbeatAt = Get-Date

    while ((Get-Date) -lt $deadline) {
        if (-not $portReady -and (Test-PortListening -TargetPort $TargetPort)) {
            $portReady = $true
            Write-Step "Target port is listening."
        }

        if (-not $logReady -and (Test-Path $StdoutPath)) {
            $logContent = Get-Content -Path $StdoutPath -Raw -ErrorAction SilentlyContinue
            if ($null -ne $logContent -and $logContent.Contains($SuccessKeyword)) {
                $logReady = $true
                Write-Step "Startup success log detected."
            }
        }

        if (-not $healthReady -and (Test-HealthEndpoint -Url $HealthUrl)) {
            $healthReady = $true
            Write-Step ("Health check succeeded: {0}" -f $HealthUrl)
        }

        if (((Get-Date) - $lastHeartbeatAt).TotalSeconds -ge 5) {
            Write-Step ("Waiting for backend. PortReady={0}, LogReady={1}, HealthReady={2}, PID={3}" -f `
                $portReady, $logReady, $healthReady, $BackendPid)
            $lastHeartbeatAt = Get-Date
        }

        if ($portReady -and $logReady -and $healthReady) {
            return [PSCustomObject]@{
                PortReady = $true
                LogReady = $true
                HealthReady = $true
                TimedOut = $false
            }
        }

        $backendProcess = Get-Process -Id $BackendPid -ErrorAction SilentlyContinue
        if ($null -eq $backendProcess) {
            throw "Backend process exited before the service became ready."
        }

        Start-Sleep -Seconds 2
    }

    return [PSCustomObject]@{
        PortReady = $portReady
        LogReady = $logReady
        HealthReady = $healthReady
        TimedOut = $true
    }
}

$mavenCommand = $null
$javaCommand = $null
$backendProcess = $null

try {
    Write-Step "Backend restart started."
    $mavenCommand = Get-MavenCommand
    $javaCommand = Get-JavaCommand
    Write-Step ("Detected Maven command: {0}" -f $mavenCommand)
    Write-Step ("Detected Java command: {0}" -f $javaCommand)

    Stop-BackendProcess -TargetPort $Port
    Invoke-Build `
        -MavenCommand $mavenCommand `
        -RootPath $ProjectRoot `
        -TimeoutSec $BuildTimeoutSec `
        -StdoutPath $BuildLogPath `
        -StderrPath $BuildErrorLogPath

    $jarPath = Get-BuiltJarPath -RootPath $ProjectRoot
    $backendProcess = Start-BackendProcess `
        -JavaCommand $javaCommand `
        -JarPath $jarPath `
        -RootPath $ProjectRoot `
        -StdoutPath $LogPath `
        -StderrPath $ErrorLogPath

    $readyState = Wait-BackendReady `
        -TargetPort $Port `
        -TimeoutSec $StartupTimeoutSec `
        -StdoutPath $LogPath `
        -SuccessKeyword $StartupSuccessKeyword `
        -BackendPid $backendProcess.Pid `
        -HealthUrl $HealthCheckUrl

    if ($readyState.TimedOut) {
        throw ("Startup timed out after {0} seconds. PortReady={1}, LogReady={2}, HealthReady={3}" -f `
            $StartupTimeoutSec, $readyState.PortReady, $readyState.LogReady, $readyState.HealthReady)
    }

    Write-Step ("Backend is ready. Port={0}, PID={1}" -f $Port, $backendProcess.Pid)
    $healthStatusCode = Get-HealthStatusCode -Url $HealthCheckUrl
    Write-Host ("Health URL: {0}" -f $HealthCheckUrl)
    Write-Host ("Health status code: {0}" -f $healthStatusCode)
    Write-Host "Note: '/' may return 401 because login interceptor is active. Use the health URL above for readiness check."
    Write-Host ("Jar file: {0}" -f $backendProcess.JarPath)
    Write-Host ("Build log: {0}" -f $BuildLogPath)
    Write-Host ("Build error log: {0}" -f $BuildErrorLogPath)
    Write-Host ("Runtime log: {0}" -f $LogPath)
    Write-Host ("Runtime error log: {0}" -f $ErrorLogPath)
    Write-Host "=== restart-backend.ps1 DONE ==="
    exit 0
}
catch {
    Write-Host ""
    Write-Host "=== Backend restart failed ===" -ForegroundColor Red
    Write-Host ("Reason: {0}" -f $_.Exception.Message)
    Write-Host ("Port {0} listening: {1}" -f $Port, (Test-PortListening -TargetPort $Port))

    if ($null -ne $backendProcess -and ($backendProcess.PSObject.Properties.Name -contains "Pid")) {
        Write-Host ("Startup PID: {0}" -f $backendProcess.Pid)
    }

    Write-Host ""
    Write-Host ("--- Tail of {0} ---" -f $BuildLogPath)
    Write-Host (Get-LogTail -Path $BuildLogPath -Tail 30)
    Write-Host ""
    Write-Host ("--- Tail of {0} ---" -f $BuildErrorLogPath)
    Write-Host (Get-LogTail -Path $BuildErrorLogPath -Tail 30)
    Write-Host ""
    Write-Host ("--- Tail of {0} ---" -f $LogPath)
    Write-Host (Get-LogTail -Path $LogPath -Tail 30)
    Write-Host ""
    Write-Host ("--- Tail of {0} ---" -f $ErrorLogPath)
    Write-Host (Get-LogTail -Path $ErrorLogPath -Tail 30)
    exit 1
}
