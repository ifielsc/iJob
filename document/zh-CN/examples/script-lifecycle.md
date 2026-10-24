# 脚本成功、失败、停止与重试

[English](../../en/examples/script-lifecycle.md) · [手册首页](../README.md)

**验证状态：基础路径已在独立环境实测；下述扩展不自动继承通过结论。**

相关字段：[脚本](../user-guide/plugins/script.md)。

## 准备、配置与执行

准备：独立 Worker，Windows BAT 或 Linux SHELL。先保存 docs-hello，重试 0、超时 60 秒：

```sh
echo hello-ijob
exit 0
```

手动执行后检查 SUCCESS、执行节点和 STDOUT。复制为 docs-failure，把 exit 0 改为 exit 7，预期 FAILED；不要以 HTTP 触发成功当作脚本成功。再建长任务：Windows `ping -n 31 127.0.0.1 > nul`，Linux `sleep 30`，运行中请求停止，等待 STOPPED。

扩展：在独立副本把超时设为 2 秒观察超时路径；设置重试 1 并记录尝试次数。这两种扩展需在目标节点单独验证，不由成功/停止截图代替。结束后停用练习调度，保留失败实例供排障。

## 结束检查

保留输入、定义版本、触发/实例 ID、脱敏日志和目标核对结果。只停止本案例拥有的进程或调度；其他环境和历史证据不作为清理对象。

## 真实页面示例

成功路径的真实输出过滤视图；失败和停止有各自实例记录。

![成功路径的真实输出过滤视图；失败和停止有各自实例记录。](../images/manual/script-run.png)
