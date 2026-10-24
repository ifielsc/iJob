# Copyright (c) 2026 ifielsc
# SPDX-License-Identifier: MIT

param([ValidateSet('init','start','stop','restart','status','logs')][string]$Action = 'status')
$ErrorActionPreference = 'Stop'
[Console]::InputEncoding = [Text.UTF8Encoding]::new($false)
[Console]::OutputEncoding = [Text.UTF8Encoding]::new($false)
$OutputEncoding = [Console]::OutputEncoding
$bundle = [IO.Path]::GetFullPath($PSScriptRoot)
$dataRoot = if ($env:IJOB_HOME) { [IO.Path]::GetFullPath($env:IJOB_HOME) } else { Join-Path (Split-Path $bundle) 'ijob-data' }
if ($dataRoot.StartsWith($bundle + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase) -or $dataRoot -eq $bundle) { throw 'IJOB_HOME must be outside the bundle.' }
if ($dataRoot -match '[\r\n"]') { throw 'Unsupported data path.' }
for ($item = $dataRoot; $item; $item = Split-Path -Parent $item) {
    if ((Test-Path -LiteralPath $item) -and ((Get-Item -LiteralPath $item).Attributes -band [IO.FileAttributes]::ReparsePoint)) { throw 'Data paths must not contain links or junctions.' }
}
$java = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin/java.exe' } else { (Get-Command java.exe -ErrorAction Stop).Source }
$pidPath = Join-Path $dataRoot 'native-process.json'
$log = Join-Path $dataRoot 'logs/ijob.log'
$zh = $env:IJOB_LANG -eq 'zh'
function Say([string]$English, [string]$Chinese) { if ($zh) { Write-Host $Chinese } else { Write-Host $English } }
function Get-OwnedProcess {
    if (-not (Test-Path -LiteralPath $pidPath)) { return $null }
    if ((Get-Item -LiteralPath $pidPath).Attributes -band [IO.FileAttributes]::ReparsePoint) { throw 'Unexpected PID file link.' }
    $record = Get-Content -Raw -LiteralPath $pidPath | ConvertFrom-Json
    $process = Get-CimInstance Win32_Process -Filter "ProcessId = $([int]$record.pid)"
    if (-not $process) { return $null }
    if ($process.CreationDate.ToUniversalTime().ToString('o') -ne $record.created -or
        $process.ExecutablePath -ne $record.executable -or
        -not $process.CommandLine.Contains('"-Dijob.native.home=' + $dataRoot + '"')) {
        throw 'PID belongs to a different process; nothing was signalled.'
    }
    return $process
}
function Stop-OwnedProcess {
    $process = Get-OwnedProcess
    if (-not $process) { Say 'Already stopped.' '已经停止。'; return }
    # A separate console helper sends Ctrl+C only to the verified Java console.
    $args = @('-NoProfile','-File',('"' + (Join-Path $bundle 'signal-stop.ps1') + '"'),'-ProcessId',$process.ProcessId,'-Created',('"' + $process.CreationDate.ToUniversalTime().ToString('o') + '"'),'-DataRoot',('"' + $dataRoot + '"'))
    $helper = Start-Process -FilePath (Join-Path $env:WINDIR 'System32/WindowsPowerShell/v1.0/powershell.exe') -ArgumentList $args -WindowStyle Hidden -Wait -PassThru
    if ($helper.ExitCode -ne 0) { throw 'Graceful signal refused or failed; process retained.' }
    for ($attempt = 0; $attempt -lt 120; $attempt++) {
        if (-not (Get-OwnedProcess)) { Say 'Stopped; configuration and data retained.' '已停止，配置和数据已保留。'; return }
        Start-Sleep -Seconds 1
    }
    throw 'Shutdown is still running; no force-kill was sent. Inspect logs.'
}
if ($Action -in @('init','start','restart')) {
    $version = & $java --version | Out-String
    if ($LASTEXITCODE -ne 0 -or $version -notmatch '(?m)^(openjdk|java) 21[. ]') { throw 'Java 21 is required.' }
    & $java '-Dstdout.encoding=UTF-8' '-Dstderr.encoding=UTF-8' -jar (Join-Path $bundle 'ijob-setup-tool-1.0.0.jar') init --mode windows --bundle $bundle --home $dataRoot
    if ($LASTEXITCODE -ne 0) { throw 'Initialization failed; existing files retained.' }
    if ($Action -eq 'init') { exit 0 }
}
if (-not (Test-Path -LiteralPath $dataRoot)) {
    if ($Action -eq 'stop') { Say 'Already stopped.' '已经停止。'; exit 0 }
    throw 'Installation not initialized; run initialize.cmd.'
}
if (-not (Select-String -LiteralPath (Join-Path $dataRoot 'installation.properties') -Pattern '^mode=windows$' -Quiet)) { throw 'This is not a Windows installation.' }
if ($Action -eq 'logs') { Get-Content -LiteralPath $log -Tail 100 -Wait; exit 0 }
$lock = [IO.File]::Open((Join-Path $dataRoot '.native-control.lock'), 'OpenOrCreate', 'ReadWrite', 'None')
try {
    if ($Action -eq 'status') {
        $process = Get-OwnedProcess
        if ($process) { Say "RUNNING (PID $($process.ProcessId)); check logs for readiness." "正在运行（PID $($process.ProcessId)）；就绪状态请查看日志。"; exit 0 }
        Say 'STOPPED' '已停止'; exit 3
    }
    if ($Action -in @('stop','restart')) { Stop-OwnedProcess; if ($Action -eq 'stop') { exit 0 } }
    $existing = Get-OwnedProcess
    if ($existing) { Say "Already running (PID $($existing.ProcessId))." "已经运行（PID $($existing.ProcessId)）。"; exit 0 }
    $vendor = (Select-String -LiteralPath (Join-Path $dataRoot 'installation.properties') -Pattern '^vendor=(sqlite|mysql|postgresql|sqlserver|oracle)$').Matches.Groups[1].Value
    if (-not $vendor) { throw 'Invalid database metadata.' }
    foreach ($name in @('logs','runtime','.native-control.lock')) {
        $path = Join-Path $dataRoot $name
        if ((Test-Path -LiteralPath $path) -and ((Get-Item -LiteralPath $path).Attributes -band [IO.FileAttributes]::ReparsePoint)) { throw 'Unexpected runtime path link.' }
    }
    New-Item -ItemType Directory -Path (Join-Path $dataRoot 'logs') -Force | Out-Null
    $session = [guid]::NewGuid().ToString('N')
    $arguments = @("-Dijob.native.home=$dataRoot",'-jar',(Join-Path $bundle 'ijob-starter-1.0.0.jar'),
        '--spring.profiles.active=release,native-background',
        ('--spring.config.additional-location=' + ([uri](Join-Path $dataRoot 'application.properties')).AbsoluteUri),
        ('--app.web.directory=' + (Join-Path $bundle 'web-dist')),
        ('--logging.config=' + ([uri](Join-Path $bundle 'logging-native.xml')).AbsoluteUri),
        ('--ijob.native.log-directory=' + (Join-Path $dataRoot 'logs')), "--ijob.native.log-session=$session")
    $previousVendor = $env:IJOB_PLATFORM_DB
    try {
        $env:IJOB_PLATFORM_DB = $vendor
        $process = Start-Process -FilePath $java -ArgumentList ($arguments | ForEach-Object { '"' + $_ + '"' }) -WorkingDirectory (Join-Path $dataRoot 'runtime') -WindowStyle Hidden -PassThru
    } finally { $env:IJOB_PLATFORM_DB = $previousVendor }
    $identity = Get-CimInstance Win32_Process -Filter "ProcessId = $($process.Id)"
    if (-not $identity) { throw 'Application exited during launch; inspect logs.' }
    @{pid=$process.Id; created=$identity.CreationDate.ToUniversalTime().ToString('o'); executable=$identity.ExecutablePath} | ConvertTo-Json | Set-Content -LiteralPath $pidPath -Encoding UTF8
    for ($attempt = 0; $attempt -lt 120; $attempt++) {
        if (-not (Get-OwnedProcess)) { throw 'Application exited; inspect logs.' }
        if ((Test-Path -LiteralPath $log) -and (Select-String -LiteralPath $log -SimpleMatch "[launch=$session]" | Where-Object { $_.Line.Contains('runtimePhase=READY') })) {
            Say 'Ready. Open your configured address (default http://127.0.0.1:18080).' '已就绪。打开配置的访问地址（默认 http://127.0.0.1:18080）。'; exit 0
        }
        Start-Sleep -Seconds 1
    }
    throw 'Startup timed out; process retained. Inspect logs or run stop.cmd.'
} finally { $lock.Dispose() }
