# Configure each instance once: one lifecycle entry point

[简体中文](../../zh-CN/installation/instance-launcher.md) · [Manual home](../README.md)

## Three separate directories

The program directory holds the complete distribution; the control directory holds the entry point and instance.conf; the data home holds application.properties, secrets, databases and logs. None may contain another. Create their parent directories first. Each instance has its own control directory and data home; a read-only program bundle may be shared.

```text
programs/ijob-1.0.0/       Complete distribution
instances/a/              ijob.cmd + instance.ps1 (Windows) or ijob.sh (Linux/Docker)
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

Direct start.cmd, start.sh and start-docker.sh remain supported. Without IJOB_HOME they select these locations beside the program directory; they do not remember a previously chosen custom home:

| Legacy entry | Default home |
| --- | --- |
| Windows start.cmd | Program parent/ijob-data |
| Linux start.sh | Program parent/ijob-data-linux |
| Docker start-docker.sh | Program parent/ijob-data-docker |

IJOB_LANG defaults to English; zh selects Chinese initialization/launch messages. It does not change the web UI or business-notification language; some underlying errors and technical logs remain English. Legacy actions still need consistent environment variables. Prefer the persistent entry point for daily operation.

The setup JAR accepts --home/--lang, but a single invocation does not persist these for legacy scripts. application.properties cannot locate itself: the launcher needs the data home before reading it. instance.conf supplies separate persistent launch settings and is not platform configuration.

## Upgrade and diagnosis

Use show to confirm the instance, stop, then follow [backup/upgrade](../operations/upgrade-rollback.md). Keep home and change bundle to the new complete distribution while stopped; review control-script compatibility, start, then verify login, history and a harmless task. Do not change home/bundle while running or status/logs could point elsewhere. Use canonical absolute paths without symlinks/junctions. Missing, duplicate or unknown keys are rejected.
