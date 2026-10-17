param(
    [Parameter(Mandatory=$true)][string]$Bom,
    [Parameter(Mandatory=$true)][string]$OutputDirectory
)
$ErrorActionPreference = 'Stop'
$bomPath = (Resolve-Path -LiteralPath $Bom).Path
if (Test-Path -LiteralPath $OutputDirectory) { throw 'Use a new evidence directory; existing evidence is never overwritten.' }
New-Item -ItemType Directory -Path $OutputDirectory | Out-Null
Copy-Item -LiteralPath $bomPath -Destination (Join-Path $OutputDirectory 'bom.json')
$document = Get-Content -Raw -LiteralPath $bomPath | ConvertFrom-Json
# Only public third-party coordinates are sent. No application names, code, hashes, or credentials.
$components = @($document.components | Where-Object { $_.purl -like 'pkg:maven/*' -and $_.group -ne 'com.ifelio' })
$findings = [Collections.Generic.List[object]]::new()
for ($offset = 0; $offset -lt $components.Count; $offset += 50) {
    $batch = @($components[$offset..([Math]::Min($offset + 49, $components.Count - 1))])
    $queries = @($batch | ForEach-Object { @{ package = @{ ecosystem = 'Maven'; name = "$($_.group):$($_.name)" }; version = $_.version } })
    $result = Invoke-RestMethod -Uri 'https://api.osv.dev/v1/querybatch' -Method Post -ContentType 'application/json' -Body (@{queries=$queries} | ConvertTo-Json -Depth 8) -TimeoutSec 45
    if (@($result.results).Count -ne $batch.Count) { throw 'Incomplete OSV response' }
    for ($index = 0; $index -lt $batch.Count; $index++) {
        if ($result.results[$index].next_page_token) { throw 'OSV pagination requires follow-up; scan is incomplete' }
        foreach ($vulnerability in $result.results[$index].vulns) {
            $findings.Add([ordered]@{ component="$($batch[$index].group):$($batch[$index].name)"; version=$batch[$index].version; id=$vulnerability.id })
        }
    }
}
$advisories = [Collections.Generic.List[object]]::new()
foreach ($id in @($findings.id | Sort-Object -Unique)) {
    $advisories.Add((Invoke-RestMethod -Uri "https://api.osv.dev/v1/vulns/$id" -TimeoutSec 45))
}
$report = [ordered]@{ scannedAt=[datetimeoffset]::Now.ToString('o'); source='OSV public Maven advisories'; complete=$true; bomSha256=(Get-FileHash -LiteralPath $bomPath -Algorithm SHA256).Hash; components=$components.Count; findings=@($findings.ToArray()); advisories=@($advisories.ToArray()) }
[IO.File]::WriteAllText((Join-Path $OutputDirectory 'osv-report.json'), ($report | ConvertTo-Json -Depth 60), [Text.UTF8Encoding]::new($false))
Write-Output "OSV scan complete: components=$($components.Count); findings=$($findings.Count)"
$findings | Format-Table -AutoSize
if ($findings.Count -gt 0) { exit 2 }
