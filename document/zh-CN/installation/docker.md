# Linux Docker 安装与启停

[English](../../en/installation/docker.md) · [手册首页](../README.md)

## 准备与首次启动

本章使用 Linux Docker Engine、Compose v2 和完整 Docker 包；不把 Windows 原生脚本当成 Windows 容器部署。解压后进入程序目录，设置独立规范绝对路径（不经符号链接）：

```sh
export IJOB_HOME=/srv/ijob-data/docker-a
export IJOB_LANG=zh
./start-docker.sh
```

启动器加载缺失的离线镜像，校验精确镜像 ID，交互初始化，再等待 Compose 就绪。首次初始化创建管理员、AES 密钥、application.properties 和 compose.yaml。配置和 runtime 由容器 UID/GID 10001 读取/写入，保留受限权限。默认地址 `http://127.0.0.1:18080`；外部访问需独立配置入口和 TLS。

## 查看状态

```sh
docker compose -f "$IJOB_HOME/compose.yaml" ps
```

backend 和 web 应健康；容器启动不等于业务任务已完成。

## 查看日志

```sh
docker compose -f "$IJOB_HOME/compose.yaml" logs --tail 200 -f backend web
```

Ctrl+C 退出日志跟随。

## 停止与再次启动

停止时运行：

```sh
./stop-docker.sh
```

再次启动运行 `./start-docker.sh`，已有配置和数据保留。不要用 `down -v` 当作日常停止命令。

## 修改配置后重建

普通平台值编辑 application.properties；宿主端口、挂载和容器资源编辑同一 home 的 compose.yaml。容器内 HTTP 保持 `0.0.0.0:8080`，修改宿主端口映射即可。完成备份与检查后：

```sh
./start-docker.sh --recreate
```

这个入口继续使用对应包的镜像锁；重建也会重新挂载被编辑器替换的配置文件。不要只用未带镜像锁的 `compose up` 来部署新候选。第一轮生成的 Compose 与仓库高级 Compose 模板不同；如 `IJOB_HTTP_BIND` 只属于高级模板，不会自动修改已生成的 loopback 映射。详见[部署与 Agent 配置](../configuration/deployment-agent.md)。
