param([Parameter(Mandatory=$true)][ValidatePattern('^[A-Za-z0-9_-]+$')][string]$RunId, [switch]$Offline, [switch]$ScanDependencies)
$ErrorActionPreference = 'Stop'
if ($Offline -and $ScanDependencies) { throw 'Dependency scanning requires network access' }
$workspace = (Resolve-Path (Join-Path $PSScriptRoot '../../../..')).Path
$evidence = Join-Path $workspace "test/reports/$RunId"
if (Test-Path -LiteralPath $evidence) { throw 'Use a new evidence run ID' }
New-Item -ItemType Directory -Path $evidence | Out-Null
$main = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$agent = (Resolve-Path (Join-Path $main '../ijob-monitor-agent')).Path
$network = @(); if ($Offline) { $network += '-o' }
$summaries = @()
try {
    $version = & mvn --version 2>&1
    $version | Set-Content -LiteralPath (Join-Path $evidence 'toolchain.log')
    if ($LASTEXITCODE -ne 0 -or ($version -join "`n") -notmatch 'Apache Maven 3\.9\.11\b' -or ($version -join "`n") -notmatch 'Java version: 21[.,]') {
        throw 'Use Maven 3.9.11 and JDK 21 (JAVA_HOME and PATH).'
    }
    & python -c 'import paramiko; assert paramiko.__version__ == "4.0.0"' *> (Join-Path $evidence 'python-fixture.log')
    if ($LASTEXITCODE -ne 0) { throw 'Install Python fixture dependencies from tools/ci/requirements.txt' }
    $formatter = Join-Path ([Environment]::GetFolderPath('UserProfile')) '.m2/repository/com/google/googlejavaformat/google-java-format/1.24.0/google-java-format-1.24.0-all-deps.jar'
    if (-not (Test-Path -LiteralPath $formatter)) {
        if ($Offline) { throw 'Formatter missing from local Maven cache' }
        & mvn -B -ntp org.apache.maven.plugins:maven-dependency-plugin:3.9.0:get '-Dartifact=com.google.googlejavaformat:google-java-format:1.24.0:jar:all-deps' '-Dtransitive=false' *> (Join-Path $evidence 'formatter-download.log')
        if ($LASTEXITCODE -ne 0) { throw 'Formatter download failed' }
    }
    & (Join-Path $PSScriptRoot 'format-java.ps1') -Check -FormatterJar $formatter *> (Join-Path $evidence 'format.log')
    foreach ($project in @($main, $agent)) {
        Push-Location $project
        $name = Split-Path $project -Leaf
        try {
            $selection = @()
            if ($project -eq $main) { $selection = @('-Dtest=*Test,RealSqliteMigrationIT,SqliteOfflineMigrationIT,SecurityHttpIT', '-Dsurefire.failIfNoSpecifiedTests=false') }
            try {
                & mvn @network -B -ntp -Pquality @selection clean install *> (Join-Path $evidence "$name-build.log")
                if ($LASTEXITCODE -ne 0) { throw "Build/tests failed: $name; inspect evidence log" }
            } finally {
                # Preserve JUnit evidence even when Maven fails.
                $reports = @(Get-ChildItem -LiteralPath $project -Recurse -File -Filter 'TEST-*.xml' | Where-Object { $_.DirectoryName -match '[\\/]target[\\/]surefire-reports$' })
                foreach ($report in $reports) {
                    $module = Split-Path (Split-Path (Split-Path $report.FullName -Parent) -Parent) -Parent | Split-Path -Leaf
                    $destination = Join-Path $evidence "$module-junit"
                    New-Item -ItemType Directory -Path $destination -Force | Out-Null
                    Copy-Item -LiteralPath $report.FullName -Destination $destination
                    [xml]$xml = Get-Content -Raw -LiteralPath $report.FullName
                    $suite = $xml.testsuite
                    $summaries += [pscustomobject]@{ project=$name; suite=[string]$suite.name; tests=[int]$suite.tests; failures=[int]$suite.failures; errors=[int]$suite.errors; skipped=[int]$suite.skipped }
                }
            }
            $actual = @($summaries | Where-Object { $_.project -eq $name })
            if (($actual | Measure-Object tests -Sum).Sum -le 0) { throw "No tests executed: $name" }
            $unexpectedSkips = @($actual | Where-Object { $_.skipped -gt 0 -and -not $_.suite.EndsWith('.RealMessageSourcesTest') })
            if ($unexpectedSkips.Count) { throw 'Unexpected skipped tests; inspect junit-summary.json' }
            if ($project -eq $main) {
                foreach ($required in @('RealSqliteMigrationIT','SqliteOfflineMigrationIT','SecurityHttpIT')) {
                    if (-not ($actual | Where-Object { $_.suite.EndsWith(".$required") -and $_.tests -gt $_.skipped -and $_.failures -eq 0 -and $_.errors -eq 0 })) { throw "Required integration suite did not pass: $required" }
                }
            }
            if ($ScanDependencies) {
                & mvn -B -ntp -DskipTests org.cyclonedx:cyclonedx-maven-plugin:2.9.1:makeAggregateBom *> (Join-Path $evidence "$name-sbom.log")
                if ($LASTEXITCODE -ne 0) { throw "SBOM generation failed: $name" }
                & (Join-Path $PSScriptRoot 'scan-sbom.ps1') -Bom (Join-Path $project 'target/bom.json') -OutputDirectory (Join-Path $evidence "$name-sca")
                if ($LASTEXITCODE -ne 0) { throw "Dependency findings require triage: $name" }
            }
        } finally { Pop-Location }
    }
    Write-Output "Backend and Agent quality gate passed; evidence=$evidence"
} finally {
    ConvertTo-Json -InputObject @($summaries) -Depth 4 | Set-Content -LiteralPath (Join-Path $evidence 'junit-summary.json')
}
