# 每个实例只配置一次：统一启停入口

[English](../../en/installation/instance-launcher.md) · [手册首页](../README.md)

## 三个独立目录

程序目录保存完整交付包；控制目录保存统一入口与 instance.conf；数据目录保存 application.properties、秘密、数据库和日志。三个目录互不包含，父目录预先建立。每个实例各有一份控制目录和数据目录，可共享只读程序包。

```text
programs/ijob-1.0.0/       完整程序包
instances/a/              ijob.cmd + support/instance.ps1（Windows）或 ijob.sh（Linux/Docker）
                          instance.conf：本实例启动设置
data/a/                   本实例 IJOB_HOME，可能包含秘密
```

先在程序目录运行一次 configure，具体命令见 [Windows](windows.md)、[Linux](linux.md)、[Docker](docker.md)。configure 只创建新控制目录，不创建管理员、不初始化数据库、不启动程序；已有控制目录拒绝覆盖。已有数据目录也可登记，但首次 start 会校验其安装方式与完整性。没有创建控制入口时直接运行程序包里的 ijob start 会明确报错，不猜测原实例。

## 交互设置与受控修改

在完整程序包中执行以下命令，按提示选择目录与语言。Linux 还可选择 linux/docker，Windows 固定为 windows。向导先说明默认值，校验后显示程序、控制、数据目录以及语言和模式；只有输入 `yes` 才保存，空白确认默认取消。任意输入步骤使用 `!` 或结束输入均取消，不初始化数据或调用 setup。

```powershell
.\ijob.cmd configure -Interactive -Lang zh
```

```sh
bash ./ijob.sh configure --interactive --lang zh
```

仅在交互终端中，无目录参数的 configure 自动进入向导。自动化继续使用完整参数；重定向输入时必须显式选择 Interactive/interactive，否则拒绝等待输入。默认控制目录为程序包父目录下的 ijob-instance，默认数据目录为同级 ijob-data；这些是向导建议值，旧入口的既有默认位置不变。父目录必须已经存在，目录必须互不包含；取消或校验失败不会创建半份控制目录。提示语言默认 en，与网页及通知语言无关。

已有控制目录支持停机后修改程序目录和提示语言；先通过原入口 stop/status 确认停止。在控制目录执行：

```powershell
.\ijob.cmd configure -Update -Interactive
# 非交互示例：.\ijob.cmd configure -Update -Lang zh -Bundle D:/ijob/programs/ijob-1.0.0-new
```

```sh
./ijob.sh configure --update --interactive
# 非交互示例：./ijob.sh configure --update --lang zh --bundle /srv/ijob/programs/ijob-1.0.0-new
```

修改前核对当前实例已停止，校验完整新包，自动将原 instance.conf 原始字节保存在控制目录的 instance.conf.backup-*，再原子替换配置。启动、停止与修改在同一控制目录互斥；修改期间不要从旧控制目录或 legacy 入口并发操作。不会移动或覆盖数据，不会改写 application.properties 或重新生成密钥。home、mode 和控制目录保持固定；数据/模式迁移应按备份恢复流程新建控制目录。恢复启动设置时先停止实例，核对备份和程序兼容性后恢复原文件；数据与应用回退仍按升级手册执行。

旧版本控制脚本不因程序包更新而自动获得此功能。按文末流程用新包创建新的控制目录，再检查 `show`、`status`、启动和停止是否指向预期实例；确认数据目录、配置和历史记录保持正确。

## 一份配置供全部命令使用

instance.conf 是 UTF-8 纯文本，每项一行，不使用引号、变量展开或 shell 表达式；Linux 使用 LF 换行。由 configure 自动生成，平时无需手填。

| 配置项 | 内容与修改规则 |
| --- | --- |
| version | 控制文件格式，当前为 1；不手改。不是产品版本。 |
| bundle | 完整程序目录的绝对路径；停机后用 configure Update/update 受控修改，升级后按升级流程核对就绪、登录和数据。 |
| home | 本实例数据目录的绝对路径；受控修改不变更它，数据迁移按备份恢复流程建立新控制目录。 |
| lang | 启动提示语言，zh 或 en；默认 en，受控修改后下一次命令读取生效。 |
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

仍可直接使用旧 legacy/start.cmd、legacy/start.sh、legacy/start-docker.sh。未设置 IJOB_HOME 时，按程序包父目录选择以下默认位置，不会自动查找曾经使用过的自定义目录：

| 旧入口 | 默认数据目录 |
| --- | --- |
| Windows legacy/start.cmd | 程序包父目录/ijob-data |
| Linux legacy/start.sh | 程序包父目录/ijob-data-linux |
| Docker legacy/start-docker.sh | 程序包父目录/ijob-data-docker |

未设置 IJOB_LANG 时默认英文，中文值为 zh。它控制初始化与启动提示，不改变网页语言或业务通知语言；部分底层工具错误与技术日志保留英文。旧脚本启停时仍需保证相同的环境变量；日常推荐使用上述持久入口。

setup JAR 支持 --home/--lang 参数，但单次执行不为旧脚本保存位置。这两个启动值不能靠 application.properties 定位自己，因为启动器必须先知道数据目录才能读取该文件。新的 instance.conf 正是独立的持久启动设置，不能与平台配置混用。

## 升级与定位

先执行 show 确认实例，再 stop，完成[备份及升级](../operations/upgrade-rollback.md)。跨越旧平铺布局时按下文创建新控制入口；相同布局且控制脚本兼容时，可在保留 home 的前提下修改 bundle 后 start，核对登录、历史与无副作用任务。不能在程序运行时改 home 或 bundle，否则状态与日志可能指向别处。路径必须是规范绝对路径，不使用符号链接/目录联接；配置缺项、重复键或未知键会拒绝执行。

## 程序包目录与旧入口

```text
ijob-1.0.0/
  ijob.cmd / ijob.sh    推荐入口：先 configure，再使用实例控制目录
  legacy/              旧式 initialize/start/stop/status/logs 等入口
  support/             内部辅助脚本和 logging-native.xml；不直接运行
  docs/                中英文手册
  JAR、web-dist 等      程序资源
```

legacy 下的命令从程序包根目录调用，例如 `.\legacy\start.cmd`、`bash ./legacy/start.sh`、`sudo bash ./legacy/start-docker.sh`，也可进入 legacy 目录调用。必须保留完整包；程序资源始终按包根目录定位。旧入口默认数据位置仍为程序包父目录下的 ijob-data / ijob-data-linux / ijob-data-docker，不会变成程序目录或 legacy 目录下的新安装。

Windows 新控制目录只有 ijob.cmd、instance.conf 和 support/instance.ps1；Linux/Docker 新控制目录为 ijob.sh 和 instance.conf。

## 从原平铺脚本包升级

旧控制脚本不知道新的 legacy/support 布局，**不能只把旧 instance.conf 的 bundle 改为新包路径**。推荐保留旧控制目录，创建一个指向原数据的新控制目录：

1. 通过旧控制目录执行 show，记下原 home、lang、mode；确认路径和实例身份。
2. 通过旧控制目录 stop，核对停止状态，并按备份流程备份原数据与配置。
3. 解压新完整包到另一个程序目录，在新包根目录执行 configure。控制目录使用新路径，home、lang 和 mode 沿用第 1 步的值。

Windows 示例（在新程序包根目录）：

```powershell
.\ijob.cmd configure -Directory D:/ijob/instances/a-layout -Home D:/ijob-data/a -Lang zh
```

Linux 原生示例；Docker 将 mode 改为 docker，并按原实例账号一致使用 sudo：

```sh
bash ./ijob.sh configure --directory /srv/ijob/instances/a-layout --home /srv/ijob-data/a --lang zh --mode linux
```

4. 从新控制目录执行 show、start、status、logs，核对原账号、任务定义和历史。已有安装会复用，不再次初始化或生成新密钥。切勿同时通过新旧入口启动同一数据目录。
5. 将日常快捷方式/运维记录指向新控制目录。若仅回退本次脚本布局，先通过新入口 stop，再使用保留的旧入口启动原程序包；涉及其他程序或数据库变更仍遵循升级回退手册。

新控制脚本也识别旧包的平铺管理文件，便于受控回退；这不代表旧控制脚本自动识别新包。旧控制目录与旧包保留，不自动删除。
