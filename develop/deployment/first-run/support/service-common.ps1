# Copyright (c) 2026 ifielsc
# SPDX-License-Identifier: MIT

function Get-ServicePath([string]$Value) {
    if (-not $Value -or $Value -notmatch '^[A-Za-z]:[\\/]' -or $Value -match '[\r\n"%]') { throw 'Use an absolute local service path without quotes, percent signs or newlines.' }
    $path = [IO.Path]::GetFullPath($Value).TrimEnd('\','/')
    if ($path.Length -le 2) { throw 'A drive root cannot be a service path.' }
    for ($part = $path; $part; $part = Split-Path -Parent $part) {
        if ((Test-Path -LiteralPath $part) -and ((Get-Item -Force -LiteralPath $part).Attributes -band [IO.FileAttributes]::ReparsePoint)) { throw 'Service paths must not contain links or junctions.' }
    }
    return $path
}
function Read-ServiceInstance([string]$Control) {
    $path = Get-ServicePath (Join-Path $Control 'instance.conf')
    $values = @{}
    foreach ($line in [IO.File]::ReadAllLines($path, [Text.Encoding]::UTF8)) {
        if (-not $line -or $line.StartsWith('#')) { continue }
        if ($line -notmatch '^(version|bundle|home|lang|mode)=(.+)$' -or $values.ContainsKey($Matches[1])) { throw 'Invalid or duplicate instance setting.' }
        $values[$Matches[1]] = $Matches[2]
    }
    if ($values.Count -ne 5 -or $values.version -cne '1' -or $values.mode -cne 'windows' -or $values.lang -cnotin @('zh','en')) { throw 'Windows service management requires a Windows native instance.' }
    $values.bundle = Get-ServicePath $values.bundle
    $values.home = Get-ServicePath $values.home
    foreach ($pair in @(@($Control,$values.bundle), @($Control,$values.home), @($values.bundle,$values.home))) {
        if ($pair[0].Equals($pair[1], [StringComparison]::OrdinalIgnoreCase) -or $pair[0].StartsWith($pair[1] + '\', [StringComparison]::OrdinalIgnoreCase) -or $pair[1].StartsWith($pair[0] + '\', [StringComparison]::OrdinalIgnoreCase)) { throw 'Program, control and data directories must be separate.' }
    }
    return $values
}
function New-ServiceXml([string]$Name, [string]$Java, [string]$DataRoot, [string]$Bundle, [string]$Vendor, [string]$Lang) {
    if ($Name -cnotmatch '^ijob[A-Za-z0-9]{1,40}$' -or $Vendor -cnotin @('sqlite','mysql','postgresql','sqlserver','oracle') -or $Lang -cnotin @('zh','en')) { throw 'Invalid service name, database vendor or language.' }
    $Java = Get-ServicePath $Java; $DataRoot = Get-ServicePath $DataRoot; $Bundle = Get-ServicePath $Bundle
    $document = New-Object System.Xml.XmlDocument
    $root = $document.CreateElement('service'); $null = $document.AppendChild($root)
    function Add-Value([string]$Key, [string]$Value) {
        $element = $document.CreateElement($Key); $element.InnerText = $Value
        $null = $root.AppendChild($element)
    }
    Add-Value 'id' $Name
    Add-Value 'name' "iJob $Name"
    Add-Value 'description' "iJob native instance: $DataRoot"
    Add-Value 'executable' $Java
    $arguments = @("-Dijob.native.home=$DataRoot", '-jar', (Join-Path $Bundle 'ijob-starter-1.0.0.jar'),
        '--spring.profiles.active=release,native-background',
        ('--spring.config.additional-location=' + ([uri](Join-Path $DataRoot 'application.properties')).AbsoluteUri),
        ('--app.web.directory=' + (Join-Path $Bundle 'web-dist')),
        ('--logging.config=' + ([uri](Join-Path $Bundle 'support/logging-native.xml')).AbsoluteUri),
        ('--ijob.native.log-directory=' + (Join-Path $DataRoot 'logs')), '--ijob.native.log-session=windows-service')
    Add-Value 'arguments' (($arguments | ForEach-Object { '"' + $_ + '"' }) -join ' ')
    Add-Value 'workingdirectory' (Join-Path $DataRoot 'runtime')
    Add-Value 'startmode' 'Manual'
    Add-Value 'stoptimeout' '120 sec'
    Add-Value 'resetfailure' '1 hour'
    foreach ($delay in @('10 sec','30 sec','60 sec')) {
        $element = $document.CreateElement('onfailure'); $element.SetAttribute('action','restart'); $element.SetAttribute('delay',$delay)
        $null = $root.AppendChild($element)
    }
    $element = $document.CreateElement('onfailure'); $element.SetAttribute('action','none'); $null = $root.AppendChild($element)
    foreach ($entry in @{IJOB_HOME=$DataRoot; IJOB_LANG=$Lang; IJOB_PLATFORM_DB=$Vendor; JAVA_TOOL_OPTIONS=''; JDK_JAVA_OPTIONS=''; _JAVA_OPTIONS=''}.GetEnumerator()) {
        $element = $document.CreateElement('env'); $element.SetAttribute('name',$entry.Key); $element.SetAttribute('value',$entry.Value)
        $null = $root.AppendChild($element)
    }
    $account = $document.CreateElement('serviceaccount')
    foreach ($entry in @{domain='NT SERVICE'; user=$Name}.GetEnumerator()) {
        $element = $document.CreateElement($entry.Key); $element.InnerText = $entry.Value; $null = $account.AppendChild($element)
    }
    $null = $root.AppendChild($account)
    Add-Value 'logpath' (Join-Path $DataRoot 'logs/service')
    $log = $document.CreateElement('log'); $log.SetAttribute('mode','roll-by-size')
    foreach ($entry in @{sizeThreshold='10240'; keepFiles='5'}.GetEnumerator()) {
        $element = $document.CreateElement($entry.Key); $element.InnerText = $entry.Value; $null = $log.AppendChild($element)
    }
    $null = $root.AppendChild($log)
    return $document.OuterXml
}
