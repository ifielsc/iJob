# Copyright (c) 2026 ifielsc
# SPDX-License-Identifier: MIT

param(
    [Parameter(Mandatory=$true)][string]$Control,
    [ValidateSet('install','preview','start','stop','restart','status','logs','enable','disable','uninstall')][string]$Action = 'status',
    [string]$Name,
    [string]$JavaHome,
    [string]$Wrapper,
    [string]$OutputDirectory,
    [switch]$Once
)
$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [Text.UTF8Encoding]::new($false)
. (Join-Path $PSScriptRoot 'service-common.ps1')
$lock = $null; $nativeLock = $null
function Say([string]$English, [string]$Chinese) { if ($settings.lang -eq 'zh') { Write-Host $Chinese } else { Write-Host $English } }
function Invoke-Wrapper([string]$Operation) {
    & $wrapperPath $Operation
    if ($LASTEXITCODE -ne 0) { throw "Service wrapper $Operation failed; preserve registration and inspect logs." }
}
function Find-Service([string]$ServiceName) { Get-CimInstance Win32_Service -Filter "Name='$ServiceName'" }
try {
    $Control = Get-ServicePath $Control
    $settings = Read-ServiceInstance $Control
    $dataRoot = $settings.home; $bundle = $settings.bundle
    if ($Once -and $Action -ne 'logs') { throw '-Once is only supported by logs.' }
    if ($Action -notin @('install','preview') -and ($Name -or $JavaHome -or $Wrapper -or $OutputDirectory)) { throw 'Service runtime commands use their saved registration.' }
    if ($Action -ne 'preview') {
        $principal = New-Object Security.Principal.WindowsPrincipal([Security.Principal.WindowsIdentity]::GetCurrent())
        if (-not $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) { throw 'Run service management from an elevated terminal; the application uses its own virtual service account.' }
        if ($Action -ne 'logs') { $lock = [IO.File]::Open((Get-ServicePath (Join-Path $Control '.service-control.lock')), 'OpenOrCreate', 'ReadWrite', 'None') }
    }
    $registration = Join-Path $Control 'service.json'
    $marker = Get-ServicePath (Join-Path $dataRoot '.service-owner')
    $serviceRoot = Get-ServicePath (Join-Path $Control 'service')
    if ($Action -in @('install','preview')) {
        if ($Name -cnotmatch '^ijob[A-Za-z0-9]{1,40}$') { throw 'Use a unique service name: ijob followed by 1-40 letters or digits.' }
        $JavaHome = Get-ServicePath $JavaHome
        $java = Get-ServicePath (Join-Path $JavaHome 'bin/java.exe')
        $version = & $java --version | Out-String
        if ($LASTEXITCODE -ne 0 -or $version -notmatch '(?m)^(openjdk|java) 21[. ]') { throw 'Java 21 is required.' }
        foreach ($file in @('application.properties','installation.properties','setup.complete')) {
            if (-not (Test-Path -LiteralPath (Get-ServicePath (Join-Path $dataRoot $file)) -PathType Leaf)) { throw 'Initialize this instance and stop it before service installation.' }
        }
        if (-not (Select-String -LiteralPath (Join-Path $dataRoot 'installation.properties') -Pattern '^mode=windows$' -Quiet)) { throw 'Not a Windows native installation.' }
        $vendors = @(Select-String -LiteralPath (Join-Path $dataRoot 'installation.properties') -Pattern '^vendor=(sqlite|mysql|postgresql|sqlserver|oracle)$')
        if ($vendors.Count -ne 1) { throw 'Invalid database metadata.' }
        $vendor = $vendors[0].Matches.Groups[1].Value
        foreach ($file in @('ijob-starter-1.0.0.jar','support/logging-native.xml')) {
            if (-not (Test-Path -LiteralPath (Get-ServicePath (Join-Path $bundle $file)) -PathType Leaf)) { throw 'Use a complete native bundle.' }
        }
        $xml = New-ServiceXml $Name $java $dataRoot $bundle $vendor $settings.lang
        if ($Action -eq 'preview') {
            $OutputDirectory = Get-ServicePath $OutputDirectory
            if (Test-Path -LiteralPath $OutputDirectory) { throw 'Preview directory already exists.' }
            $null = New-Item -ItemType Directory -Path $OutputDirectory
            [IO.File]::WriteAllText((Join-Path $OutputDirectory "$Name.xml"), $xml, [Text.UTF8Encoding]::new($false))
            Say 'Preview generated; no service was installed.' '已生成预览；未安装服务。'
            exit 0
        }
        if ($OutputDirectory) { throw '-OutputDirectory is only supported by preview.' }
        foreach ($path in @($registration,$marker,$serviceRoot)) { if (Test-Path -LiteralPath $path) { throw 'Service ownership already exists; inspect or uninstall it first.' } }
        if (Find-Service $Name) { throw 'Service name is already in use.' }
        $Wrapper = Get-ServicePath $Wrapper
        $dependency = Get-Content -Raw -LiteralPath (Join-Path $PSScriptRoot 'winsw.json') | ConvertFrom-Json
        if ((Get-FileHash -LiteralPath $Wrapper -Algorithm SHA256).Hash.ToLowerInvariant() -cne $dependency.sha256) { throw 'Wrapper hash differs from the pinned official WinSW dependency.' }
        $nativeLock = [IO.File]::Open((Get-ServicePath (Join-Path $dataRoot '.native-control.lock')), 'OpenOrCreate', 'ReadWrite', 'None')
        $pidFile = Get-ServicePath (Join-Path $dataRoot 'native-process.json')
        if (Test-Path -LiteralPath $pidFile) {
            $pidRecord = Get-Content -Raw -LiteralPath $pidFile | ConvertFrom-Json
            if (-not $pidRecord.pid -or (Get-CimInstance Win32_Process -Filter "ProcessId = $([int]$pidRecord.pid)")) { throw 'Stop the native instance and inspect its PID record before installing a service.' }
        }
        $null = New-Item -ItemType Directory -Path $serviceRoot
        $wrapperPath = Join-Path $serviceRoot "$Name.exe"
        $xmlPath = Join-Path $serviceRoot "$Name.xml"
        Copy-Item -LiteralPath $Wrapper -Destination $wrapperPath
        Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'WinSW-LICENSE.txt') -Destination $serviceRoot
        [IO.File]::WriteAllText($xmlPath, $xml, [Text.UTF8Encoding]::new($false))
        # Retain ownership on partial installation failure so unmanaged launchers fail closed.
        [IO.File]::WriteAllText($marker, "$Control|$Name`n", [Text.UTF8Encoding]::new($false))
        $record = @{version=1; name=$Name; control=$Control; bundle=$bundle; home=$dataRoot; java=$java; wrapper_sha256=$dependency.sha256; xml_sha256=(Get-FileHash -LiteralPath $xmlPath -Algorithm SHA256).Hash.ToLowerInvariant()}
        $record | ConvertTo-Json | Set-Content -LiteralPath $registration -Encoding UTF8
        Invoke-Wrapper 'install'
        $account = "NT SERVICE\$Name"
        Grant-ServiceAccess $Control $dataRoot $account
        Say "Installed $Name, stopped, autostart disabled. Use service enable and start when ready." "已安装 $Name，当前停止且未启用自启。准备好后使用 service enable 和 start。"
        exit 0
    }
    $null = Get-ServicePath $registration
    $record = Get-Content -Raw -LiteralPath $registration | ConvertFrom-Json
    if ($record.version -ne 1 -or $record.name -cnotmatch '^ijob[A-Za-z0-9]{1,40}$' -or $record.control -cne $Control -or $record.bundle -cne $bundle -or $record.home -cne $dataRoot) { throw 'Service registration differs from this instance; restore settings or uninstall before upgrading.' }
    $Name = $record.name
    if ([IO.File]::ReadAllText($marker).TrimEnd("`r","`n") -cne "$Control|$Name") { throw 'Service ownership does not match this control directory.' }
    $wrapperPath = Get-ServicePath (Join-Path $serviceRoot "$Name.exe")
    $xmlPath = Get-ServicePath (Join-Path $serviceRoot "$Name.xml")
    if ((Get-FileHash -LiteralPath $wrapperPath -Algorithm SHA256).Hash.ToLowerInvariant() -cne $record.wrapper_sha256 -or (Get-FileHash -LiteralPath $xmlPath -Algorithm SHA256).Hash.ToLowerInvariant() -cne $record.xml_sha256) { throw 'Registered service files changed; inspect them before management.' }
    $owned = Find-Service $Name
    if (-not $owned -or $owned.PathName.Trim('"') -ine $wrapperPath -or $owned.StartName -ine "NT SERVICE\$Name") { throw 'Windows service identity does not match this registration.' }
    switch ($Action) {
        'start' { Invoke-Wrapper 'start'; Say 'Service started; verify runtimePhase=READY in application logs.' '服务已启动；请在应用日志中确认 runtimePhase=READY。' }
        'stop' { Invoke-Wrapper 'stop'; Wait-ServiceStopped $Name }
        'restart' { Invoke-Wrapper 'stop'; Wait-ServiceStopped $Name; Invoke-Wrapper 'start' }
        'status' {
            $owned | Select-Object Name,State,StartMode,ProcessId,StartName | Format-List
            if ($owned.State -eq 'Running') { exit 0 }; exit 3
        }
        'enable' { Set-Service -Name $Name -StartupType Automatic }
        'disable' { Set-Service -Name $Name -StartupType Manual }
        'logs' {
            $log = Get-ServicePath (Join-Path $dataRoot 'logs/ijob.log')
            if ($Once) { Get-Content -LiteralPath $log -Encoding UTF8 -Tail 200 } else { Get-Content -LiteralPath $log -Encoding UTF8 -Tail 200 -Wait }
        }
        'uninstall' {
            if ($owned.State -ne 'Stopped') { throw 'Stop the service before uninstalling.' }
            Invoke-Wrapper 'uninstall'
            $archive = Join-Path $Control ('service-uninstalled-' + [guid]::NewGuid().ToString('N'))
            $null = New-Item -ItemType Directory -Path $archive
            Copy-Item -LiteralPath $registration,$xmlPath -Destination $archive
            $destination = Get-ServicePath (Join-Path $archive 'service')
            if (-not $serviceRoot.StartsWith($Control + '\', [StringComparison]::OrdinalIgnoreCase) -or -not $destination.StartsWith($Control + '\', [StringComparison]::OrdinalIgnoreCase)) { throw 'Service archive paths escaped the control directory.' }
            Move-Item -LiteralPath $serviceRoot -Destination $destination
            Remove-Item -LiteralPath $registration,$marker
            Say "Uninstalled. Data, ACL grants and settings retained. Registration backup: $archive" "已卸载。数据、访问授权和设置保留。服务登记备份：$archive"
        }
    }
    exit 0
} catch { Write-Error $_.Exception.Message; exit 1 }
finally { if ($nativeLock) { $nativeLock.Dispose() }; if ($lock) { $lock.Dispose() } }
