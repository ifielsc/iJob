# Linux Docker：配置一次与日常启停

[English](../../en/installation/docker.md) · [手册首页](../README.md)

## 1. 准备与一次性配置

准备 Linux Docker Engine、Compose v2、Bash 和完整 Docker 包。使用有权限的账号，下面示例统一使用 sudo。在程序包目录执行：

```sh
sudo mkdir -p /srv/ijob/instances /srv/ijob-data
sudo bash ./ijob.sh configure --directory /srv/ijob/instances/docker-a --home /srv/ijob-data/docker-a --lang zh --mode docker
```

父目录必须可由执行账号写入；三个目录互不包含。configure 只生成本实例控制入口及 instance.conf，不启动程序。之后无需每次 export IJOB_HOME/IJOB_LANG。

## 2. 首次启动

```sh
sudo /srv/ijob/instances/docker-a/ijob.sh start
```

按包内镜像 ID 锁加载缺失的离线镜像，交互初始化，再等待 Compose 就绪。新建 application.properties 与 compose.yaml；容器访问的配置/runtime 归 UID/GID 10001，保留受限权限。默认访问 `http://127.0.0.1:18080`，自定义端口使用实际地址。

## 3. 查看配置与状态

```sh
sudo /srv/ijob/instances/docker-a/ijob.sh show
sudo /srv/ijob/instances/docker-a/ijob.sh status
```

status 显示本实例项目的 Compose 状态；backend/web 应健康。命令返回成功不等于容器正在运行或业务任务成功。

## 4. 查看日志

```sh
sudo /srv/ijob/instances/docker-a/ijob.sh logs
sudo /srv/ijob/instances/docker-a/ijob.sh logs --once
```

第一条跟随日志，Ctrl+C 退出查看，不停止应用；第二条输出最近 200 行后返回。Docker 日志来自本项目 backend/web。

## 5. 停止、再次启动与配置变更

```sh
sudo /srv/ijob/instances/docker-a/ijob.sh stop
sudo /srv/ijob/instances/docker-a/ijob.sh start
```

备份并按[配置手册](../configuration.md)修改数据目录里的 application.properties 后：

```sh
sudo /srv/ijob/instances/docker-a/ijob.sh restart
```

restart 使用该程序包的镜像锁重建本项目并等待就绪，不删除卷；Compose 中端口、挂载或资源变更也随重建应用。容器内 HTTP 保持 0.0.0.0:8080，同机各实例改独立宿主端口。不要用 down -v 作为日常停止命令。生成的 Compose 与仓库高级模板不同，IJOB_HTTP_BIND 等高级模板变量不会自动修改生成配置。

目录、语言、默认值和升级方式见[统一入口](instance-launcher.md)。旧 legacy/start-docker.sh / legacy/stop-docker.sh 和 Compose 命令仍支持；需要每次保持相同 IJOB_HOME。

同机多实例请把 compose.yaml 的 web.ports 中 `${IJOB_HTTP_PORT:-18080}` 改为各实例固定端口（例如 `127.0.0.1:18081:8080`），再用本实例入口 restart。只在初始化阶段设置临时环境变量不会将端口保存下来。

## 6. 异常恢复与开机启动

生成的 backend/web 使用 `restart: unless-stopped`。Docker 服务本身需按宿主机的运维要求启用开机启动；已有且未手动停止的容器才能随 Docker 恢复。手动执行本实例 `stop` 后，Docker 服务重启或宿主机重启都不会自动启动该实例，需要再次执行 `start`。这是 [Docker 重启策略](https://docs.docker.com/engine/containers/start-containers-automatically/)的行为。

首次 `start` 和显式 `restart` 由 Compose 等待 backend 健康后启动 web；Docker 守护进程恢复已有容器时，各容器按各自重启策略恢复，不能把 Compose 的依赖声明当作每次开机的严格启动顺序。详见 [Compose 启动顺序](https://docs.docker.com/compose/how-tos/startup-order/)。恢复后检查 backend/web 健康状态、实际登录和一项可重复的业务任务，再确认历史记录及数据完整。

本轮独立 Linux VM 已实测两个隔离实例：backend 进程异常退出后恢复、Docker 服务重启、VM 重启，以及入口 `restart` 重建容器。运行实例恢复后登录和任务执行成功，手动停止的另一个实例跨服务与 VM 重启保持停止；容器重建保留任务历史，实例配置文件的 SHA-256 保持一致。此结果不代表断电时外部系统写入自动具备事务性；业务写入仍需自己的幂等与恢复方案。
