# Copyright (c) 2026 ifielsc
# SPDX-License-Identifier: MIT

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
# Keep Maven 3.9.11 while fixing CVE-2025-67030 in its bundled Plexus utility.
$mavenRoot = Join-Path $Destination "apache-maven-$version"
$plexus = Join-Path $mavenRoot 'lib/plexus-utils-3.6.1.jar'
Invoke-WebRequest -Uri 'https://repo.maven.apache.org/maven2/org/codehaus/plexus/plexus-utils/3.6.1/plexus-utils-3.6.1.jar' -OutFile $plexus
if ((Get-FileHash -LiteralPath $plexus -Algorithm SHA256).Hash -ne '05a63effd67e2d6b9d610cc82e2bd7473289d34802e57a529b28110f28af5679') {
    throw 'Plexus security patch checksum mismatch'
}
Remove-Item -LiteralPath (Join-Path $mavenRoot 'lib/plexus-utils-3.6.0.jar')
$guava = Join-Path $mavenRoot 'lib/guava-33.7.2-jre.jar'
Invoke-WebRequest -Uri 'https://repo.maven.apache.org/maven2/com/google/guava/guava/33.7.2-jre/guava-33.7.2-jre.jar' -OutFile $guava
if ((Get-FileHash -LiteralPath $guava -Algorithm SHA256).Hash -ne 'b530942257fb935f8b2cfaa5f8eb5bd59c555fd8e8d01b8ce98912e077ea606c') {
    throw 'Guava security patch checksum mismatch'
}
Remove-Item -LiteralPath (Join-Path $mavenRoot 'lib/guava-33.4.8-jre.jar')
@{
    maven = $version
    component = 'org.codehaus.plexus:plexus-utils:3.6.1'
    sha256 = '05a63effd67e2d6b9d610cc82e2bd7473289d34802e57a529b28110f28af5679'
    reason = 'CVE-2025-67030; upstream Maven archive retained alongside the patched installation'
    guava = 'com.google.guava:guava:33.7.2-jre'
    guava_sha256 = 'b530942257fb935f8b2cfaa5f8eb5bd59c555fd8e8d01b8ce98912e077ea606c'
    guava_reason = 'CVE-2026-102554; build tool only'
} | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $mavenRoot 'ijob-security-patch.json') -Encoding utf8
$bin = Join-Path $Destination "apache-maven-$version/bin"
$env:PATH = "$bin$([IO.Path]::PathSeparator)$env:PATH"
if ($env:GITHUB_PATH) { $bin | Out-File -LiteralPath $env:GITHUB_PATH -Append -Encoding utf8 }
& mvn --version
if ($LASTEXITCODE -ne 0) { throw 'Maven installation failed' }
