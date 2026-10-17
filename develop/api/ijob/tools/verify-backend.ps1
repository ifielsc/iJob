param([Parameter(Mandatory=$true)][string]$RunId, [switch]$Offline, [switch]$ScanDependencies)
$ErrorActionPreference = 'Stop'
if ($RunId -notmatch '^[A-Za-z0-9_-]+$') { throw 'Invalid run ID' }
$workspace = (Resolve-Path (Join-Path $PSScriptRoot '../../../..')).Path
$evidence = Join-Path $workspace "test/reports/$RunId"
if (Test-Path -LiteralPath $evidence) { throw 'Use a new evidence run ID' }
New-Item -ItemType Directory -Path $evidence | Out-Null
& (Join-Path $PSScriptRoot 'format-java.ps1') -Check
$main = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$agent = (Resolve-Path (Join-Path $main '../ijob-monitor-agent')).Path
$network = @(); if ($Offline) { $network += '-o' }
foreach ($project in @($main, $agent)) {
    Push-Location $project
    try {
        $name = Split-Path $project -Leaf
        $selection = @(); if ($project -eq $main) { $selection = @('-Dtest=*Test,RealSqliteMigrationIT,SqliteOfflineMigrationIT,SecurityHttpIT', '-Dsurefire.failIfNoSpecifiedTests=false') }
        & mvn @network -q -Pquality @selection clean install *> (Join-Path $evidence "$name-build.log")
        if ($LASTEXITCODE -ne 0) { throw "Build/tests failed: $name; inspect evidence log" }
        $reports = @(Get-ChildItem -LiteralPath $project -Recurse -File -Filter 'TEST-*.xml' | Where-Object { $_.DirectoryName -match '[\\/]target[\\/]surefire-reports$' })
        foreach ($report in $reports) {
            $module = Split-Path (Split-Path (Split-Path $report.FullName -Parent) -Parent) -Parent | Split-Path -Leaf
            $destination = Join-Path $evidence "$module-junit"
            New-Item -ItemType Directory -Path $destination -Force | Out-Null
            Copy-Item -LiteralPath $report.FullName -Destination $destination
        }
        if ($ScanDependencies) {
            if ($Offline) { throw 'Dependency scanning requires network access' }
            & mvn -q -DskipTests org.cyclonedx:cyclonedx-maven-plugin:2.9.1:makeAggregateBom *> (Join-Path $evidence "$name-sbom.log")
            if ($LASTEXITCODE -ne 0) { throw "SBOM generation failed: $name" }
            & (Join-Path $PSScriptRoot 'scan-sbom.ps1') -Bom (Join-Path $project 'target/bom.json') -OutputDirectory (Join-Path $evidence "$name-sca")
            if ($LASTEXITCODE -ne 0) { throw "Dependency findings require triage: $name" }
        }
    } finally { Pop-Location }
}
Write-Output "Backend quality gate passed; evidence=$evidence"
