# Copyright (c) 2026 ifielsc
# SPDX-License-Identifier: MIT

param(
    [switch]$Check,
    [string]$FormatterJar = (Join-Path ([Environment]::GetFolderPath('UserProfile')) '.m2/repository/com/google/googlejavaformat/google-java-format/1.24.0/google-java-format-1.24.0-all-deps.jar'),
    [string]$JavaExecutable = (Join-Path $env:JAVA_HOME $(if ([IO.Path]::DirectorySeparatorChar -eq '\') { 'bin/java.exe' } else { 'bin/java' }))
)

$ErrorActionPreference = 'Stop'
$projectDirectory = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
if (-not (Test-Path -LiteralPath $FormatterJar)) {
    throw 'Missing google-java-format 1.24.0 all-deps JAR. See document/reports/single-instance-conventions.md.'
}
if (-not (Test-Path -LiteralPath $JavaExecutable)) {
    throw 'Set JAVA_HOME to a JDK 21 installation or pass -JavaExecutable.'
}
$sourceRoots = @(Get-ChildItem -LiteralPath $projectDirectory -Directory -Filter 'ijob-*' |
    ForEach-Object { Join-Path $_.FullName 'src' } |
    Where-Object { Test-Path -LiteralPath $_ })
$sourceRoots += Join-Path $projectDirectory '../ijob-monitor-agent/src'
$sourceFiles = @($sourceRoots |
    ForEach-Object { Get-ChildItem -LiteralPath $_ -Recurse -File -Filter '*.java' } |
    Sort-Object FullName | ForEach-Object { $_.FullName })
$formatOptions = @('--aosp')
if ($Check) { $formatOptions += @('--dry-run', '--set-exit-if-changed') }
else { $formatOptions += '--replace' }
$failed = $false
for ($offset = 0; $offset -lt $sourceFiles.Count; $offset += 30) {
    $end = [Math]::Min($offset + 29, $sourceFiles.Count - 1)
    & $JavaExecutable -jar $FormatterJar @formatOptions $sourceFiles[$offset..$end]
    if ($LASTEXITCODE -ne 0) { $failed = $true }
}
if ($failed) { throw 'Java formatting check failed.' }
$lineEndingFailures = @()
foreach ($sourceFile in $sourceFiles) {
    $content = [IO.File]::ReadAllText($sourceFile)
    $normalized = [regex]::Replace($content, '\r\n|\r|\n', "`r`n").TrimEnd("`r", "`n") + "`r`n"
    if ($content -cne $normalized) {
        if ($Check) { $lineEndingFailures += $sourceFile }
        else { [IO.File]::WriteAllText($sourceFile, $normalized, [Text.UTF8Encoding]::new($false)) }
    }
}
if ($lineEndingFailures.Count -gt 0) { throw "Java CRLF/end-of-file check failed: $($lineEndingFailures -join ', ')" }
Write-Output "Java formatting completed: $($sourceFiles.Count) files; check=$Check"
