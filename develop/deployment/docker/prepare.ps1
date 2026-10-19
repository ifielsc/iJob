# Copyright (c) 2026 ifielsc
# SPDX-License-Identifier: MIT

param(
    [Parameter(Mandatory)][ValidateSet('mysql', 'postgresql', 'sqlserver', 'oracle', 'sqlite')][string]$Database,
    [Parameter(Mandatory)][string]$ConfigPath,
    [switch]$Cluster,
    [switch]$Build
)

$ErrorActionPreference = 'Stop'
if ($Cluster -and $Database -eq 'sqlite') { throw 'SQLite requires STANDALONE mode.' }
$resolvedConfig = (Resolve-Path -LiteralPath $ConfigPath).Path
$developRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
if ($resolvedConfig.StartsWith($developRoot + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) {
    throw 'Keep private configuration outside the source/build contexts.'
}
$images = Get-Content -LiteralPath (Join-Path $PSScriptRoot 'images.lock.json') -Raw | ConvertFrom-Json
foreach ($entry in $images.PSObject.Properties) {
    if ($entry.Value.image -notmatch '@sha256:[0-9a-f]{64}$') { throw 'Image digest is not pinned.' }
}
$docker = Get-Command docker -ErrorAction SilentlyContinue
if (-not $docker) { throw 'Docker CLI is unavailable; no container validation or build was performed.' }
$env:IJOB_PLATFORM_DB = $Database
$env:IJOB_CONFIG_PATH = $resolvedConfig
$composeArgs = @('compose', '--env-file', (Join-Path $PSScriptRoot 'images.env'), '-f', (Join-Path $PSScriptRoot 'compose.yaml'))
if ($Cluster) { $composeArgs += @('-f', (Join-Path $PSScriptRoot 'compose.cluster.yaml')) }
& $docker.Source @composeArgs config --quiet
if ($LASTEXITCODE -ne 0) { throw 'Compose configuration validation failed.' }
if ($Build) {
    & $docker.Source @composeArgs build --pull
    if ($LASTEXITCODE -ne 0) { throw 'Image build failed.' }
}
Write-Output 'Preparation completed. No containers were started and no images were pushed.'
