# Windows 服务与 systemd 托管

[English](../../en/installation/services.md) · [手册首页](../README.md)

先按[统一入口](instance-launcher.md)配置实例，完成首次初始化、启动检查和停止。服务启动不询问账号、不调用 setup，也不重置已有秘密。每个实例使用独立服务名、控制目录、数据目录和端口；程序目录可只读共享。

当前为新增实现的验收说明：本地契约检查与 VM 真实运行结果分别记录。Windows VM 安装/重启验收尚待完成；不能将生成 XML 或本地测试当作 Windows 服务实测。

Linux 独立 VM 已验证双实例登录和任务执行、停止隔离、主进程异常重启、两次 VM 重启中的启用/禁用自启，以及卸载重装后的历史保留。部署脚本候选之间的升级和回退也已实测，应用配置与秘密文件逐字节保持一致；这些候选的编译代码相同，不能据此声称已验证数据库版本迁移。结果绑定本轮程序与环境，最终重建候选仍需复验。

## Windows

使用 Java 21 和 .NET Framework 4。服务宿主固定为 [WinSW 2.12.0 的 WinSW.NET4.exe](https://github.com/winsw/winsw/releases/tag/v2.12.0)，MIT 许可全文随包保存在 support/WinSW-LICENSE.txt。可执行文件单独下载，不进源码；从 support/winsw.json 核对官方地址、长度和 SHA-256。当前 SHA-256 为 `923111c7142b3dc783a3c722b19b8a21bcb78222d7a136ac33f0ca8a29f4cb66`，这是官方 HTTPS 下载字节的固定摘要，不是上游签名；该文件未带 Authenticode 签名。

从控制目录操作。服务名以 ijob 开头，后接 1–40 个英文字母或数字。可先生成不安装服务的 XML 预览；实际管理使用管理员终端：

```powershell
.\ijob.cmd service preview -ServiceName ijobA -JavaHome D:/tools/jdk-21 -OutputDirectory D:/ijob-preview/a
.\ijob.cmd service install -ServiceName ijobA -JavaHome D:/tools/jdk-21 -Wrapper D:/downloads/WinSW.NET4.exe
.\ijob.cmd service enable
.\ijob.cmd start
.\ijob.cmd status
.\ijob.cmd logs -Once
.\ijob.cmd stop
.\ijob.cmd service disable
```

安装时验证包装器哈希，使用每实例 `NT SERVICE\ijobA` 虚拟账号，不保存服务账号密码；为其授予本控制目录读取/执行和本数据目录修改权限。Java 和程序包必须可由该账号读取执行。服务路径不能含 `%`，防止 WinSW 把路径片段作为环境变量展开。服务安装后为手动启动且保持停止；enable 改为开机自启，disable 改回手动，不停止当前运行。

WinSW 直接监督 Java 前台进程。异常退出按 10/30/60 秒重启，随后停止自动尝试；连续正常运行一小时后重置失败计数。正常停止先发送 Ctrl+C 并等待最多 120 秒；超时可能强制终止，必须检查日志及业务恢复，不能视为优雅停止。应用日志在 home/logs，包装器日志在 home/logs/service，按大小保留有限文件。服务 Running 只表示进程已启动，应用就绪仍需确认本次启动的 runtimePhase=READY 及登录/任务运行。

## Linux systemd

准备 Java 21、systemd、Bash 和 flock。为每个实例建立独立、非 root 的系统账号；数据目录及已有数据应由它拥有，首次原生启动/停止也使用该账号。程序目录和控制目录必须由 root 拥有且不可被组或其他用户写入。账号和权限准备应在安装前显式完成，安装器不会递归接管既有数据。

在控制目录执行，示例账号 ijob-a 已存在：

```sh
sudo ./ijob.sh service install --name ijob-a --user ijob-a --java-home /usr/lib/jvm/java-21-openjdk-amd64
sudo ./ijob.sh service enable
sudo ./ijob.sh start
sudo ./ijob.sh status
sudo ./ijob.sh logs --once
sudo ./ijob.sh stop
sudo ./ijob.sh service disable
```

安装器校验并写入 /etc/systemd/system/ijob-a.service，固定程序/控制/数据身份及 unit 哈希。应用以前台 Java 运行，使用注册账号；配置文件仅开放该组读取，数据目录不自动换所有者。新服务保持停止、未 enable。enable/disable 只切换下次开机自启，不隐式启动或停止。异常退出 10 秒后重启，5 分钟内最多启动 3 次；达到限制时先排障，再由管理员按 systemd 流程 reset-failed 后启动。

停止发送 SIGTERM，等待 120 秒；不自动 SIGKILL。停止超时保留进程，需要检查日志，不能签为停止成功。status 展示 ActiveState/SubState/MainPID/UnitFileState；应用就绪独立核对。service logs / 统一 logs 查看本 unit 的 journal，应用文件日志仍在 home/logs/ijob.log。journal 的留存由主机 journald 配置管理。

## 升级、回退与卸载

统一 start/stop/restart/status/logs 自动识别托管实例并转交服务管理器。旧 legacy 入口遇到本数据目录的服务归属标记会拒绝争抢。不要删除标记来绕开检查；旧版本程序不具备新保护，不能并发使用。

服务登记存在时 configure Update/update 拒绝修改程序路径。按顺序停止并核对进程退出、备份数据和配置、service uninstall、受控更新 bundle、重新 service install、恢复原自启选择、启动并验收。回退同理，保留原程序包和控制配置；涉及数据库变化仍遵循[升级回退](../operations/upgrade-rollback.md)。

卸载命令：Windows `.\ijob.cmd service uninstall`，Linux `sudo ./ijob.sh service uninstall`。必须先停止；卸载保留数据、应用账号、OS 账号/ACL 授权、instance.conf 和服务登记备份。服务名冲突、配置/包装器/unit 哈希变化、实例身份不符时拒绝管理。部分安装失败也保留归属记录，先定位本轮服务状态和失败点再修复，不覆盖其他服务。

开机自启验收在隔离 VM 进行，分别检查启用和禁用、异常恢复、账号/历史保留与 A/B 实例互不影响。Docker 使用 Compose restart policy 与 Docker daemon 自启，不安装上述原生服务进行重复托管。
