# Linux 原生：配置一次与日常启停

[English](../../en/installation/linux.md) · [手册首页](../README.md)

## 1. 准备与一次性配置

准备 Java 21、Bash、util-linux 的 flock 和完整 native 包；使用本实例的专属操作系统账号。在程序包目录执行：

```sh
mkdir -p /srv/ijob/instances /srv/ijob-data
bash ./ijob.sh configure --directory /srv/ijob/instances/a --home /srv/ijob-data/a --lang zh --mode linux
```

父目录必须可由执行账号写入；三个目录互不包含。configure 只生成本实例控制入口及 instance.conf，不启动程序。之后无需每次 export IJOB_HOME/IJOB_LANG。

## 2. 首次启动

```sh
/srv/ijob/instances/a/ijob.sh start
```

自动交互初始化，之后后台运行。输入独立管理员账号、密码和数据库类型；SQLite 可用于单实例入门。已有安装会保留配置，不必重复初始化。默认访问 `http://127.0.0.1:18080`，自定义端口使用实际地址。

## 3. 查看配置与状态

```sh
/srv/ijob/instances/a/ijob.sh show
/srv/ijob/instances/a/ijob.sh status
```

status 核验本实例拥有的进程；已停止时退出码为 3。还需核对工作台节点是否就绪。

## 4. 查看日志

```sh
/srv/ijob/instances/a/ijob.sh logs
/srv/ijob/instances/a/ijob.sh logs --once
```

第一条跟随日志，Ctrl+C 退出查看，不停止应用；第二条输出最近 200 行后返回。原生日志在数据目录/logs；业务实例日志在运行中心。

## 5. 停止、再次启动与配置变更

```sh
/srv/ijob/instances/a/ijob.sh stop
/srv/ijob/instances/a/ijob.sh start
```

备份并按[配置手册](../configuration.md)修改数据目录里的 application.properties 后：

```sh
/srv/ijob/instances/a/ijob.sh restart
```

restart 先停止再启动，保留数据与历史。不要切换操作系统账号接管现有进程。

目录、语言、默认值和升级方式见[统一入口](instance-launcher.md)。旧 start.sh / stop.sh / status.sh / logs.sh 仍支持；直接调用时需保持相同 IJOB_HOME。
