# 运行变量与表达式参考

[English](../../en/reference/variables.md) · [手册首页](../README.md)

本文适用于 1.0.0。配置步骤见[参数模板、输入和变量](../user-guide/parameters.md)。平台变量、脚本自身环境变量和 SQL 绑定参数是不同机制。

## 系统变量

`ijob.` 为保留前缀，自定义输入和输出不能使用。下列值由平台按执行上下文提供；没有相应上下文时不要假设变量存在。

| 变量 | 含义 | 可用条件 / 格式 |
| --- | --- | --- |
| `ijob.instance.id` | 当前实例 ID | 当前实例已有 ID；数字 |
| `ijob.instance.rootId` | 根实例 ID | 已记录根实例；数字 |
| `ijob.trigger.id` | 触发 ID | 有有效触发记录；数字 |
| `ijob.trigger.type` | 触发类型 | 有有效触发记录；枚举名称 |
| `ijob.trigger.time` | UTC 触发时间 | ISO 本地日期时间字符串，本身不携带偏移；按 UTC 理解 |
| `ijob.trigger.date` | UTC 触发日期 | ISO 日期字符串 |
| `ijob.business.date` | 业务时区的日期 | 已保存完整业务时间上下文 |
| `ijob.business.time` | 业务时间 | 已保存完整业务时间上下文；ISO 日期时间字符串，包含时区偏移 |
| `ijob.business.zoneId` | 业务时区 | 已保存完整业务时间上下文；与业务时间配套使用 |
| `ijob.schedule.id` | 调度 ID | 触发关联调度；手动执行不保证存在 |

执行上下文另行注入系统变量，不把这些系统项计作用户输入项。

旧触发缺少业务时间时不会在重试时猜测补齐。显示时区、机器时间和业务时区分别理解，参阅[调度](../user-guide/schedules.md)。

## 支持变量解析的字段

| 写法 | 行为 |
| --- | --- |
| `${limit}` | 替换存在且非 null 的变量；缺失时保留原占位符 |
| `${limit!}` | 要求变量存在且非 null，否则报错 |
| `${region:default}` | 缺失或 null 时使用文本默认值 default |
| `$${HOME}` | 仅在脚本文本转义处理中保留脚本自身的 `${HOME}` |

这些语法仅适用于插件确实调用变量解析的字段。网关条件、JSONPath、Cron、文件匹配规则各有自己的语法，不能混用。默认值属于文本，不会自动转换为整数。

在支持对象解析的字段中，整个字符串是单个占位符时可保留变量 JSON 类型；嵌入其他字符后会得到文本。例如 `${limit}` 可保留整数，`limit=${limit}` 是字符串。

## SCRIPT、SQL 与 ETL 的区别

| 位置 | 示例 | 规则 |
| --- | --- | --- |
| SCRIPT 脚本文本 | `echo "limit=${limit}"` | 写入脚本前做文本替换；limit 在本例必须为整数 |
| SHELL 自身变量 | `echo "$${HOME}"` | 平台移除额外的 $，交给 shell 解析 `${HOME}` |
| SCRIPT 输出 | `echo __VAR__:rows=3` | 通过输出协议写入字符串 `"3"`，不自动解析为 JSON 数字 |
| SQL 插件 | `SELECT id FROM orders WHERE id <= ${limit};` | 文本替换，不使用预编译参数绑定；只对受约束整数使用本例 |
| ETL 数据库读取 | `SELECT id FROM orders WHERE id <= :limit` | 在查询参数中设置 limit，并由驱动绑定值；不要在 SQL 中加引号包住 :limit |

ETL 查询参数可将 limit 映射到 `${limit}`。日期参数按数据库类型显式转换，例如 PostgreSQL `CAST(:batchDate AS date)`。占位符不能用来绑定表名、列名等 SQL 标识符。

SCRIPT 的工作目录和环境变量配置为静态值，不执行平台 `${...}` 插值。

任意字符串直接拼入脚本或 SQL 可能改变命令含义。选择受约束类型，按实际解释器和数据库处理引号；秘密保存在资源配置，不放日志或普通变量。

## 输入快照与输出合并

- 本次执行值只属于本次请求，不会自动更新参数模板或已保存的调度。
- 已保存调度不会自动跟随模板更新；“重新载入”会替换当前编辑草稿。
- 上游输出可以覆盖同名基础输入；多个上游同时产生同名输出会报歧义冲突，避免依赖完成先后顺序。
- 上游输出为 null 时会移除该键。下游需要的值使用明确且唯一的名称。

## 限制

| 对象 | 限制 |
| --- | --- |
| 用户输入 / 输出快照 | 最多 100 项，名称最多 128 字符 |
| 输入与变量快照 | UTF-8 JSON 最多 65,536 字节 |
| 参数类型 | STRING、INTEGER、DECIMAL、BOOLEAN、DATE、DATETIME、ENUM、OBJECT、ARRAY |
| 敏感参数 | 不允许保存默认值；不要将变量当作秘密保险库 |
| SCRIPT 输出变量 | 最多 100 个，单值 16 KiB；其他输出/日志限制见脚本页 |

## 相关页面

[脚本](../user-guide/plugins/script.md) · [SQL](../user-guide/plugins/sql.md) · [ETL](../user-guide/plugins/etl.md) · [接口与参数教程](../examples/api-and-parameters.md)
