# Windows native installation and lifecycle

[简体中文](../../zh-CN/installation/windows.md) · [Manual home](../README.md)

## Preparation

Extract the complete native bundle into its own program directory and open a terminal there. Install Java 21 and set JAVA_HOME/PATH. Use PowerShell; the launcher invokes Windows PowerShell.

```powershell
$env:IJOB_HOME = "D:/ijob-data/a"
$env:IJOB_LANG = "en"
```

Use an independent absolute IJOB_HOME path and set the same home in later terminals. It contains secrets and must stay outside source. Run all commands below in the bundle directory.

## First installation and start

```powershell
.\start.cmd
```

`start` invokes initialization automatically. Enter a new administrator, password and database type; SQLite is suitable for the first walkthrough. Existing installations are preserved. After readiness, open `http://127.0.0.1:18080` or your configured port. Java runs hidden in the background; closing this terminal does not stop the application.

To generate configuration without starting the application, use the optional command:

```powershell
.\initialize.cmd
```

## Daily use: status

```powershell
.\status.cmd
```

The command verifies the process identity owned by this installation. Also inspect nodes and actual work in the dashboard.

## Daily use: logs

```powershell
.\logs.cmd
```

Ctrl+C exits log following without stopping the application. Application logs are in IJOB_HOME/logs; business-instance logs are in the run center.

## Daily use: stop

```powershell
.\stop.cmd
```

Wait for exit and check status again. Agree a maintenance window and review active work first; stopping the platform cannot undo external writes.

## Change configuration and restart

Edit `IJOB_HOME/application.properties`, keep each key unique, and check formats using [Configuration](../configuration.md). Then:

```powershell
.\restart.cmd
```

Later starts also use `start`; do not reinitialize. Follow [Operations](../operations.md) for upgrades/restores and preserve the existing home.
