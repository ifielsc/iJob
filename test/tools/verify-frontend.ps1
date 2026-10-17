param([Parameter(Mandatory = $true)][ValidatePattern('^[a-zA-Z0-9-]+$')][string]$RunId)
$ErrorActionPreference = 'Stop'
$projectRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$webRoot = Join-Path $projectRoot 'develop/web/ijob'
$reportRoot = Join-Path $projectRoot "test/reports/$RunId"
if (Test-Path -LiteralPath $reportRoot) { throw 'Evidence directory already exists; choose a new RunId.' }
New-Item -ItemType Directory -Path $reportRoot | Out-Null
Push-Location $webRoot
$oldHtml = $env:PLAYWRIGHT_HTML_OUTPUT_DIR
$oldJunit = $env:PLAYWRIGHT_JUNIT_OUTPUT_FILE
try {
    $commands = @(
        @{ Name = 'runtime'; Args = @('check:runtime') },
        @{ Name = 'typecheck'; Args = @('typecheck') },
        @{ Name = 'lint'; Args = @('lint') },
        @{ Name = 'format'; Args = @('format:check') },
        @{ Name = 'unit'; Args = @('test', '--reporter=default', '--reporter=junit', "--outputFile=$(Join-Path $reportRoot 'unit-junit.xml')") },
        @{ Name = 'build'; Args = @('build') }
    )
    foreach ($command in $commands) {
        & pnpm @($command.Args) 2>&1 | Tee-Object -FilePath (Join-Path $reportRoot "$($command.Name).log")
        if ($LASTEXITCODE -ne 0) { throw "$($command.Name) failed: $LASTEXITCODE" }
    }
    $env:PLAYWRIGHT_HTML_OUTPUT_DIR = Join-Path $reportRoot 'playwright-html'
    $env:PLAYWRIGHT_JUNIT_OUTPUT_FILE = Join-Path $reportRoot 'browser-junit.xml'
    & pnpm exec playwright test --reporter=list,junit,html --output (Join-Path $reportRoot 'browser') 2>&1 | Tee-Object -FilePath (Join-Path $reportRoot 'browser.log')
    if ($LASTEXITCODE -ne 0) { throw "Browser tests failed: $LASTEXITCODE" }
    $env:PLAYWRIGHT_HTML_OUTPUT_DIR = Join-Path $reportRoot 'production-html'
    $env:PLAYWRIGHT_JUNIT_OUTPUT_FILE = Join-Path $reportRoot 'production-junit.xml'
    & pnpm exec playwright test --config playwright.production.config.ts --reporter=list,junit,html --output (Join-Path $reportRoot 'production') 2>&1 | Tee-Object -FilePath (Join-Path $reportRoot 'production.log')
    if ($LASTEXITCODE -ne 0) { throw "Production smoke test failed: $LASTEXITCODE" }
    $env:PLAYWRIGHT_HTML_OUTPUT_DIR = Join-Path $reportRoot 'integration-html'
    $env:PLAYWRIGHT_JUNIT_OUTPUT_FILE = Join-Path $reportRoot 'integration-junit.xml'
    & pnpm exec playwright test --config playwright.integration.config.ts --reporter=list,junit,html --output (Join-Path $reportRoot 'integration') 2>&1 | Tee-Object -FilePath (Join-Path $reportRoot 'integration.log')
    if ($LASTEXITCODE -ne 0) { throw "Isolated backend integration failed: $LASTEXITCODE" }
    & pnpm list --depth 0 --json | Out-File -LiteralPath (Join-Path $reportRoot 'dependencies.json') -Encoding utf8
    $records = Get-ChildItem -LiteralPath $reportRoot -Recurse -File | ForEach-Object {
        [pscustomobject]@{ Path = [IO.Path]::GetRelativePath($reportRoot, $_.FullName); SHA256 = (Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash }
    }
    $records | Export-Csv -LiteralPath (Join-Path $reportRoot 'evidence-sha256.csv') -Encoding utf8 -NoTypeInformation
    Write-Output "Frontend verification passed: $reportRoot"
} finally {
    $env:PLAYWRIGHT_HTML_OUTPUT_DIR = $oldHtml
    $env:PLAYWRIGHT_JUNIT_OUTPUT_FILE = $oldJunit
    Pop-Location
}
