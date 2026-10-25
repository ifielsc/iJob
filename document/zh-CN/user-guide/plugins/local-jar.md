# 可信 Local JAR 插件

[English](../../../en/user-guide/plugins/local-jar.md) · [手册首页](../../README.md)

## 入口与前提

任务 → 新增/编辑 → 插件 `LOCAL_JAR`。先准备字段表要求的资源、权限及执行节点依赖。任务公共超时、重试和发布规则见[任务](../tasks.md)。

## 字段与作用

| 页面字段/动作 | 用途与填写规则 |
| --- | --- |
| 配置格式、SPI 协议（自动核验） | 新命名空间配置格式/协议使用 1；SPI Maven 版本 1.0.0。 |
| JAR 文件名、文件大小、SHA-256 校验值 | 先将经审核制品部署到 API 和 Worker 的制品目录，再填写文件名与实际 SHA-256；页面不支持上传或填写路径。文件大小由服务器核验。 |
| 制品版本、入口类 | 记录插件实现版本和入口类；入口须实现本版本 SPI。 |
| 业务参数 | 在业务参数编辑器填写名称、类型和值，对象和数组可逐层编辑；结构与校验由插件定义。技术配置以 JSON 保存，不要求用户粘贴完整任务 JSON。 |

## 最小样例与核对

先准备上表的独立测试资源，按页面逐项填写；保存后手动执行，再按下文核对实例、输出与目标系统。保存本身不会执行。

| 配置项 | 填写内容 |
| --- | --- |
| JAR 文件名 | `hello-job-1.0.0.jar`（先部署到 API 与 Worker 制品目录） |
| SHA-256 校验值 | 填写该文件实际的 64 位十六进制校验值 |
| 入口类 | `com.example.jobs.HelloTask`（须与实际制品一致） |
| 业务参数 | 按此插件约定逐项填写名称、类型和值 |

先按配置手册启用并准备 Worker 目录。插件使用宿主 SPI，不打包平台类；禁止 com.ifielsc.ijob、com.ifelio.ijob、com.ifeel.ijob 平台包混入制品。旧包名插件需改 import、重编译、重新登记入口和哈希。先运行无外部副作用插件，核对输出、停止和异常处理。Local JAR 不是不可信代码沙箱。

示例中的资源、路径及对象名称需替换为本环境实际值；实际外部协议验收状态见[案例索引](../../examples.md)。

## 技术字段对照（按需查阅）

以下是保存配置中的键名，供开发或排障定位；不是需要粘贴的任务 JSON。自动核验的元数据不作为页面手工输入。

| 页面字段/分组 | 技术键名 |
| --- | --- |
| 配置格式、SPI 协议（自动核验） | `formatVersion / spiVersion` |
| JAR 文件名、文件大小、SHA-256 校验值 | `jarFile / jarSize / sha256` |
| 制品版本、入口类 | `implementationVersion / entryClass` |
| 业务参数 | `userConfigJson` |

SPI: `com.ifielsc:ijob-plugin-spi:1.0.0`; Manifest: `IJob-Plugin-Api-Version: 1`.
