param(
    [string]$OutputDir = 'benchmarks\mysql-artifacts',
    [string]$Prefix = (Get-Date -Format 'yyyyMMdd-HHmmss'),
    [string]$DbPassword = '123456',
    [string]$MySqlPath = 'C:\Program Files\MySQL\MySQL Server 8.4\bin\mysql.exe',
    [string]$SchemaName = 'hmdp'
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$repoRoot = Split-Path -Parent $PSScriptRoot
Push-Location $repoRoot
try {
    function Write-Log([string]$Message) {
        $ts = Get-Date -Format 'yyyy-MM-dd HH:mm:ss'
        Write-Host "[$ts] $Message"
    }

    function ConvertTo-PlainJsonValue($Value) {
        if ($null -eq $Value) { return $null }
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

    function Invoke-MySqlRaw([string]$Sql, [string]$OutputPath) {
        $oldPwd = $env:MYSQL_PWD
        try {
            $env:MYSQL_PWD = $DbPassword
            & $MySqlPath -uroot -D $SchemaName -e $Sql *> $OutputPath
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

    if (-not (Test-Path -LiteralPath $MySqlPath)) {
        throw "mysql.exe not found: $MySqlPath"
    }

    $resolvedOutputDir = Join-Path $repoRoot $OutputDir
    New-Item -ItemType Directory -Force -Path $resolvedOutputDir | Out-Null

    $processlistFile = Join-Path $resolvedOutputDir ($Prefix + '-mysql-processlist.txt')
    $innodbStatusFile = Join-Path $resolvedOutputDir ($Prefix + '-mysql-innodb-status.txt')
    $digestFile = Join-Path $resolvedOutputDir ($Prefix + '-mysql-digest.txt')
    $manifestFile = Join-Path $resolvedOutputDir ($Prefix + '-mysql-artifacts.json')

    Write-Log "Capture processlist -> $processlistFile"
    Invoke-MySqlRaw 'SHOW FULL PROCESSLIST;' $processlistFile

    Write-Log "Capture innodb status -> $innodbStatusFile"
    Invoke-MySqlRaw 'SHOW ENGINE INNODB STATUS\G' $innodbStatusFile

    Write-Log "Capture top digest -> $digestFile"
    Invoke-MySqlRaw @"
SELECT DIGEST_TEXT,
       COUNT_STAR,
       ROUND(SUM_TIMER_WAIT / 1000000000000, 3) AS total_sec,
       ROUND(AVG_TIMER_WAIT / 1000000000, 3) AS avg_ms,
       ROUND(MAX_TIMER_WAIT / 1000000000, 3) AS max_ms
FROM performance_schema.events_statements_summary_by_digest
WHERE SCHEMA_NAME = '$SchemaName'
ORDER BY SUM_TIMER_WAIT DESC
LIMIT 10;
"@ $digestFile

    $manifest = [ordered]@{
        generated_at = (Get-Date).ToString('yyyy-MM-dd HH:mm:ss')
        output_dir = $resolvedOutputDir
        prefix = $Prefix
        schema_name = $SchemaName
        mysql_path = $MySqlPath
        files = [ordered]@{
            processlist = $processlistFile
            innodb_status = $innodbStatusFile
            digest = $digestFile
        }
    }
    Save-JsonFile -Path $manifestFile -Record $manifest

    Write-Host "MYSQL_ARTIFACT_MANIFEST=$manifestFile"
}
finally {
    Pop-Location
}
