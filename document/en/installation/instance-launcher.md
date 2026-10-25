# Configure each instance once: one lifecycle entry point

[简体中文](../../zh-CN/installation/instance-launcher.md) · [Manual home](../README.md)

## Three separate directories

The program directory holds the complete distribution; the control directory holds the entry point and instance.conf; the data home holds application.properties, secrets, databases and logs. None may contain another. Create their parent directories first. Each instance has its own control directory and data home; a read-only program bundle may be shared.

```text
programs/ijob-1.0.0/       Complete distribution
instances/a/              ijob.cmd + support/instance.ps1 (Windows) or ijob.sh (Linux/Docker)
                          instance.conf: this instance's launch settings
data/a/                   This instance's IJOB_HOME; may contain secrets
```

Run configure once from the program directory; see [Windows](windows.md), [Linux](linux.md) or [Docker](docker.md). It only creates a new control directory: no administrator, database initialization or startup. Existing control directories are never overwritten. An existing data home may be registered, but start validates its installation mode/completeness. Running ijob start in an unconfigured program directory fails explicitly instead of guessing an instance.

## Shared settings for every action

instance.conf is plain UTF-8, one key per line, without quotes, variable expansion or shell expressions; use LF on Linux. configure generates it, so normal setup does not require manual editing.

| Setting | Meaning and change rules |
| --- | --- |
| version | Control-file format, currently 1; do not edit. This is not the product version. |
| bundle | Absolute complete-program directory; change while stopped as part of an upgrade. |
| home | Absolute instance data directory; editing this value does not move data. Follow backup/restore procedures. |
| lang | Launch-message language: zh or en; defaults to en, read on the next command. |
| mode | windows, linux or docker, selected during creation. Editing it does not migrate an installation. |

start, stop, restart, status and logs all read this file, regardless of the caller's working directory. Saved home/lang take precedence over inherited IJOB_HOME/IJOB_LANG so a fresh terminal or another instance's variables cannot silently select a different home. Commands display the selected data home; show prints launch settings without application passwords or keys.

| Action | Behavior |
| --- | --- |
| start | Initialize a new installation if necessary and start; reuse existing configuration. |
| stop | Stop this instance while retaining configuration, data and history. |
| restart | Stop/start native processes. For Docker, recreate using this bundle's image locks and wait for readiness, applying application/Compose changes. |
| status | Native process state or this Docker project's Compose status. Native stopped state exits 3; successful Docker ps does not prove health. |
| logs | Follow logs. Ctrl+C stops viewing, not the background service. |
| logs -Once / logs --once | Windows / Linux and Docker: print the latest 200 lines and return. |
| show | Display program directory, data home, language and mode. |

Use the same OS account for all commands; if Docker needs sudo, use it consistently. Java 21/PATH and Docker permissions remain system prerequisites, not instance.conf properties. Do not put DB passwords in this file. Protect the data home as private configuration.

## Legacy scripts without environment variables

Direct legacy/start.cmd, legacy/start.sh and legacy/start-docker.sh remain supported. Without IJOB_HOME they select these locations beside the program directory; they do not remember a previously chosen custom home:

| Legacy entry | Default home |
| --- | --- |
| Windows legacy/start.cmd | Program parent/ijob-data |
| Linux legacy/start.sh | Program parent/ijob-data-linux |
| Docker legacy/start-docker.sh | Program parent/ijob-data-docker |

IJOB_LANG defaults to English; zh selects Chinese initialization/launch messages. It does not change the web UI or business-notification language; some underlying errors and technical logs remain English. Legacy actions still need consistent environment variables. Prefer the persistent entry point for daily operation.

The setup JAR accepts --home/--lang, but a single invocation does not persist these for legacy scripts. application.properties cannot locate itself: the launcher needs the data home before reading it. instance.conf supplies separate persistent launch settings and is not platform configuration.

## Upgrade and diagnosis

Use show to confirm the instance, stop, then follow [backup/upgrade](../operations/upgrade-rollback.md). For the old flat layout, create a new control entry as described below. With compatible control scripts and the same layout, keep home and change bundle while stopped, start, then verify login, history and a harmless task. Do not change home/bundle while running or status/logs could point elsewhere. Use canonical absolute paths without symlinks/junctions. Missing, duplicate or unknown keys are rejected.

## Package layout and legacy entry points

```text
ijob-1.0.0/
  ijob.cmd / ijob.sh    Recommended: configure, then use the instance control directory
  legacy/              Previous initialize/start/stop/status/logs entry points
  support/             Internal helpers and logging-native.xml; do not invoke directly
  docs/                Chinese and English manuals
  JARs, web-dist, ...   Program resources
```

From the package root, call `.\legacy\start.cmd`, `bash ./legacy/start.sh`, or `sudo bash ./legacy/start-docker.sh`; entering legacy first also works. Keep the whole package intact. Program resources are always resolved from the package root. Default legacy data homes remain ijob-data / ijob-data-linux / ijob-data-docker beside the program directory, never a new installation inside the program or legacy directory.

A new Windows control directory contains only ijob.cmd, instance.conf and support/instance.ps1. Linux/Docker control directories contain ijob.sh and instance.conf.

## Upgrade from the previous flat-script package

Old control scripts do not know the legacy/support layout. **Changing only bundle in an old instance.conf is insufficient.** Retain the old control directory and create a new one referencing the same data:

1. Run show through the old control directory; record home, lang and mode and verify the instance identity.
2. Run stop through that directory, confirm shutdown and back up the original data/configuration.
3. Extract the new complete package to a separate program directory and run configure from its root. Use a new control directory and retain the original home, lang and mode.

Windows example, from the new package root:

```powershell
.\ijob.cmd configure -Directory D:/ijob/instances/a-layout -Home D:/ijob-data/a -Lang en
```

Linux native example; for Docker select mode docker and consistently use the original account/sudo convention:

```sh
bash ./ijob.sh configure --directory /srv/ijob/instances/a-layout --home /srv/ijob-data/a --lang en --mode linux
```

4. Use show, start, status and logs through the new control directory. Verify the existing account, definitions and history. Existing installations are reused without reinitialization or new keys. Never launch the same data home concurrently through both entries.
5. Update shortcuts/operations records to the new control directory. To roll back only this script-layout change, stop through the new entry and start the original package through the retained old entry. Other program/database changes still require the documented rollback procedure.

New control scripts also recognize the old flat management layout for controlled rollback; old control scripts do not automatically recognize the new layout. Old controls and packages are retained, not automatically removed.
