# 数据校验

[English](../../../en/user-guide/plugins/data-check.md) · [手册首页](../../README.md)

## 入口与前提

任务 → 新增/编辑 → 插件 `DATA_CHECK`。先准备字段表要求的资源、权限及执行节点依赖。任务公共超时、重试和发布规则见[任务](../tasks.md)。

## 字段与作用

| 字段/动作 | 用途与填写规则 |
| --- | --- |
| `resourceId / queryTimeoutSeconds` | 业务数据库资源；每条查询超时默认 30 秒。 |
| `checks[].name / sql / type` | 每条检查有可辨名称和查询；type 为 NORMAL/COUNT/EXISTS/EMPTY，按结果含义选择。 |
| `compareType / expected / minValue / maxValue` | EQ/NE/GT/GE/LT/LE/BETWEEN；普通比较填 expected，范围比较填边界，保持数值类型。 |
| `variable / level / handlers` | 输出变量、检查级别（默认 ERROR）及后续处理；不要将警告当作数据已修复。 |

## 最小样例与核对

```json
{"checks":[{"name":"three-orders","sql":"SELECT COUNT(*) FROM orders","type":"NORMAL","compareType":"EQ","expected":3,"level":"ERROR"}]}
```

补业务数据库资源后运行：三行时通过，再用独立副本改为两行核对失败。查看每项检查和总实例状态；COUNT 与 SQL COUNT(*) 的语义不要混淆，标量比较样例用 NORMAL。校验仅发现问题，除非另配处理器，不会修复数据。

样例代码是配置片段，资源 ID/路径需替换；实际外部协议验收状态见[案例索引](../../examples.md)。

## 真实页面示例

NORMAL 值比较与 ERROR 级别；比较值在下方表单填写。

![NORMAL 值比较与 ERROR 级别；比较值在下方表单填写。](../../images/manual/check-editor.png)
