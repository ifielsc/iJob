# Copyright (c) 2026 ifielsc
# SPDX-License-Identifier: MIT

param(
    [ValidateSet('configure','start','stop','restart','status','logs','show')][string]$Action = 'show',
    [string]$Directory,
    [Alias('Home')][string]$DataHome,
    [ValidateSet('en','zh')][string]$Lang = 'en',
    [switch]$Once
)
$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [Text.UTF8Encoding]::new($false)
$OutputEncoding = [Console]::OutputEncoding
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
try {
    if ($Action -eq 'configure') {
        if ($Once) { throw '-Once is only supported by logs.' }
        $control = Absolute-Path $Directory
        $dataRoot = Absolute-Path $DataHome
        $bundle = Absolute-Path $PSScriptRoot
        Separate-Paths $bundle $control; Separate-Paths $bundle $dataRoot; Separate-Paths $control $dataRoot
        if (Test-Path -LiteralPath $control) { throw 'Control directory already exists; existing configuration was retained.' }
        foreach ($parent in @((Split-Path $control), (Split-Path $dataRoot))) {
            if (-not (Test-Path -LiteralPath $parent -PathType Container)) { throw 'Create the control and data parent directories first.' }
        }
        foreach ($name in @('manage.ps1','ijob-setup-tool-1.0.0.jar','ijob-starter-1.0.0.jar','ijob.cmd')) {
            if (-not (Test-Path -LiteralPath (Join-Path $bundle $name) -PathType Leaf)) { throw 'Configure from a complete native distribution.' }
        }
        New-Item -ItemType Directory -Path $control | Out-Null
        [IO.File]::WriteAllText((Join-Path $control 'instance.conf'), "version=1`nbundle=$bundle`nhome=$dataRoot`nlang=$Lang`nmode=windows`n", [Text.UTF8Encoding]::new($false))
        Copy-Item -LiteralPath (Join-Path $bundle 'ijob.cmd'), (Join-Path $bundle 'instance.ps1') -Destination $control
        Say "Configured. Run $control\ijob.cmd start. Application data was not initialized." "配置完成。运行 $control\ijob.cmd start。尚未初始化应用数据。"
        exit 0
    }
    if ($Directory -or $DataHome -or $PSBoundParameters.ContainsKey('Lang') -or ($Once -and $Action -ne 'logs')) { throw 'Runtime commands read instance.conf; directory/language options are only for configure.' }
    $control = Absolute-Path $PSScriptRoot
    $file = Join-Path $control 'instance.conf'
    if (-not (Test-Path -LiteralPath $file -PathType Leaf) -or ((Get-Item -LiteralPath $file).Attributes -band [IO.FileAttributes]::ReparsePoint)) { throw 'Use ijob.cmd configure -Directory <new-control-directory> -Home <data-directory> -Lang zh or en first.' }
    $settings = @{}
    foreach ($line in [IO.File]::ReadAllLines($file, [Text.Encoding]::UTF8)) {
        if (-not $line -or $line.StartsWith('#')) { continue }
        if ($line -notmatch '^(version|bundle|home|lang|mode)=(.+)$') { throw 'Invalid instance.conf line; values are plain text, not shell code.' }
        $key = $Matches[1]; $value = $Matches[2]
        if ($settings.ContainsKey($key)) { throw 'Duplicate instance.conf key.' }
        $settings[$key] = $value
    }
    if ($settings.Count -ne 5 -or $settings.version -cne '1' -or $settings.mode -cne 'windows' -or $settings.lang -cnotin @('en','zh')) { throw 'Invalid or incomplete Windows instance configuration.' }
    $bundle = Absolute-Path $settings.bundle; $dataRoot = Absolute-Path $settings.home
    Separate-Paths $bundle $control; Separate-Paths $bundle $dataRoot; Separate-Paths $control $dataRoot
    $Lang = $settings.lang
    if ($Action -eq 'show') { Write-Output "bundle=$bundle`nhome=$dataRoot`nlang=$Lang`nmode=windows"; exit 0 }
    # Persisted instance identity deliberately wins over an unrelated terminal's environment.
    $env:IJOB_HOME = $dataRoot; $env:IJOB_LANG = $Lang
    Say "Instance home: $dataRoot" "实例数据目录：$dataRoot"
    if ($Action -eq 'logs' -and $Once) {
        foreach ($name in @('logs','logs/ijob.log')) { $null = Absolute-Path (Join-Path $dataRoot $name) }
        Get-Content -LiteralPath (Join-Path $dataRoot 'logs/ijob.log') -Encoding UTF8 -Tail 200
        exit 0
    }
    $manager = Join-Path $bundle 'manage.ps1'
    if (-not (Test-Path -LiteralPath $manager -PathType Leaf)) { throw 'Configured distribution is missing manage.ps1.' }
    & powershell.exe -NoProfile -File $manager $Action
    exit $LASTEXITCODE
} catch {
    Write-Error $_.Exception.Message
    exit 1
}
