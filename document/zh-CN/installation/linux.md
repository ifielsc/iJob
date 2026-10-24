# Linux 原生安装与启停

[English](../../en/installation/linux.md) · [手册首页](../README.md)

## 准备

解压完整 native 包到独立程序目录，在该目录打开终端。安装 Java 21，并设置 JAVA_HOME/PATH。需要 Bash、flock、常用 GNU 工具和 /proc；为 .sh 文件保留执行权限。

```sh
export IJOB_HOME=/srv/ijob-data/a
export IJOB_LANG=zh
```

IJOB_HOME 使用独立绝对路径；新终端也必须指向同一个 home。它含有秘密，不放源码内。以下命令均在程序包目录执行。

## 首次安装与启动

```sh
./start.sh
```

`start` 自动调用初始化。按提示输入新管理员账号、密码和数据库类型；入门可选 SQLite。已有安装不会被覆盖。等待就绪后访问 `http://127.0.0.1:18080`（改过端口则用新端口）。Java 在后台运行；启动超时仍可能保留进程，先查状态和日志。

只想提前生成配置而不启动时，才单独运行：

```sh
java -jar ijob-setup-tool-1.0.0.jar init --mode linux --bundle "$PWD" --home "$IJOB_HOME"
```

## 日常：查看状态

```sh
./status.sh
```

状态命令核验本安装拥有的进程身份；还要到工作台检查节点和实际业务运行。

## 日常：查看日志

```sh
./logs.sh
```

Ctrl+C 退出日志跟随，不停止应用。日志位于 IJOB_HOME/logs；业务实例日志在运行中心查看。

## 日常：停止

```sh
./stop.sh
```

等待退出后再次查询状态。先确认维护窗口和在途业务，停止平台不能撤销已完成的外部写入。

## 修改配置与重启

编辑 `IJOB_HOME/application.properties`，保持一键一行，按[配置手册](../configuration.md)检查格式，然后：

```sh
./stop.sh
./start.sh
```

再次启动也直接用 `start`，不必重新初始化。升级和恢复请走[运维流程](../operations.md)，不要覆盖已有 home。
