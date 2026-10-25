# Windows 原生：配置一次与日常启停

[English](../../en/installation/windows.md) · [手册首页](../README.md)

## 1. 准备程序和目录

解压完整 native 包，安装 Java 21 并设置 JAVA_HOME/PATH。在程序包目录打开 PowerShell。先创建控制目录和数据目录的父目录：

```powershell
New-Item -ItemType Directory -Force D:/ijob/instances, D:/ijob-data | Out-Null
```

## 2. 为本实例配置一次

```powershell
.\ijob.cmd configure -Directory D:/ijob/instances/a -Home D:/ijob-data/a -Lang zh
```

此操作创建控制入口，不启动程序。程序位置自动记录，目录和语言保存在 `D:/ijob/instances/a/instance.conf`。无需设置 IJOB_HOME/IJOB_LANG；旧环境变量也不会覆盖这份实例设置。另一个实例使用不同控制目录和数据目录，同机还需独立端口。

## 3. 首次启动

```powershell
cd D:/ijob/instances/a
.\ijob.cmd start
```

start 自动初始化新安装。按提示输入独立管理员账号、密码和数据库类型，入门可选 SQLite；无需先运行 legacy/initialize.cmd。已有有效安装会复用配置。默认访问 `http://127.0.0.1:18080`，改过端口则用新端口。后台隐藏运行，关闭当前终端不会停止应用。

## 4. 查看配置与状态

```powershell
.\ijob.cmd show
.\ijob.cmd status
```

状态会核验进程身份；仍需到工作台检查节点就绪和业务运行。已停止状态退出码为 3，供自动化区分。

## 5. 查看日志

```powershell
.\ijob.cmd logs
.\ijob.cmd logs -Once
```

第一条跟随日志，Ctrl+C 退出查看；第二条输出最近 200 行后返回。应用日志在数据目录/logs，业务实例日志在运行中心。

## 6. 停止、再次启动和修改配置

```powershell
.\ijob.cmd stop
.\ijob.cmd status
.\ijob.cmd start
```

按[配置手册](../configuration.md)备份并修改数据目录中的 application.properties 后，执行：

```powershell
.\ijob.cmd restart
```

新开终端也可直接运行 `D:/ijob/instances/a/ijob.cmd status`，无需重新设置变量。目录/语言及升级规则见[统一入口](instance-launcher.md)。旧 start/initialize/stop/status/logs/restart.cmd 仍兼容，直接调用它们时遵循该页的环境变量与默认目录规则。
