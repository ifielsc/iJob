# 每个实例只配置一次：统一启停入口

[English](../../en/installation/instance-launcher.md) · [手册首页](../README.md)

## 三个独立目录

程序目录保存完整交付包；控制目录保存统一入口与 instance.conf；数据目录保存 application.properties、秘密、数据库和日志。三个目录互不包含，父目录预先建立。每个实例各有一份控制目录和数据目录，可共享只读程序包。

```text
programs/ijob-1.0.0/       完整程序包
instances/a/              ijob.cmd + instance.ps1（Windows）或 ijob.sh（Linux/Docker）
                          instance.conf：本实例启动设置
data/a/                   本实例 IJOB_HOME，可能包含秘密
```

先在程序目录运行一次 configure，具体命令见 [Windows](windows.md)、[Linux](linux.md)、[Docker](docker.md)。configure 只创建新控制目录，不创建管理员、不初始化数据库、不启动程序；已有控制目录拒绝覆盖。已有数据目录也可登记，但首次 start 会校验其安装方式与完整性。没有创建控制入口时直接运行程序包里的 ijob start 会明确报错，不猜测原实例。

## 一份配置供全部命令使用

instance.conf 是 UTF-8 纯文本，每项一行，不使用引号、变量展开或 shell 表达式；Linux 使用 LF 换行。由 configure 自动生成，平时无需手填。

| 配置项 | 内容与修改规则 |
| --- | --- |
| version | 控制文件格式，当前为 1；不手改。不是产品版本。 |
| bundle | 完整程序目录的绝对路径；升级时停机后按升级流程修改。 |
| home | 本实例数据目录的绝对路径；修改不会搬迁数据，按备份恢复流程处理。 |
| lang | 启动提示语言，zh 或 en；默认 en，下一次命令读取生效。 |
| mode | Windows 为 windows；Linux 原生为 linux；Docker 为 docker。创建时选择，不通过改这一项迁移部署方式。 |

同一实例的 start、stop、restart、status、logs 都读取同一个文件；控制目录可从任何工作目录调用。已保存的 home/lang 优先于终端继承的 IJOB_HOME/IJOB_LANG，避免新窗口忘记设置或沿用另一个实例。每次运行显示所选数据目录；show 只显示启动设置，不输出应用密码或密钥。

| 动作 | 说明 |
| --- | --- |
| start | 自动初始化缺失的新安装并启动；已有安装复用配置。 |
| stop | 停止本实例，保留配置、数据和历史。 |
| restart | 原生先停再启；Docker 按该程序包的镜像锁重建并等待就绪，应用配置与 Compose 修改生效。 |
| status | 原生报告进程状态；Docker 显示本项目 Compose 状态。原生停止状态退出码为 3，Docker 的 ps 成功不代表服务健康。 |
| logs | 跟随日志；Ctrl+C 退出查看，不停止后台服务。 |
| logs -Once / logs --once | Windows / Linux 与 Docker：输出最近 200 行后返回，便于脚本与排障。 |
| show | 查看当前程序目录、数据目录、语言和运行方式。 |

所有命令使用相同操作系统账号；需要 Docker sudo 时对该实例各命令一致使用。Java 21/PATH、Docker 权限等系统前提仍须准备；它们不是 instance.conf 的属性。控制文件不放数据库口令。数据目录仍按私密配置要求保护。

## 没有设置环境变量时，旧脚本会怎样

仍可直接使用旧 start.cmd、start.sh、start-docker.sh。未设置 IJOB_HOME 时，按程序包父目录选择以下默认位置，不会自动查找曾经使用过的自定义目录：

| 旧入口 | 默认数据目录 |
| --- | --- |
| Windows start.cmd | 程序包父目录/ijob-data |
| Linux start.sh | 程序包父目录/ijob-data-linux |
| Docker start-docker.sh | 程序包父目录/ijob-data-docker |

未设置 IJOB_LANG 时默认英文，中文值为 zh。它控制初始化与启动提示，不改变网页语言或业务通知语言；部分底层工具错误与技术日志保留英文。旧脚本启停时仍需保证相同的环境变量；日常推荐使用上述持久入口。

setup JAR 支持 --home/--lang 参数，但单次执行不为旧脚本保存位置。这两个启动值不能靠 application.properties 定位自己，因为启动器必须先知道数据目录才能读取该文件。新的 instance.conf 正是独立的持久启动设置，不能与平台配置混用。

## 升级与定位

先执行 show 确认实例，再 stop，完成[备份及升级](../operations/upgrade-rollback.md)。在保留 home 的前提下修改 instance.conf 的 bundle 为新完整包，复核控制脚本兼容性后 start，核对登录、历史与无副作用任务。不能在程序运行时改 home 或 bundle，否则状态与日志可能指向别处。路径必须是规范绝对路径，不使用符号链接/目录联接；配置缺项、重复键或未知键会拒绝执行。
