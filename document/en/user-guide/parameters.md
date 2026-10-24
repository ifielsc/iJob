# Parameter templates, inputs and variables

[简体中文](../../zh-CN/user-guide/parameters.md) · [Manual home](../README.md)

## Entry and workflow

Parameter templates; task/workflow run dialog. Actions depend on account permissions; identify the object and environment first.

## Configuration and actions

| Field/action | Purpose and rules |
| --- | --- |
| `参数名 / 类型` | Up to 100 fields: STRING/INTEGER/DECIMAL/BOOLEAN/DATE/DATETIME/ENUM/OBJECT/ARRAY. Preserve JSON types. |
| `必填 / 默认值 / 敏感` | Required and default-present are independent. Sensitive fields cannot store defaults. |
| `选项 / 最小最大 / 长度` | Set type-specific options, numeric and length bounds; distinguish empty, absent, 0 and false. |
| `传入 / 本次值` | Include/value in a run dialog affect only that execution, not templates/schedules. |
| `默认绑定 / 重新载入` | Bindings guide later selection; saved schedules do not follow template edits. Reload replaces the current draft. |

## Use and verify

Example: batchDate (DATE, required) and limit (INTEGER, default 3). Fill the date and inspect the run input snapshot. Variables are not a secret vault; store credentials/tokens in resources. Scripts can output `__VAR__:rows=3`. Follow each plugin/condition field for references rather than assuming one interpolation syntax everywhere.

## Real page examples

Real two-field template: required date and integer default 3.

![Real two-field template: required date and integer default 3.](../images/manual/parameter-editor.png)
