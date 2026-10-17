param([Parameter(Mandatory=$true)][ValidatePattern('^[A-Za-z0-9-]+$')][string]$RunId)
$ErrorActionPreference = 'Stop'
$workspace = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$project = Join-Path $workspace 'develop/api/ijob'
$evidence = Join-Path $workspace "test/reports/$RunId"
if (Test-Path -LiteralPath $evidence) { throw 'Use a new evidence RunId.' }
# Fail before Maven can inherit a database target or application configuration.
$allowed = @('IJOB_TEST_JAVA_HOME', 'IJOB_TEST_PYTHON', 'IJOB_TEST_SH', 'IJOB_TEST_PG_BIN')
$unsafe = @(Get-ChildItem Env: | Where-Object {
    ($_.Name -like 'IJOB_*' -and $_.Name -notin $allowed) -or
    $_.Name -match '^(SPRING_|JAVA_TOOL_OPTIONS$|JDK_JAVA_OPTIONS$|_JAVA_OPTIONS$|MAVEN_OPTS$|MAVEN_ARGS$)'
})
if ($unsafe.Count) { throw "Remove inherited configuration variables: $($unsafe.Name -join ', ')" }
$version = & mvn --version 2>&1
if ($LASTEXITCODE -ne 0 -or ($version -join "`n") -notmatch 'Apache Maven 3\.9\.11\b' -or
    ($version -join "`n") -notmatch 'Java version: 21[.,]') { throw 'Use Maven 3.9.11 and JDK 21.' }
$required = @(
    'ConcurrencyHttpIT', 'DataCheckHttpIT', 'DiagnosticsHttpIT', 'EtlFileIncrementalIT',
    'FilesHttpIT', 'InstanceLogSnapshotIT', 'PushHttpIT', 'SecurityCompletionIT',
    'Stage9cLifecycleHttpIT', 'TableIdMigrationIT', 'TriggerBusinessTimeMigrationIT',
    'UnifiedExperienceHttpIT', 'WorkbenchHttpIT'
)
New-Item -ItemType Directory -Path $evidence | Out-Null
$version | Set-Content -LiteralPath (Join-Path $evidence 'toolchain.log')
$temporary = Join-Path ([IO.Path]::GetTempPath()) ('ijob-local-it-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $temporary | Out-Null
$oldTemp = $env:TEMP; $oldTmp = $env:TMP; $oldTmpDir = $env:TMPDIR
$summaries = @()
$started = [datetime]::UtcNow
$exitCode = $null
@{ suites=$required; vendor='sqlite'; temporaryDirectory=$temporary; startedUtc=$started.ToString('o') } |
    ConvertTo-Json -Depth 4 | Set-Content -LiteralPath (Join-Path $evidence 'selection.json')
try {
    $env:TEMP = $temporary; $env:TMP = $temporary; $env:TMPDIR = $temporary
    $env:IJOB_AUDIT_VENDOR = 'sqlite'
    $env:IJOB_AUDIT_RUN_ID = [datetime]::UtcNow.ToString('yyMMddHHmmss')
    $env:SPRING_PROFILES_ACTIVE = 'isolated-integration'
    $env:SPRING_CONFIG_LOCATION = 'classpath:/application.yml'
    Push-Location $project
    try {
        & mvn -B -ntp -Pquality -pl ijob-starter -am "-Dtest=$($required -join ',')" `
            '-Dsurefire.failIfNoSpecifiedTests=false' "-Djava.io.tmpdir=$temporary" test `
            *> (Join-Path $evidence 'maven.log')
        $exitCode = $LASTEXITCODE
    } finally { Pop-Location }
} finally {
    $env:TEMP = $oldTemp; $env:TMP = $oldTmp; $env:TMPDIR = $oldTmpDir
    Remove-Item Env:IJOB_AUDIT_VENDOR, Env:IJOB_AUDIT_RUN_ID, Env:SPRING_PROFILES_ACTIVE, Env:SPRING_CONFIG_LOCATION -ErrorAction SilentlyContinue
    $destination = Join-Path $evidence 'junit'
    New-Item -ItemType Directory -Path $destination | Out-Null
    foreach ($name in $required) {
        $path = Join-Path $project "ijob-starter/target/surefire-reports/TEST-com.ifelio.ijob.$name.xml"
        if (-not (Test-Path -LiteralPath $path) -or (Get-Item -LiteralPath $path).LastWriteTimeUtc -lt $started) {
            $summaries += [pscustomobject]@{suite=$name; missingOrStale=$true; tests=0; failures=0; errors=0; skipped=0}
            continue
        }
        Copy-Item -LiteralPath $path -Destination $destination
        [xml]$xml = Get-Content -Raw -LiteralPath $path
        $s = $xml.testsuite
        $summaries += [pscustomobject]@{suite=$name; missingOrStale=$false; tests=[int]$s.tests; failures=[int]$s.failures; errors=[int]$s.errors; skipped=[int]$s.skipped}
    }
    ConvertTo-Json -InputObject @($summaries) -Depth 4 | Set-Content -LiteralPath (Join-Path $evidence 'junit-summary.json')
    @{exitCode=$exitCode; finishedUtc=[datetime]::UtcNow.ToString('o'); temporaryDirectory=$temporary; temporaryDirectoryRetained=$true} |
        ConvertTo-Json | Set-Content -LiteralPath (Join-Path $evidence 'execution.json')
    # Preserve temporary fixture data locally on failure; never archive it or clean other runs.
    $records = @(Get-ChildItem -LiteralPath $evidence -Recurse -File | ForEach-Object {
        [pscustomobject]@{path=[IO.Path]::GetRelativePath($evidence, $_.FullName); sha256=(Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash}
    })
    $records | Export-Csv -LiteralPath (Join-Path $evidence 'evidence-sha256.csv') -NoTypeInformation
}
if ($exitCode -ne 0) { throw "Maven integration failed ($exitCode); inspect $evidence" }
if (@($summaries | Where-Object { $_.missingOrStale -or $_.tests -eq 0 -or $_.failures -or $_.errors -or $_.skipped }).Count) {
    throw 'Every selected integration suite must execute and pass without skips.'
}
Write-Output "Local HTTP/SQLite integration passed: $evidence"
