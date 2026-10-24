# Copyright (c) 2026 ifielsc
# SPDX-License-Identifier: MIT

param([Parameter(Mandatory=$true)][int]$ProcessId, [Parameter(Mandatory=$true)][string]$Created, [Parameter(Mandatory=$true)][string]$DataRoot)
$ErrorActionPreference = 'Stop'
$process = Get-CimInstance Win32_Process -Filter "ProcessId = $ProcessId"
if (-not $process) { exit 0 }
if ($process.CreationDate.ToUniversalTime().ToString('o') -ne $Created -or -not $process.CommandLine.Contains('"-Dijob.native.home=' + $DataRoot + '"')) { exit 2 }
Add-Type -TypeDefinition @'
using System;
using System.Runtime.InteropServices;
public static class IJobConsoleSignal {
    [DllImport("kernel32.dll", SetLastError=true)] public static extern bool FreeConsole();
    [DllImport("kernel32.dll", SetLastError=true)] public static extern bool AttachConsole(uint pid);
    [DllImport("kernel32.dll", SetLastError=true)] public static extern bool SetConsoleCtrlHandler(IntPtr handler, bool add);
    [DllImport("kernel32.dll", SetLastError=true)] public static extern bool GenerateConsoleCtrlEvent(uint signal, uint group);
    [DllImport("kernel32.dll", SetLastError=true)] public static extern uint GetConsoleProcessList(uint[] processes, uint size);
}
'@
[void][IJobConsoleSignal]::FreeConsole()
if (-not [IJobConsoleSignal]::AttachConsole($ProcessId)) { exit 3 }
try {
    # Refuse a shared interactive console; never send Ctrl+C to unrelated processes.
    $members = New-Object uint32[] 256
    $count = [IJobConsoleSignal]::GetConsoleProcessList($members, $members.Length)
    if ($count -lt 1 -or $count -gt $members.Length) { exit 4 }
    foreach ($member in $members[0..($count - 1)]) { if ($member -ne $ProcessId -and $member -ne $PID) { exit 5 } }
    [void][IJobConsoleSignal]::SetConsoleCtrlHandler([IntPtr]::Zero, $true)
    if (-not [IJobConsoleSignal]::GenerateConsoleCtrlEvent(0, 0)) { exit 6 }
    Start-Sleep -Milliseconds 500
} finally { [void][IJobConsoleSignal]::FreeConsole() }
