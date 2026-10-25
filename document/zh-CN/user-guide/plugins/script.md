# 脚本

[English](../../../en/user-guide/plugins/script.md) · [手册首页](../../README.md)

## 入口与前提

任务 → 新增/编辑 → 插件 `SCRIPT`。先准备字段表要求的资源、权限及执行节点依赖。任务公共超时、重试和发布规则见[任务](../tasks.md)。

## 字段与作用

| 页面字段/动作 | 用途与填写规则 |
| --- | --- |
| 执行方式、SSH 资源 | LOCAL 在 Worker 本机/容器执行；SSH 使用独立 SSH 资源，在远端执行。 |
| 脚本引擎、Python 解释器 | 选择与执行系统匹配的引擎；非内置解释器填写节点上可执行的路径。Windows 入门用 BAT，Linux 用 SHELL /bin/sh。 |
| 脚本内容、工作目录、环境变量 | 脚本文本、工作目录和环境变量。路径属于执行机器，不是浏览器机器；秘密使用资源配置。 |
| 脚本编码、输出编码 | 脚本文件编码与输出解码分别配置；UTF-8 优先，旧 charset 仅兼容。 |

## 最小样例与核对

先准备上表的独立测试资源，按页面逐项填写；保存后手动执行，再按下文核对实例、输出与目标系统。保存本身不会执行。

| 配置项 | 填写内容 |
| --- | --- |
| 执行方式 | 本地 Worker（LOCAL） |
| 脚本引擎 | Windows 选 BAT；Linux 选 SHELL |
| 工作目录 | 首次演练留空 |

```bat
echo hello-ijob
echo __VAR__:rows=3
exit 0
```

最小样例选择 LOCAL/BAT；Linux 将引擎改为 SHELL，解释器 /bin/sh，脚本同样使用 echo 和 exit。退出码非零为失败。stdout/stderr 会进入实例日志，分别标记 STDOUT/STDERR；并非“不记录”。输出受限：行 16 KiB，捕获/日志预算各 256 KiB，捕获最多 1000 行；变量值 16 KiB、最多 100 个。`__VAR__:rows=3` 写入输出变量，日志会隐藏变量值。超限日志可能截断，不用于保存大文件。停止会请求终止进程，但不能撤销已完成写入。

示例中的资源、路径及对象名称需替换为本环境实际值；实际外部协议验收状态见[案例索引](../../examples.md)。

SSH 模式关闭连接不保证远端进程退出。本轮超时测试中，平台已为 TIMED_OUT，远端进程仍完成后续写入；需由远端任务实现期限、幂等和取消。

## 真实页面示例

Windows LOCAL/BAT 样例。

![Windows LOCAL/BAT 样例。](../../images/manual/script-editor.png)

## 技术字段对照（按需查阅）

以下是保存配置中的键名，供开发或排障定位；不是需要粘贴的任务 JSON。自动核验的元数据不作为页面手工输入。

| 页面字段/分组 | 技术键名 |
| --- | --- |
| 执行方式、SSH 资源 | `executionMode / sshResourceId` |
| 脚本引擎、Python 解释器 | `engine / interpreter` |
| 脚本内容、工作目录、环境变量 | `script / workingDir / env` |
| 脚本编码、输出编码 | `scriptCharset / outputCharset` |
