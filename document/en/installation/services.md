# Windows services and systemd management

[简体中文](../../zh-CN/installation/services.md) · [Manual home](../README.md)

Configure the [instance entry](instance-launcher.md), initialize, verify startup and stop first. Service startup never prompts for accounts, invokes setup or resets secrets. Give each instance separate service/control/data identities and a port; a read-only program directory may be shared.

Service management supervises the application process. Configure startup dependencies for a separate database or other external services explicitly and confirm they are ready; the installer does not discover database services automatically. Give each instance separate file permissions so that one instance cannot access another instance’s data home.

Changing service settings, replacing a program bundle and migrating a database are separate operations. Changing process management does not migrate data. Back up the installation and check compatibility before replacing a bundle; follow the upgrade and rollback steps at the end of this page.

## Windows

Use Java 21 and .NET Framework 4. The host is pinned to [WinSW 2.12.0, WinSW.NET4.exe](https://github.com/winsw/winsw/releases/tag/v2.12.0), with its MIT license included at support/WinSW-LICENSE.txt. Prepare the wrapper executable separately. Check the official URL, length and SHA-256 in support/winsw.json. The pinned digest is `923111c7142b3dc783a3c722b19b8a21bcb78222d7a136ac33f0ca8a29f4cb66`, calculated from official HTTPS download bytes, not an upstream signature. This file has no Authenticode signature.

Operate from the control directory. Names begin with ijob followed by 1–40 ASCII letters/digits. Preview creates XML without installation; actual management requires an elevated terminal:

```powershell
.\ijob.cmd service preview -ServiceName ijobA -JavaHome D:/tools/jdk-21 -OutputDirectory D:/ijob-preview/a
.\ijob.cmd service install -ServiceName ijobA -JavaHome D:/tools/jdk-21 -Wrapper D:/downloads/WinSW.NET4.exe
.\ijob.cmd service enable
.\ijob.cmd start
.\ijob.cmd status
.\ijob.cmd logs -Once
.\ijob.cmd stop
.\ijob.cmd service disable
```

Installation verifies the wrapper hash and uses the per-instance virtual account `NT SERVICE\ijobA`, without storing an account password. It grants read/execute on this control directory and modification on this data home. The account also needs read/execute access to Java and the bundle. Service paths cannot contain `%`, which WinSW expands as environment variables. Installation leaves the service stopped and manual. Enable selects autostart; disable returns to manual without stopping an active service.

WinSW directly supervises foreground Java. Abnormal exits trigger retries after 10/30/60 seconds, then automatic attempts stop. One hour of normal operation resets the failure count. Normal stop first sends Ctrl+C and waits up to 120 seconds; timeout may force termination, so inspect logs and recovery rather than claiming graceful completion. Application logs use home/logs; size-limited wrapper logs use home/logs/service. Running means the process started; independently verify the current launch's runtimePhase=READY, login and task execution.

Access grants include configuration files and runtime directories whose ACL inheritance was disabled by setup. Existing inheritance protection and ownership remain unchanged. The complete directory tree is checked before granting access; links and junctions are rejected.

Stop and restart wait up to 150 seconds for Windows to confirm the service has stopped, because the wrapper's stop command returns after submitting the request. A timeout reports failure and preserves registration; restart does not start another process until stop completes. Stopping a service does not imply its running business tasks succeeded; inspect their records and external effects.

## Linux systemd

Provide Java 21, systemd, Bash and flock. Create a separate non-root system account per instance; it owns the data home and existing files and performs the initial native startup/stop. Program and control directories must be root-owned and not writable by group/others. Prepare accounts and permissions explicitly; installation never recursively takes ownership of existing data.

From the control directory, with an existing account ijob-a:

```sh
sudo ./ijob.sh service install --name ijob-a --user ijob-a --java-home /usr/lib/jvm/java-21-openjdk-amd64
sudo ./ijob.sh service enable
sudo ./ijob.sh start
sudo ./ijob.sh status
sudo ./ijob.sh logs --once
sudo ./ijob.sh stop
sudo ./ijob.sh service disable
```

Installation validates /etc/systemd/system/ijob-a.service and binds program/control/data identities and the unit digest. Foreground Java runs as the registered account; the configuration becomes group-readable while data ownership is unchanged. New units remain stopped and disabled. Enable/disable changes boot activation only. Failure restarts wait 10 seconds, with at most three starts within five minutes; diagnose limits before administrator reset-failed and restart.

Stop sends SIGTERM and waits 120 seconds without automatic SIGKILL. Java's normal signal exit code 143 is classified as a successful service exit. A timeout retains the process for diagnosis and is not successful shutdown. Running business tasks may fail when their process group terminates; inspect instance records and external effects. A clean service stop does not mean task success. Status shows ActiveState/SubState/MainPID/UnitFileState; application readiness is separate. Service/unified logs use this unit's journal, while application files remain at home/logs/ijob.log. Journal retention is controlled by host journald settings.

## Upgrade, rollback and uninstall

Unified start/stop/restart/status/logs detects service ownership and delegates to the service manager. Updated legacy entry points refuse to compete for a service-owned home. Do not remove ownership markers to bypass checks; older bundles lack this protection and must not operate concurrently.

Configure Update/update rejects program changes while registration exists. Stop and verify process exit, back up data/configuration, service uninstall, update bundle through controlled configuration, reinstall, restore the autostart choice, start and verify. Roll back in the same order while retaining original programs and control settings; database changes still follow [upgrade/rollback](../operations/upgrade-rollback.md).

Uninstall with `.\ijob.cmd service uninstall` on Windows or `sudo ./ijob.sh service uninstall` on Linux, after stopping. Data, application accounts, OS accounts/ACL grants, instance.conf and registration backups remain. Conflicting names, altered configuration/wrapper/unit hashes and mismatched identities refuse management. Partial installation failures retain ownership records; inspect this instance's service and failure point before repair rather than overwriting another service.

Before enabling boot startup, check enabled and disabled startup behavior, failure recovery, and account/history retention in a test environment. With multiple instances, also check that they operate independently. Docker uses Compose restart policies and Docker daemon startup; do not add native service management for the same containers.