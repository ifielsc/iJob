# 可信 Local JAR 插件

[English](../../../en/user-guide/plugins/local-jar.md) · [手册首页](../../README.md)

## 入口与前提

任务 → 新增/编辑 → 插件 `LOCAL_JAR`。先准备字段表要求的资源、权限及执行节点依赖。任务公共超时、重试和发布规则见[任务](../tasks.md)。

## 字段与作用

| 字段/动作 | 用途与填写规则 |
| --- | --- |
| `formatVersion / spiVersion` | 新命名空间配置格式/协议使用 1；SPI Maven 版本 1.0.0。 |
| `jarFile / jarSize / sha256` | 上传/选择经审核制品，平台核对文件名、大小与 SHA-256，不手工伪造。 |
| `implementationVersion / entryClass` | 记录插件实现版本和入口类；入口须实现本版本 SPI。 |
| `userConfigJson` | 传给插件的业务 JSON；由插件定义结构和校验，平台不替代插件业务说明。 |

## 最小样例与核对

```text
Maven SPI: com.ifielsc:ijob-plugin-spi:1.0.0
Manifest: IJob-Plugin-Api-Version: 1
```

先按配置手册启用并准备 Worker 目录。插件使用宿主 SPI，不打包平台类；禁止 com.ifielsc.ijob、com.ifelio.ijob、com.ifeel.ijob 平台包混入制品。旧包名插件需改 import、重编译、重新登记入口和哈希。先运行无外部副作用插件，核对输出、停止和异常处理。Local JAR 不是不可信代码沙箱。

样例代码是配置片段，资源 ID/路径需替换；实际外部协议验收状态见[案例索引](../../examples.md)。
