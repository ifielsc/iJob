# Linux native installation and lifecycle

[简体中文](../../zh-CN/installation/linux.md) · [Manual home](../README.md)

## Preparation

Extract the complete native bundle into its own program directory and open a terminal there. Install Java 21 and set JAVA_HOME/PATH. Bash, flock, standard GNU tools and /proc are required; retain executable permissions on .sh files.

```sh
export IJOB_HOME=/srv/ijob-data/a
export IJOB_LANG=en
```

Use an independent absolute IJOB_HOME path and set the same home in later terminals. It contains secrets and must stay outside source. Run all commands below in the bundle directory.

## First installation and start

```sh
./start.sh
```

`start` invokes initialization automatically. Enter a new administrator, password and database type; SQLite is suitable for the first walkthrough. Existing installations are preserved. After readiness, open `http://127.0.0.1:18080` or your configured port. Java runs in the background. A startup timeout may leave the process running; inspect status and logs.

To generate configuration without starting the application, use the optional command:

```sh
java -jar ijob-setup-tool-1.0.0.jar init --mode linux --bundle "$PWD" --home "$IJOB_HOME"
```

## Daily use: status

```sh
./status.sh
```

The command verifies the process identity owned by this installation. Also inspect nodes and actual work in the dashboard.

## Daily use: logs

```sh
./logs.sh
```

Ctrl+C exits log following without stopping the application. Application logs are in IJOB_HOME/logs; business-instance logs are in the run center.

## Daily use: stop

```sh
./stop.sh
```

Wait for exit and check status again. Agree a maintenance window and review active work first; stopping the platform cannot undo external writes.

## Change configuration and restart

Edit `IJOB_HOME/application.properties`, keep each key unique, and check formats using [Configuration](../configuration.md). Then:

```sh
./stop.sh
./start.sh
```

Later starts also use `start`; do not reinitialize. Follow [Operations](../operations.md) for upgrades/restores and preserve the existing home.
