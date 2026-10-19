param(
    [Parameter(Mandatory = $true)][ValidatePattern('^[a-zA-Z0-9-]+$')][string]$RunId,
    [ValidateSet('Basic', 'Full')][string]$Suite = 'Basic',
    [ValidatePattern('^[1-9][0-9]*/[1-9][0-9]*$')][string]$Shard,
    [ValidateRange(0, 64)][int]$BrowserWorkers = 0,
    [string]$ReportRoot,
    [string]$TemporaryRoot
)
$ErrorActionPreference = 'Stop'
$projectRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$webRoot = Join-Path $projectRoot 'develop/web/ijob'
$reportRoot = if ($ReportRoot) { [IO.Path]::GetFullPath($ReportRoot) } else { Join-Path $projectRoot "test/reports/$RunId" }
if (Test-Path -LiteralPath $reportRoot) { throw 'Evidence directory already exists; choose a new RunId.' }
if ($Suite -eq 'Full' -and [IO.Path]::DirectorySeparatorChar -ne '\') { throw 'Full includes Windows BAT/PowerShell integration; use a Windows worker.' }
if ($Suite -eq 'Full') {
    $allowed = @('IJOB_TEST_JAVA_HOME', 'IJOB_TEST_PYTHON', 'IJOB_TEST_SH', 'IJOB_TEST_PG_BIN',
        'IJOB_TEST_BACKEND_JAR', 'IJOB_TEST_PLUGIN_SPI_JAR', 'IJOB_TEST_CRYPTO_JAR', 'IJOB_TEST_PASSWORD')
    $unsafe = @(Get-ChildItem Env: | Where-Object {
        ($_.Name -like 'IJOB_*' -and $_.Name -notin $allowed) -or
        $_.Name -match '^(SPRING_|JAVA_TOOL_OPTIONS$|JDK_JAVA_OPTIONS$|_JAVA_OPTIONS$|MAVEN_OPTS$|MAVEN_ARGS$)'
    })
    if ($unsafe.Count) { throw "Remove inherited configuration variables: $($unsafe.Name -join ', ')" }
    foreach ($name in @('IJOB_TEST_PYTHON', 'IJOB_TEST_SH', 'IJOB_TEST_PG_BIN')) {
        $value = [Environment]::GetEnvironmentVariable($name)
        if (-not $value -or -not (Test-Path -LiteralPath $value)) { throw "Set $name to the isolated test tool path." }
    }
    foreach ($binary in @('initdb.exe', 'pg_ctl.exe', 'psql.exe')) {
        if (-not (Test-Path -LiteralPath (Join-Path $env:IJOB_TEST_PG_BIN $binary))) { throw "Missing PostgreSQL fixture tool: $binary" }
    }
    $busy = @(Get-NetTCPConnection -State Listen -ErrorAction Stop | Where-Object LocalPort -In 15438,18087,5176)
    if ($busy.Count) { throw 'Full integration ports are occupied; do not reuse or stop existing services.' }
}
if ($Shard) {
    $parts = $Shard.Split('/')
    if ($Suite -ne 'Basic' -or [int]$parts[0] -gt [int]$parts[1]) { throw 'Sharding requires Basic and an index no greater than the total.' }
}
New-Item -ItemType Directory -Path $reportRoot | Out-Null
@{ suite = $Suite; shard = $Shard; browserWorkers = $BrowserWorkers } | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $reportRoot 'selection.json')
Push-Location $webRoot
$oldHtml = $env:PLAYWRIGHT_HTML_OUTPUT_DIR
$oldJunit = $env:PLAYWRIGHT_JUNIT_OUTPUT_FILE
$oldTemp = $env:TEMP; $oldTmp = $env:TMP; $oldTmpDir = $env:TMPDIR
try {
    if ($Suite -eq 'Full') {
        $temporary = if ($TemporaryRoot) { [IO.Path]::GetFullPath($TemporaryRoot) } else { Join-Path ([IO.Path]::GetTempPath()) ('ijob-frontend-full-' + [guid]::NewGuid().ToString('N')) }
        if (Test-Path -LiteralPath $temporary) { throw 'Use a new temporary directory.' }
        New-Item -ItemType Directory -Path $temporary | Out-Null
        # Python prefers TMPDIR, while Node on Windows prefers TEMP. Keep the guards aligned.
        $env:TEMP = $temporary; $env:TMP = $temporary; $env:TMPDIR = $temporary
        @{ temporaryRoot=$temporary; retained=$true } | ConvertTo-Json |
            Set-Content -LiteralPath (Join-Path $reportRoot 'isolation.json')
    }
    $expectedPnpm = (Get-Content package.json -Raw | ConvertFrom-Json).packageManager -replace '^pnpm@', ''
    $actualPnpm = & pnpm --version
    if ($LASTEXITCODE -ne 0 -or $actualPnpm.Trim() -ne $expectedPnpm) { throw "Use pnpm $expectedPnpm" }
    $commands = @(
        @{ Name = 'runtime'; Args = @('check:runtime') },
        @{ Name = 'typecheck'; Args = @('typecheck') },
        @{ Name = 'lint'; Args = @('lint') },
        @{ Name = 'boundaries'; Args = @('check:boundaries') },
        @{ Name = 'format'; Args = @('format:check') },
        @{ Name = 'unit'; Args = @('test', '--reporter=default', '--reporter=junit', "--outputFile=$(Join-Path $reportRoot 'unit-junit.xml')") },
        @{ Name = 'build'; Args = @('build') },
        @{ Name = 'bundle'; Args = @('check:bundle') }
    )
    foreach ($command in $commands) {
        $commandArgs = $command.Args
        & pnpm @commandArgs 2>&1 | Tee-Object -FilePath (Join-Path $reportRoot "$($command.Name).log")
        if ($LASTEXITCODE -ne 0) { throw "$($command.Name) failed: $LASTEXITCODE" }
    }
    $browsers = @(
        @{ Name = 'browser'; Config = 'playwright.config.ts' },
        @{ Name = 'production'; Config = 'playwright.production.config.ts' }
    )
    if ($Suite -eq 'Full') {
        $browsers += @{ Name = 'security-integration'; Config = 'playwright.integration.config.ts' }
        foreach ($name in @('batch-etl','diagnostics','etl','localjar','monitor','workbench')) {
            $browsers += @{ Name = "$name-integration"; Config = "playwright.$name-integration.config.ts" }
        }
    }
    foreach ($browser in $browsers) {
        $name = $browser.Name
        $env:PLAYWRIGHT_HTML_OUTPUT_DIR = Join-Path $reportRoot "$name-html"
        $env:PLAYWRIGHT_JUNIT_OUTPUT_FILE = Join-Path $reportRoot "$name-junit.xml"
        # Configs start their own servers; execute sequentially to avoid port collisions.
        $browserArgs = @('exec', 'playwright', 'test', '--config', $browser.Config, '--reporter=list,junit,html', '--output', (Join-Path $reportRoot $name))
        if ($Shard) { $browserArgs += @('--shard', $Shard) }
        if ($BrowserWorkers) { $browserArgs += @('--workers', $BrowserWorkers) }
        & pnpm @browserArgs 2>&1 | Tee-Object -FilePath (Join-Path $reportRoot "$name.log")
        if ($LASTEXITCODE -ne 0) { throw "$name failed: $LASTEXITCODE" }
        [xml]$junit = Get-Content -Raw -LiteralPath $env:PLAYWRIGHT_JUNIT_OUTPUT_FILE
        if ([int]$junit.testsuites.tests -le [int]$junit.testsuites.skipped) { throw "No active browser tests: $name" }
        if ($Suite -eq 'Full' -and [int]$junit.testsuites.skipped -ne 0) { throw "Full suite must not skip tests: $name" }
    }
    Write-Output "Frontend $Suite verification passed: $reportRoot"
} finally {
    $env:PLAYWRIGHT_HTML_OUTPUT_DIR = $oldHtml
    $env:PLAYWRIGHT_JUNIT_OUTPUT_FILE = $oldJunit
    if ($Suite -eq 'Full') { $env:TEMP = $oldTemp; $env:TMP = $oldTmp; $env:TMPDIR = $oldTmpDir }
    $records = Get-ChildItem -LiteralPath $reportRoot -Recurse -File | ForEach-Object {
        [pscustomobject]@{ Path = [IO.Path]::GetRelativePath($reportRoot, $_.FullName); SHA256 = (Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash }
    }
    $records | Export-Csv -LiteralPath (Join-Path $reportRoot 'evidence-sha256.csv') -Encoding utf8 -NoTypeInformation
    Pop-Location
}
