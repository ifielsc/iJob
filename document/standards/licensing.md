# 版权、许可证与素材来源

项目自有代码采用根目录 LICENSE 中的 MIT License，版权声明为 `Copyright (c) 2026 ifielsc`。当前由 ifielsc 个人独立开发。许可证选择与仓库可见性、发布授权分别处理。

自有 Java、TypeScript、JavaScript、Vue、CSS、Python、PowerShell、Shell 及 Dockerfile 源码使用适合该格式的注释，包含版权行和 `SPDX-License-Identifier: MIT`。Shebang 保持第一行，Vue 使用 HTML 注释，CSS 使用块注释。现有第三方声明不得覆盖；第三方补丁和 Maven wrapper 不重新归属。冻结的 SQL、历史兼容文件不为添加注释改写字节，生成输出、二进制、JSON 等由目录许可与来源记录覆盖。

各 Maven 模块及前端静态输出携带自有 LICENSE 副本，构建镜像保留对应告知文件。源码中的副本必须与根 LICENSE 一致，检查入口为 `tools/repository/check-license.py`。普通新增自有源码应按相同约定添加文件头。

第三方依赖不因项目采用 MIT 而改变许可。分发时保留实际组件的版权、LICENSE/NOTICE，并按最终 JAR、前端 bundle 和镜像生成清单；不能用声明 BOM 代替实际嵌套库或操作系统层的核对。MySQL Connector/J 的 GPL/FOSS Exception、Oracle JDBC/NLS 的实际随包条款（POM 的 FUTC 名称可能与内置 FDHUTC 全文不同）及多许可组件应记录具体适用条件。未完成项必须如实标注，不写成全量许可或漏洞审计通过。

自有品牌素材位于 `develop/web/ijob/src/assets/brand/`，包括 `ijob-helix.png`、`ifielsc-icon.png`、`login-light.webp`、`login-dark.webp`。来源署名统一为“ifielsc 个人创建”（维护者于 2026-11-01 确认）。该署名仅覆盖上述自有品牌素材，不覆盖依赖包中的图标或字体。

创作过程记录：维护者于 2026-10-19 声明上述素材使用 ChatGPT 生成，无外部素材引用；具体生成模型、日期和会话未记录。个人创作者署名与辅助生成工具分别记录，署名不表示素材未经 AI 辅助。P3 证据登记文件哈希、来源声明与适用平台条款，不凭 AI 生成推断排他性或第三方权利核验已完成。

维护者于 2026-10-31 再次确认上述素材由 ChatGPT 生成，原始生成记录已无法找到。按现有来源声明保留证据缺口，不将缺失会话记录本身等同于已知侵权，也不宣称完成独占权或第三方权利核验。

前端构建生成 `THIRD_PARTY_NOTICES.txt` 和 `.vite/third-party-components.json`，按实际渲染的依赖模块和保守包含的样式输入清点。上游包遗漏全文时，只有能追溯到对应版本的许可文本才能补入 `develop/web/ijob/tools/third-party/`，并锁定来源与 SHA-256；不能用本项目版权替换第三方版权。尚未取得的全文明确记为 `missing-upstream-text`，构建可用于私有测试，但不能据此宣布再分发核验通过。

标准 MIT 文本：[SPDX MIT](https://spdx.org/licenses/MIT.html)。每次候选发布，把许可清单、素材哈希、漏洞处置及制品哈希绑定同一提交；新原始证据保存在工作区 `runtime/<env-id>/runs/<run-id>/reports`，资源与秘密也遵循工作区环境规范。历史 `maintenance/release-preparation/` 证据保留原位，不迁移或覆盖。

2026-10-31 用户决定：正式成品不再嵌入 MySQL Connector/J、Oracle JDBC 或 Oracle NLS。保留测试作用域依赖和数据库适配，运行时由用户在实例外部驱动目录提供合法取得的 JAR。PostgreSQL JDBC 与 Microsoft SQL Server JDBC 继续随包提供，保留相应全文及版权告知。该调整不免除其他第三方组件及镜像系统层的许可义务。

正式二进制下载须同时提供 `ijob-1.0.0-third-party-sources.zip` 及校验值，作为与实际组件版本对应的独立交付件。此包包含适用组件的原始源码、发行版补丁、构建资料和许可说明；项目自己的源码 ZIP 不能替代它。成品 README 和第三方清单提供该交付件名称。构建工具不会因写入名称就断言材料已齐备，公开前仍须核对实际 JAR、JRE 与系统包版本，并保证收件人能从同一发行下载位置取得材料；仅保存本机缓存不算交付。
