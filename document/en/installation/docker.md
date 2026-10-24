# Linux Docker installation and lifecycle

[简体中文](../../zh-CN/installation/docker.md) · [Manual home](../README.md)

## Preparation and first start

Use Linux Docker Engine, Compose v2 and the complete Docker bundle. Windows native scripts are not Windows-container deployment. Extract the bundle, enter its directory, and select an independent canonical absolute path without symlinks:

```sh
export IJOB_HOME=/srv/ijob-data/docker-a
export IJOB_LANG=en
./start-docker.sh
```

The launcher loads missing offline images, verifies exact image IDs, initializes interactively and waits for Compose readiness. Initialization creates the administrator, AES key, application.properties and compose.yaml. Container UID/GID 10001 reads configuration and writes runtime; retain restricted permissions. Default address: `http://127.0.0.1:18080`. Configure a separate ingress and TLS for external access.

## Status

```sh
docker compose -f "$IJOB_HOME/compose.yaml" ps
```

backend and web should be healthy; started containers do not prove task completion.

## Logs

```sh
docker compose -f "$IJOB_HOME/compose.yaml" logs --tail 200 -f backend web
```

Ctrl+C exits log following.

## Stop and start again

To stop:

```sh
./stop-docker.sh
```

Start again with `./start-docker.sh`; existing configuration/data are preserved. Do not use `down -v` for daily shutdown.

## Recreate after configuration changes

Edit application.properties for platform settings and this home's compose.yaml for host ports, mounts and container resources. Keep internal HTTP at `0.0.0.0:8080`; change the published host mapping. After backup and validation:

```sh
./start-docker.sh --recreate
```

This entry retains the bundle's image lock and remounts configuration files replaced by editors. Do not deploy a new candidate using an unlocked `compose up`. Generated first-run Compose differs from repository advanced templates: for example, `IJOB_HTTP_BIND` belongs to the advanced template and does not rewrite an existing loopback mapping. See [Deployment and Agent configuration](../configuration/deployment-agent.md).
