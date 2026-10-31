# Linux Docker: configure once and manage the instance

[简体中文](../../zh-CN/installation/docker.md) · [Manual home](../README.md)

## 1. Prerequisites and one-time configuration

Prepare Linux Docker Engine, the Compose plugin providing the `docker compose` command, Bash and a complete Docker bundle. Check the installed versions with `docker version` and `docker compose version`. Use an authorized account; these examples consistently use sudo. From the program directory:

```sh
sudo mkdir -p /srv/ijob/instances /srv/ijob-data
sudo bash ./ijob.sh configure --directory /srv/ijob/instances/docker-a --home /srv/ijob-data/docker-a --lang en --mode docker
```

Parents must be writable by the execution account and none of the three directories may contain another. configure only creates the instance control entry and instance.conf; it does not start the application. Repeated IJOB_HOME/IJOB_LANG exports are unnecessary.

## 2. First startup

```sh
sudo /srv/ijob/instances/docker-a/ijob.sh start
```

The launcher loads missing offline images by locked image ID, initializes interactively and waits for Compose readiness. It creates application.properties and compose.yaml; container-accessible configuration/runtime belong to UID/GID 10001 with restricted permissions. Open `http://127.0.0.1:18080` or your configured port.

## 3. Inspect settings and status

```sh
sudo /srv/ijob/instances/docker-a/ijob.sh show
sudo /srv/ijob/instances/docker-a/ijob.sh status
```

status shows this instance project in Compose; backend/web should be healthy. A successful command does not prove containers are running or business execution succeeded.

## 4. View logs

```sh
sudo /srv/ijob/instances/docker-a/ijob.sh logs
sudo /srv/ijob/instances/docker-a/ijob.sh logs --once
```

The first follows logs; Ctrl+C stops viewing, not the application. The second prints the latest 200 lines and returns. Docker logs come from this project’s backend/web.

## 5. Stop, start again and apply changes

```sh
sudo /srv/ijob/instances/docker-a/ijob.sh stop
sudo /srv/ijob/instances/docker-a/ijob.sh start
```

After backing up and editing application.properties in the data home using [Configuration](../configuration.md):

```sh
sudo /srv/ijob/instances/docker-a/ijob.sh restart
```

restart recreates this project using the bundle’s image locks and waits for readiness without removing volumes. Compose port/mount/resource changes are applied too. Keep container HTTP at 0.0.0.0:8080; assign separate host ports on the same machine. Do not use down -v for daily stopping. Generated Compose differs from advanced repository templates; variables such as IJOB_HTTP_BIND do not automatically modify generated configuration.

See the [unified entry](instance-launcher.md) for paths, language, defaults and upgrades. Legacy legacy/start-docker.sh / legacy/stop-docker.sh and Compose commands remain supported with consistent IJOB_HOME.

For multiple instances on one host, replace `${IJOB_HTTP_PORT:-18080}` in compose.yaml web.ports with each fixed port (for example `127.0.0.1:18081:8080`), then restart through that instance entry. A temporary variable supplied only during initialization does not persist the port.

## 6. Failure recovery and boot startup

Generated backend/web services use `restart: unless-stopped`. Enable Docker itself at boot according to the host's operating procedures; existing containers that were not manually stopped can then recover with Docker. After this instance's `stop` command, restarting Docker or rebooting the host keeps that instance stopped until an explicit `start`. This follows the [Docker restart policy](https://docs.docker.com/engine/containers/start-containers-automatically/).

Initial `start` and explicit `restart` use Compose to wait for backend health before starting web. When the Docker daemon restores existing containers, each follows its own restart policy; the Compose dependency declaration does not guarantee strict ordering at every boot. See [Compose startup order](https://docs.docker.com/compose/how-tos/startup-order/). After recovery, verify backend/web health, an actual login and a repeatable business task, then check history and data integrity.

After recovery or container recreation, confirm that this instance retains its definitions, history and configuration, and that other instances on the host keep their intended running or stopped state. For writing tasks that were active before recovery, inspect the actual target-system results before retrying. Container recovery does not make external writes transactional; business operations still need idempotency and recovery design.
MySQL/Oracle require user-supplied JDBC drivers; see [driver installation](../installation.md#jdbc-drivers). PostgreSQL/SQL Server/SQLite drivers remain included.
