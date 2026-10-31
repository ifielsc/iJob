# 按分组路由任务，并限制共享并发

[English](../../en/examples/routing-and-concurrency.md) · [手册首页](../README.md)

将任务交给匹配Worker，再让两条任务串行使用同一个并发许可。

## 适用场景与读者

适合已完成快速入门、需要将该场景用于业务流程的配置人员。

## 开始前准备

先阅读[教程公共准备](../examples.md)。本页预计时间不含外部服务安装；命令中的资源、路径和端口按自己的演练环境填写。

| 资源 | 要求 |
| --- | --- |
| 集群 | 独立CLUSTER演练环境、受支持共享平台库和两个Worker；节点身份/数据目录独立。 |
| 脚本 | 两条 Windows LOCAL / POWERSHELL 任务；每条等待 6 秒并输出 UTC 开始、结束时间与 Worker 标识。 |

## 配置一览

| 界面字段 / 配置项 | 本例填写值 |
| --- | --- |
| 节点分组 | Worker A=finance；Worker B=other |
| 任务分组 | 任务A=finance；任务B=other |
| 共享资源 | docs-shared，上限1，启用 |

## 操作步骤

### 1. 配置并检查节点

按[集群指南](../operations/standalone-cluster.md)准备共享平台库和三个独立节点：管理节点承担 API、SCHEDULER、MAINTENANCE，两个 Worker 只承担 WORKER，分组分别为 finance 和 other。所有节点使用 CLUSTER、兼容的认证与安全配置、不同 instance-name 和独立数据目录。**先启动首个节点，等待正式迁移完成并就绪，再启动其他节点**；不要让多个节点同时首次初始化空平台库。确认 A/B 就绪且没有继承无关 ID 白名单。在 Worker A 的启动环境设置 `IJOB_TUTORIAL_WORKER=finance`，B 设置为 `other`，重启后让子进程继承；这是本教程自定义观察标记，不是平台路由配置。

### 2. 发布匹配任务

新增两个 SCRIPT 任务，执行方式 LOCAL、引擎 POWERSHELL、脚本与输出编码 UTF-8、超时 60 秒、重试次数与间隔 0。A/B 分别设置 finance/other 分组，使用相同脚本并保存发布：

```powershell
$ErrorActionPreference = "Stop"
if ([string]::IsNullOrWhiteSpace($env:IJOB_TUTORIAL_WORKER)) { throw "Worker marker missing" }
Write-Output ("started=" + [DateTime]::UtcNow.ToString("o"))
Write-Output ("worker=" + $env:IJOB_TUTORIAL_WORKER)
Write-Output ("__VAR__:worker=" + $env:IJOB_TUTORIAL_WORKER)
Start-Sleep -Seconds 6
Write-Output ("finished=" + [DateTime]::UtcNow.ToString("o"))
```

分别执行，日志与输出变量 `worker` 应为 finance / other，并与实际领取节点一致。

### 3. 观察无共享限制的执行

先在并发管理确认 SCRIPT 插件容量至少为 2，A/B 的任务策略不额外串行限制，且尚未绑定共享许可。同时触发 A/B，核对两条 SUCCESS 运行的开始与结束时间：两个 6 秒执行区间应有重叠。若未重叠，先检查触发时间差、Worker 就绪和容量，再比较共享限制；触发受理本身不证明并行。

### 4. 绑定容量1的共享资源

待前两条执行结束，在并发管理创建docs-shared、上限1并启用，绑定到A/B两条任务。并发策略保存不发布任务定义版本。

### 5. 再次同时触发并检查互斥

同时触发A/B，查看一条执行、另一条等待许可；比较实际执行区间，结束时间应不与下一条开始时间重叠。不同节点也共享同一限制。

### 6. 理解不匹配与白名单

另用没有匹配Worker的专属分组任务观察排队，结束后为其提供匹配节点或按可用控制取消，不能留下无限等待。ID白名单限制根触发目标：仅任务列表非空时不接作业根，仅作业列表非空时不接独立任务根。

## 预期结果

| 检查项 | 预期值 |
| --- | --- |
| 路由 | finance→A，other→B |
| 未绑定共享许可 | 两条运行均 SUCCESS，执行区间重叠 |
| 共享容量 1 | 两条运行均 SUCCESS，执行区间不重叠；保持 finance / other 路由 |

## 常见失败

| 现象 | 处理方法 |
| --- | --- |
| 一直排队 | 依次查角色、分组、根目标白名单和各层并发许可；空节点分组不是匹配所有分组。 |

## 安全与清理

排空或取消练习触发，确认无活动工作再解除共享绑定、移除资源并恢复专属节点设置。

## 适用版本与限制

适用 iJob 1.0.0。示例覆盖独立 CLUSTER 的一个管理节点、两个 Windows Worker、按 finance / other 分组领取，以及共享容量 1 在两个 Worker 间串行执行；中英文配置得到相同结果。同宿主多进程不代表物理高可用；Linux 脚本、跨宿主故障接管与白名单组合不列为本页已确认的完整覆盖。

## 相关页面

- [节点角色、分组与允许范围](../configuration/routing.md)

- [业务并发控制](../user-guide/concurrency.md)

- [单机与集群：前提和职责](../operations/standalone-cluster.md)
