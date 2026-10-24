# 通用作业包工具（格式 1）

此工具使用 `/api/job-packages` 管理 API，源端和目标端各运行同版本 iJob 1.0.0。
仅依赖 Python 3 标准库。适用于独立环境之间的配置迁移，不复制平台数据库。

## 内容与边界

- 可一次选择多个任务或作业；API 的 `roots` 也允许混合类型，最多 100 个根对象。
- 收集嵌套作业、共享任务、已绑定参数模板及显式插件资源依赖；共享对象只导出一份。
- `--resources` 附带资源的非敏感配置，否则仅导出引用，必须映射目标资源。
- `--schedules` 包含关联调度、日历及调度参数表单；`--notifications` 包含通知绑定和通知渠道引用。通知渠道须在目标环境准备好。
- 新建或更新的任务、作业和新建调度默认停用。复用已有对象不会修改其启停状态。
- 包含任务并发上限及关联的并发资源，多个任务共享的并发资源去重。附带资源配置时可新建并发资源，引用模式须显式映射到目标已有且启用的并发资源；可核对调整容量。复用任务时保留其原并发策略，更新任务时随任务在同一事务内应用策略；旧包没有策略字段时保留目标策略。全局插件并发策略、当前许可占用和运行状态不打包、不覆盖。
- 不包含密码、密钥、Token、请求头值、敏感参数值、加密凭据、运行实例、日志、业务数据、Local JAR 文件或外部数据库/文件目录。Local JAR 须先在目标环境安装相同制品。
- 已发布作业引用旧版本任务/子作业时，拒绝用当前版本静默替换。请先确认依赖版本并重新发布作业后导出。
- 自由文本中的任意自定义秘密不能仅靠字段名完整识别。已识别的凭据字段及常见内嵌凭据会移除；导出前仍应检查脚本与自定义配置，导入前必须人工检查环境参数。

包内仅有 `manifest.json`、`objects.json`；包含格式版本、SHA-256、稳定逻辑 UUID、依赖引用及待补充输入路径。不会解压到文件系统。压缩与解压内容均限制 16 MiB，最多 500 个对象，拒绝未知路径、重复文件、未知版本及循环依赖。

## 使用顺序

在各环境的 `secrets/` 下准备受限 JSON 连接文件，字段为 `url`、`username`、`password`。不要把凭据放在命令行或仓库。远程连接必须使用 HTTPS，本机回环地址可使用 HTTP。API 重定向会被拒绝。

以下命令从仓库根目录执行；所有输入、输出路径都应替换为仓库外对应环境的路径。输出文件须为全新路径，不覆盖已有文件。

```sh
python tools/job-package/ijob_package.py --connection SOURCE_CONNECTION export --kind JOB --ids 101 102 --resources --schedules --notifications --out PACKAGE_FILE
python tools/job-package/ijob_package.py --connection TARGET_CONNECTION inspect --bundle PACKAGE_FILE --out INSPECTION_REPORT
python tools/job-package/ijob_package.py --connection TARGET_CONNECTION plan --bundle PACKAGE_FILE --choices PRIVATE_CHOICES --reviewed-environment --out PLAN_REPORT
python tools/job-package/ijob_package.py --connection TARGET_CONNECTION apply --plan PLAN_UUID --out APPLY_REPORT
python tools/job-package/ijob_package.py --connection TARGET_CONNECTION verify --plan PLAN_UUID --out VERIFY_REPORT
python tools/job-package/ijob_package.py --connection TARGET_CONNECTION records --out HISTORY_REPORT
```

`PRIVATE_CHOICES` 是以对象逻辑 UUID 为键的 JSON 对象。每项可指定：

| 字段 | 含义 |
| --- | --- |
| `action` | `CREATE` 新建、`REUSE` 复用、`UPDATE` 更新 |
| `targetId` | 复用或更新时填写目标对象 ID（字符串） |
| `body` | 可选的完整目标配置；依赖引用路径仍保持 null，由导入器绑定 |
| `secrets` | 以 `requiredSecrets` 中的 JSON Pointer 为键，填写目标环境输入值 |

省略选择时，首次导入默认新建；同一逻辑对象已导入且未变化时默认复用。引用型资源及通知渠道必须明确映射；同名对象不会自动覆盖。更新仅支持已停用且无启用调度的任务和作业，其他配置使用新建或复用。修改过的目标对象必须重新预览并明确选择。

`plan` 不创建业务对象，只保存导入计划与脱敏报告。待应用配置使用目标环境 AES 密钥加密保存，有效期 24 小时；导入结果和逻辑映射持久保存，服务重启后仍可识别重复导入。源端与目标端不需要共享 AES 密钥。

每个对象的发布、映射和回执在同一事务内提交。中途失败返回 `PARTIAL`，已完成对象不会因重试而重复创建；修复临时条件后可用同一计划继续。目标配置或版本已变化时必须重新生成计划，不能借重试覆盖并发改动。网络错误不会自动重试；先查询结果再决定下一步。

`verify` 会重新读取目标对象，与该计划执行时的指纹比较：`VERIFIED` 表示一致，`DRIFTED` 表示之后发生改动，`INCOMPLETE` 表示导入尚未完成。它不执行任务，也不测试外部资源连通性。

退出码：成功 `0`，输入/连接/API 错误 `2`，计划受阻、部分完成或验证不一致 `3`。报告不包含所补充凭据。计划与报告只能由创建计划的账号访问；所有包 API 均要求配置管理权限。

## 界面操作

在“任务管理”或“作业编排”列表勾选一项或多项，点击“导出所选”。清单默认折叠；展开后可搜索、移除对象，列表内部滚动。资源可选择附带配置或仅导出引用，调度和通知绑定可分别选择；依赖统计按去重后的对象计算。

任务管理中点击“导入任务包”，作业编排中点击“导入作业包”，依次上传校验、映射依赖、检查差异、确认导入。新建对象可调整名称及环境配置；引用型资源和通知渠道须选择目标环境已有对象。密码等输入在独立凭据区域补充。检查目标环境配置后才可生成可执行计划，检查不通过时不会创建业务对象。

导入结果按新增、更新、复用显示已完成数量和对象明细；默认下载可用浏览器阅读、打印为 PDF 的 HTML 报告，同时保留 JSON 数据下载。HTML 仅含结果元信息，不包含配置正文或凭据。部分完成可继续同一计划。网络中断时先刷新结果，避免误判。已提交计划可从“导入记录”查看或继续，未执行配置保留 24 小时。记录表显示操作账号、记录时间、首次实际导入时间与完成时间，查看详情后可返回记录列表；时间遵循界面显示时区。旧记录缺失的实际导入/完成时间标为“未记录”，不会用计划创建时间代替。导入完成后，请分别核对资源连通性、参数、日历和调度，再手动启用任务与作业。列表换页沿用现有选择规则，导出前请核对弹窗中的所选数量。

## 验证

```sh
python -m unittest discover -s tools/job-package -v
```

后端 `PackageArchiveTest` 验证包边界与脱敏，`JobPackageHttpIT` 使用两个独立 SQLite 数据库及独立 AES 密钥验证真实 HTTP 导出/导入、依赖重绑定、重启幂等、并发改动保护和权限。该集成测试不连接外部业务数据库，也不启动调度或执行任务。
