# Copyright (c) 2026 ifielsc
# SPDX-License-Identifier: MIT

[CmdletBinding(PositionalBinding=$false)]
param(
    [Parameter(Position=0)][ValidateSet('configure','start','stop','restart','status','logs','show','service')][string]$Action = 'show',
    [Parameter(Position=1)][ValidateSet('install','preview','start','stop','restart','status','logs','enable','disable','uninstall')][string]$ServiceAction,
    [string]$Directory,
    [Alias('Home')][string]$DataHome,
    [ValidateSet('en','zh')][string]$Lang = 'en',
    [string]$Bundle,
    [switch]$Interactive,
    [switch]$Update,
    [string]$ServiceName,
    [string]$JavaHome,
    [string]$Wrapper,
    [string]$OutputDirectory,
    [switch]$Once
)
$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [Text.UTF8Encoding]::new($false)
$OutputEncoding = [Console]::OutputEncoding
[Console]::InputEncoding = [Text.UTF8Encoding]::new($false)
function Absolute-Path([string]$Value) {
    if (-not $Value -or $Value -notmatch '^[A-Za-z]:[\\/]' -or $Value -match '[\r\n"]') { throw 'Use an absolute local drive path without quotes or newlines.' }
    $resolved = [IO.Path]::GetFullPath($Value).TrimEnd('\','/')
    if ($resolved.Length -le 2) { throw 'A drive root cannot be an instance directory.' }
    for ($item = $resolved; $item; $item = Split-Path -Parent $item) {
        if ((Test-Path -LiteralPath $item) -and ((Get-Item -Force -LiteralPath $item).Attributes -band [IO.FileAttributes]::ReparsePoint)) { throw 'Instance paths must not contain links or junctions.' }
    }
    return $resolved
}
function Separate-Paths([string]$First, [string]$Second) {
    if ($First.Equals($Second, [StringComparison]::OrdinalIgnoreCase) -or
        $First.StartsWith($Second + '\', [StringComparison]::OrdinalIgnoreCase) -or
        $Second.StartsWith($First + '\', [StringComparison]::OrdinalIgnoreCase)) { throw 'Program, control and data directories must be separate, non-nested directories.' }
}
function Say([string]$English, [string]$Chinese) { if ($Lang -eq 'zh') { Write-Host $Chinese } else { Write-Host $English } }
function Ask([string]$English, [string]$Chinese, [string]$Default) {
    $label = if ($Lang -eq 'zh') { $Chinese } else { $English }
    Write-Host "$label [$Default] (! = cancel / 取消): " -NoNewline
    $answer = [Console]::ReadLine()
    if ($null -eq $answer -or $answer -eq '!') { throw 'Configuration cancelled; existing settings retained.' }
    if ($answer -eq '') { return $Default }
    return $answer
}
function Read-Settings([string]$Control) {
    $file = Join-Path $Control 'instance.conf'
    $null = Absolute-Path $file
    if (-not (Test-Path -LiteralPath $file -PathType Leaf)) { throw 'Run configure from a complete distribution first.' }
    $values = @{}
    foreach ($line in [IO.File]::ReadAllLines($file, [Text.Encoding]::UTF8)) {
        if (-not $line -or $line.StartsWith('#')) { continue }
        if ($line -notmatch '^(version|bundle|home|lang|mode)=(.+)$') { throw 'Invalid instance.conf line; values are plain text, not shell code.' }
        $key = $Matches[1]; $value = $Matches[2]
        if ($values.ContainsKey($key)) { throw 'Duplicate instance.conf key.' }
        $values[$key] = $value
    }
    if ($values.Count -ne 5 -or $values.version -cne '1' -or $values.mode -cne 'windows' -or $values.lang -cnotin @('en','zh')) { throw 'Invalid or incomplete Windows instance configuration.' }
    return $values
}
function Validate-Bundle([string]$Path) {
    foreach ($name in @('support/manage.ps1','ijob-setup-tool-1.0.0.jar','ijob-starter-1.0.0.jar','ijob.cmd','support/instance.ps1')) {
        $target = Join-Path $Path $name
        $null = Absolute-Path $target
        if (-not (Test-Path -LiteralPath $target -PathType Leaf)) { throw 'Use a complete native distribution with the current launcher layout.' }
    }
}
$controlLock = $null
try {
    if ($Action -ne 'service' -and ($ServiceAction -or $ServiceName -or $JavaHome -or $Wrapper -or $OutputDirectory)) { throw 'Service options require the service action.' }
    if ($Action -eq 'configure') {
        $Lang = $Lang.ToLowerInvariant()
        if ($Once) { throw '-Once is only supported by logs.' }
        if (-not $Interactive -and -not $Update -and -not $Directory -and -not $DataHome) {
            if ([Console]::IsInputRedirected) { throw 'Use explicit configure parameters or -Interactive.' }
            $Interactive = $true
        }
        $currentBundle = Split-Path -Parent $PSScriptRoot
        if ($Update) {
            if ($Directory -or $DataHome) { throw 'Home and control directory are fixed. Create a new control directory for an explicit data migration.' }
            $control = Absolute-Path $currentBundle
            $settings = Read-Settings $control
            $null = Absolute-Path (Join-Path $control '.instance-control.lock')
            $controlLock = [IO.File]::Open((Join-Path $control '.instance-control.lock'), 'OpenOrCreate', 'ReadWrite', 'None')
            $settings = Read-Settings $control
            $dataRoot = Absolute-Path $settings.home
            if ((Test-Path -LiteralPath (Join-Path $control 'service.json')) -or (Test-Path -LiteralPath (Join-Path $dataRoot '.service-owner'))) { throw 'Stop and uninstall service management before updating settings; data is retained.' }
            if (-not $PSBoundParameters.ContainsKey('Lang')) { $Lang = $settings.lang }
            if (-not $Bundle) { $Bundle = $settings.bundle }
        } else {
            if ($Bundle) { throw '-Bundle is only supported by configure -Update.' }
            $Bundle = $currentBundle
        }
        if ($Interactive) {
            $Lang = (Ask 'Prompt language: en or zh' '提示语言：en 或 zh' $Lang).ToLowerInvariant()
            if ($Lang -cnotin @('en','zh')) { throw 'Use language en or zh.' }
            Say 'Only launch settings are saved. Application accounts/database settings are configured on first start.' '这里只保存启动设置；首次启动时另行设置应用账号和数据库。'
            if ($Update) {
                $Bundle = Ask 'Program directory' '程序目录' $Bundle
            } else {
                $parent = Split-Path -Parent $currentBundle
                if (-not $Directory) { $Directory = Join-Path $parent 'ijob-instance' }
                if (-not $DataHome) { $DataHome = Join-Path $parent 'ijob-data' }
                $Directory = Ask 'New control directory' '新控制目录' $Directory
                $DataHome = Ask 'Data directory (existing data is retained)' '数据目录（保留已有数据）' $DataHome
            }
        }
        if (-not $Update) {
            $control = Absolute-Path $Directory
            $dataRoot = Absolute-Path $DataHome
        }
        $bundle = Absolute-Path $Bundle
        Separate-Paths $bundle $control; Separate-Paths $bundle $dataRoot; Separate-Paths $control $dataRoot
        if (-not $Update -and (Test-Path -LiteralPath $control)) { throw 'Control directory already exists; existing configuration was retained.' }
        foreach ($parent in @((Split-Path $control), (Split-Path $dataRoot))) {
            if (-not (Test-Path -LiteralPath $parent -PathType Container)) { throw 'Create the control and data parent directories first.' }
        }
        if ((Test-Path -LiteralPath $dataRoot) -and -not (Test-Path -LiteralPath $dataRoot -PathType Container)) { throw 'Data home must be a directory.' }
        Validate-Bundle $bundle
        if ($Interactive) {
            Write-Host "bundle=$bundle`nhome=$dataRoot`ncontrol=$control`nlang=$Lang`nmode=windows"
            if ((Ask 'Save these settings? Type yes' '保存以上设置？输入 yes' 'no') -cne 'yes') { throw 'Configuration cancelled; existing settings retained.' }
        }
        $content = "version=1`nbundle=$bundle`nhome=$dataRoot`nlang=$Lang`nmode=windows`n"
        if ($Update) {
            if (Test-Path -LiteralPath $dataRoot) {
                $env:IJOB_HOME = $dataRoot; $env:IJOB_LANG = $settings.lang
                $oldBundle = Absolute-Path $settings.bundle
                Separate-Paths $oldBundle $control; Separate-Paths $oldBundle $dataRoot
                $manager = Join-Path $oldBundle 'support/manage.ps1'
                if (-not (Test-Path -LiteralPath $manager -PathType Leaf)) { $manager = Join-Path $oldBundle 'manage.ps1' }
                & powershell.exe -NoProfile -File $manager status
                if ($LASTEXITCODE -ne 3) { throw 'Stop the instance and verify STOPPED before changing launch settings.' }
            }
            $file = Join-Path $control 'instance.conf'
            $backup = $file + '.backup-' + [guid]::NewGuid().ToString('N')
            $temporary = $file + '.new-' + [guid]::NewGuid().ToString('N')
            try {
                [IO.File]::WriteAllText($temporary, $content, [Text.UTF8Encoding]::new($false))
                [IO.File]::Replace($temporary, $file, $backup)
            } finally { if (Test-Path -LiteralPath $temporary) { Remove-Item -LiteralPath $temporary } }
            Say "Updated. Previous settings: $backup" "已更新。原设置备份：$backup"
            exit 0
        }
        $staging = $control + '.new-' + [guid]::NewGuid().ToString('N')
        try {
            $null = New-Item -ItemType Directory -Path $staging
            [IO.File]::WriteAllText((Join-Path $staging 'instance.conf'), $content, [Text.UTF8Encoding]::new($false))
            Copy-Item -LiteralPath (Join-Path $bundle 'ijob.cmd') -Destination $staging
            $support = New-Item -ItemType Directory -Path (Join-Path $staging 'support')
            Copy-Item -LiteralPath (Join-Path $bundle 'support/instance.ps1') -Destination $support.FullName
            [IO.Directory]::Move($staging, $control)
        } finally {
            # Only remove the exact staging directory created by this invocation.
            if ((Test-Path -LiteralPath $staging) -and [IO.Path]::GetFullPath($staging).StartsWith($control + '.new-', [StringComparison]::OrdinalIgnoreCase)) { Remove-Item -LiteralPath $staging -Recurse }
        }
        Say "Configured. Run $control\ijob.cmd start. Application data was not initialized." "配置完成。运行 $control\ijob.cmd start。尚未初始化应用数据。"
        exit 0
    }
    if ($Directory -or $DataHome -or $Bundle -or $Interactive -or $Update -or $PSBoundParameters.ContainsKey('Lang') -or ($Once -and $Action -ne 'logs' -and -not ($Action -eq 'service' -and $ServiceAction -eq 'logs'))) { throw 'Runtime commands read instance.conf; settings options are only for configure.' }
    $control = Absolute-Path (Split-Path -Parent $PSScriptRoot)
    $file = Join-Path $control 'instance.conf'
    if (-not (Test-Path -LiteralPath $file -PathType Leaf) -or ((Get-Item -LiteralPath $file).Attributes -band [IO.FileAttributes]::ReparsePoint)) { throw 'Use ijob.cmd configure -Directory <new-control-directory> -Home <data-directory> -Lang zh or en first.' }
    if ($Action -ne 'logs' -and -not ($Action -eq 'service' -and $ServiceAction -eq 'logs')) {
        $null = Absolute-Path (Join-Path $control '.instance-control.lock')
        $controlLock = [IO.File]::Open((Join-Path $control '.instance-control.lock'), 'OpenOrCreate', 'ReadWrite', 'None')
    }
    $settings = Read-Settings $control
    $bundle = Absolute-Path $settings.bundle; $dataRoot = Absolute-Path $settings.home
    Separate-Paths $bundle $control; Separate-Paths $bundle $dataRoot; Separate-Paths $control $dataRoot
    $Lang = $settings.lang
    if ($Action -eq 'show') { Write-Output "bundle=$bundle`nhome=$dataRoot`nlang=$Lang`nmode=windows"; exit 0 }
    if ($Action -eq 'service' -or (Test-Path -LiteralPath (Join-Path $control 'service.json')) -or (Test-Path -LiteralPath (Join-Path $dataRoot '.service-owner'))) {
        $helper = Join-Path $bundle 'support/service.ps1'
        if (-not (Test-Path -LiteralPath $helper -PathType Leaf)) { throw 'Service helper is missing; use the complete current bundle.' }
        $operation = if ($Action -eq 'service') { if ($ServiceAction) { $ServiceAction } else { 'status' } } else { $Action }
        $arguments = @{Control=$control; Action=$operation; Once=$Once}
        if ($ServiceName) { $arguments.Name = $ServiceName }
        if ($JavaHome) { $arguments.JavaHome = $JavaHome }
        if ($Wrapper) { $arguments.Wrapper = $Wrapper }
        if ($OutputDirectory) { $arguments.OutputDirectory = $OutputDirectory }
        $global:LASTEXITCODE = 0
        & $helper @arguments
        exit $LASTEXITCODE
    }
    # Persisted instance identity deliberately wins over an unrelated terminal's environment.
    $env:IJOB_HOME = $dataRoot; $env:IJOB_LANG = $Lang
    Say "Instance home: $dataRoot" "实例数据目录：$dataRoot"
    if ($Action -eq 'logs' -and $Once) {
        foreach ($name in @('logs','logs/ijob.log')) { $null = Absolute-Path (Join-Path $dataRoot $name) }
        Get-Content -LiteralPath (Join-Path $dataRoot 'logs/ijob.log') -Encoding UTF8 -Tail 200
        exit 0
    }
    $manager = Join-Path $bundle 'support/manage.ps1'
    if (-not (Test-Path -LiteralPath $manager -PathType Leaf)) { $manager = Join-Path $bundle 'manage.ps1' }
    if (-not (Test-Path -LiteralPath $manager -PathType Leaf)) { throw 'Configured distribution is missing manage.ps1.' }
    & powershell.exe -NoProfile -File $manager $Action
    exit $LASTEXITCODE
} catch {
    Write-Error $_.Exception.Message
    exit 1
} finally { if ($controlLock) { $controlLock.Dispose() } }
