param([Parameter(Mandatory=$true)][string]$Destination)
$ErrorActionPreference = 'Stop'
$version = '3.9.11'
if (Test-Path -LiteralPath $Destination) { throw 'Use a new Maven installation directory' }
New-Item -ItemType Directory -Path $Destination | Out-Null
$url = "https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/$version/apache-maven-$version-bin.zip"
$archive = Join-Path $Destination 'maven.zip'
Invoke-WebRequest -Uri $url -OutFile $archive
$expected = (Invoke-RestMethod -Uri "$url.sha512").Trim().Split(' ')[0]
if ($expected -notmatch '^[0-9a-fA-F]{128}$' -or (Get-FileHash -LiteralPath $archive -Algorithm SHA512).Hash -ne $expected) { throw 'Maven archive checksum mismatch' }
Expand-Archive -LiteralPath $archive -DestinationPath $Destination
$bin = Join-Path $Destination "apache-maven-$version/bin"
$env:PATH = "$bin$([IO.Path]::PathSeparator)$env:PATH"
if ($env:GITHUB_PATH) { $bin | Out-File -LiteralPath $env:GITHUB_PATH -Append -Encoding utf8 }
& mvn --version
if ($LASTEXITCODE -ne 0) { throw 'Maven installation failed' }
