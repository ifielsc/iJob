# Windows native: configure once and manage the instance

[简体中文](../../zh-CN/installation/windows.md) · [Manual home](../README.md)

## 1. Prepare the program and parent directories

Extract the complete native package, install Java 21 and set JAVA_HOME/PATH. Open PowerShell in the program directory and create the control/data parents:

```powershell
New-Item -ItemType Directory -Force D:/ijob/instances, D:/ijob-data | Out-Null
```

## 2. Configure this instance once

```powershell
.\ijob.cmd configure -Directory D:/ijob/instances/a -Home D:/ijob-data/a -Lang en
```

This creates the control entry without starting the application. The program path is recorded automatically; home/language are stored in `D:/ijob/instances/a/instance.conf`. No IJOB_HOME/IJOB_LANG setup is needed, and inherited variables cannot override saved settings. Another instance needs separate control/data directories and, on the same host, a different port.

## 3. First startup

```powershell
cd D:/ijob/instances/a
.\ijob.cmd start
```

start initializes a new installation automatically. Enter an independent administrator/password and database type; SQLite is suitable for the introduction. initialize.cmd is not a prerequisite. Existing valid installations retain configuration. Visit `http://127.0.0.1:18080` or your changed port. The hidden background process survives closing the terminal.

## 4. Inspect settings and status

```powershell
.\ijob.cmd show
.\ijob.cmd status
```

Status verifies process identity; also inspect node readiness and business runs in the workbench. A stopped native instance exits 3 for automation.

## 5. View logs

```powershell
.\ijob.cmd logs
.\ijob.cmd logs -Once
```

The first follows logs until Ctrl+C; the second prints the latest 200 lines and returns. Application logs live in data-home/logs; business instance logs are in Runs.

## 6. Stop, start again and apply configuration

```powershell
.\ijob.cmd stop
.\ijob.cmd status
.\ijob.cmd start
```

After backing up and editing application.properties in the data home according to [Configuration](../configuration.md), run:

```powershell
.\ijob.cmd restart
```

A fresh terminal can directly run `D:/ijob/instances/a/ijob.cmd status` without resetting variables. See the [unified entry](instance-launcher.md) for path/language and upgrade rules. Legacy start/initialize/stop/status/logs/restart.cmd remain compatible; their environment/default-home rules are documented there.
