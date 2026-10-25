# 旧版兼容入口 / Legacy entry points

新安装请使用程序包根目录的 `ijob.cmd`（Windows）或 `ijob.sh`（Linux / Docker），见[入口说明](../README.md)。本目录保留旧式命令；所有脚本依赖完整程序包，不能只复制 legacy 目录。

For new installations, use `ijob.cmd` (Windows) or `ijob.sh` (Linux / Docker) at the package root. See the [entry guide](../README.md). These compatibility scripts require the complete package; do not copy this directory alone.

| 环境 / Platform | 旧入口 / Legacy commands |
| --- | --- |
| Windows | initialize.cmd, start.cmd, stop.cmd, restart.cmd, status.cmd, logs.cmd |
| Linux 原生 / native | start.sh, stop.sh, status.sh, logs.sh |
| Linux Docker | start-docker.sh, stop-docker.sh; Compose ps/logs |

从程序包根目录调用 `legacy/start.cmd` 或 `bash ./legacy/start.sh`；也可以进入 legacy 目录调用。无论当前工作目录如何，程序资源从包根目录定位。未设置 IJOB_HOME 时，默认数据目录仍是程序包父目录下的 ijob-data（Windows）、ijob-data-linux（Linux）或 ijob-data-docker（Docker），不会改到 legacy 目录旁。自定义目录时，每次命令都须使用同一个 IJOB_HOME；IJOB_LANG 默认 en，zh 为中文提示。

Call `legacy/start.cmd` or `bash ./legacy/start.sh` from the package root, or enter this directory first. Program resources are located from the package root independently of the working directory. Without IJOB_HOME, data still defaults to ijob-data (Windows), ijob-data-linux (Linux), or ijob-data-docker (Docker) beside the program directory, never beside legacy. Every command must receive the same custom IJOB_HOME; IJOB_LANG defaults to en, with zh selecting Chinese messages.

已有控制入口升级：先用旧入口停止实例，再从新包运行 configure，选择新的控制目录、原数据目录和原语言。保留旧控制目录用于回退；不要仅修改旧 instance.conf 的 bundle 就跨越此次目录调整。此操作复用已有安装，不重新生成账号或数据库。具体步骤见包内中英文统一入口手册。

To upgrade an existing control entry, stop the instance through its old entry, then run configure from the new package with a new control directory and the original data home and language. Retain the old control directory for rollback; changing only bundle in an old instance.conf is insufficient for this layout change. Existing accounts and data are reused. Follow the bilingual instance-launcher manual supplied in the package.
