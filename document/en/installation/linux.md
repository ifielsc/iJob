# Linux native: configure once and manage the instance

[简体中文](../../zh-CN/installation/linux.md) · [Manual home](../README.md)

## 1. Prerequisites and one-time configuration

Prepare Java 21, Bash, util-linux flock and a complete native bundle. Use the dedicated OS account for this instance. From the program directory:

```sh
mkdir -p /srv/ijob/instances /srv/ijob-data
bash ./ijob.sh configure --directory /srv/ijob/instances/a --home /srv/ijob-data/a --lang en --mode linux
```

Parents must be writable by the execution account and none of the three directories may contain another. configure only creates the instance control entry and instance.conf; it does not start the application. Repeated IJOB_HOME/IJOB_LANG exports are unnecessary.

## 2. First startup

```sh
/srv/ijob/instances/a/ijob.sh start
```

Initialization is interactive, followed by background startup. Enter an independent administrator/password and database type; SQLite is suitable for a single-instance introduction. Existing installations retain configuration without repeating initialization. Open `http://127.0.0.1:18080` or your configured port.

## 3. Inspect settings and status

```sh
/srv/ijob/instances/a/ijob.sh show
/srv/ijob/instances/a/ijob.sh status
```

status verifies the owned process and exits 3 when stopped. Also check node readiness in the workbench.

## 4. View logs

```sh
/srv/ijob/instances/a/ijob.sh logs
/srv/ijob/instances/a/ijob.sh logs --once
```

The first follows logs; Ctrl+C stops viewing, not the application. The second prints the latest 200 lines and returns. Native logs are under data-home/logs; business instance logs are in Runs.

## 5. Stop, start again and apply changes

```sh
/srv/ijob/instances/a/ijob.sh stop
/srv/ijob/instances/a/ijob.sh start
```

After backing up and editing application.properties in the data home using [Configuration](../configuration.md):

```sh
/srv/ijob/instances/a/ijob.sh restart
```

restart stops then starts, retaining data and history. Do not switch OS accounts to take over an existing process.

See the [unified entry](instance-launcher.md) for paths, language, defaults and upgrades. Legacy legacy/start.sh / legacy/stop.sh / legacy/status.sh / legacy/logs.sh remain supported with consistent IJOB_HOME.
