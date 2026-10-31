# 生产入口与服务托管

[English](../../en/operations/production.md) · [手册首页](../README.md)

完整包提供基本运行能力；生产部署还要在目标环境落实 TLS、入口访问控制、受限运行账号、备份恢复、监控和容量。反向代理需支持上传大小、SSE/长连接超时和 readiness 摘流；不把管理健康详情无条件暴露到公网。

普通 `configure` / `start` 不会安装操作系统服务。需要 Windows 服务或 systemd 托管时，先完成首次初始化并停止实例，再按[服务托管](../installation/services.md)显式执行 `service install`；需要开机自启时再执行 `service enable`。服务使用登记的 Java、程序目录、IJOB_HOME 和运行账号。检查正常停止、异常恢复及启动后就绪，避免另一个进程管理器重复启动同一实例。

Docker 限制内存/CPU/日志、使用非 root 运行身份和明确挂载。默认镜像不包含所有业务解释器/依赖；按任务需要提供可信工具。不要以 privileged 或 docker.sock 挂载补齐执行能力。Worker 本地文件和 Local JAR 必须随节点规划。

按代表性任务负载评估 CPU、内存、数据库连接与日志增长，再确定运行限额和保留周期。部署前核对程序包清单、第三方许可说明及适用的安全公告；升级或变更依赖后重新检查这些条件。