# 构建、测试与运行验收分别证明什么

[English](../../en/development/quality-gates.md) · [手册首页](../README.md)

安装者拿到完整包无需执行本章。开发者/发布维护者用这些检查判断候选是否可交付：编译成功只证明能构建；自动化测试证明相应覆盖；运行验收证明指定环境与场景。跳过和未执行不能记为通过。

源码构建按安装说明使用 Python 3.11+、JDK 21、Maven 3.9.11、Node 24.14.1、pnpm 11.19.0。开发从根 AGENTS.md 和 document/standards/README.md 开始；本文不是替代当前工程规范的新规则集。

## 检查入口

前端目录 `develop/web/ijob`：

```sh
pnpm install --frozen-lockfile
pnpm check
pnpm exec playwright install chromium
pnpm exec playwright test --config=playwright.config.ts
pnpm exec playwright test --config=playwright.production.config.ts
```

本地使用新的外部报告目录设置 PLAYWRIGHT_JUNIT_OUTPUT_FILE 和 --output，不复用已有端口。Linux 首次需要安装 Playwright 浏览器系统库。pnpm check 包括运行时、类型、lint、模块边界、格式、单元、生产构建和 bundle 预算；浏览器测试另行执行。

后端和 Agent 完整质量入口从仓库根执行，需要 PowerShell、JDK/Maven、Python 及 tools/ci/requirements.txt：

```powershell
./develop/api/ijob/tools/verify-backend.ps1 -RunId candidate-backend-001 -ReportRoot /absolute/new/reports -TemporaryRoot /absolute/new/temp
```

该完整入口需要包含固定历史提交的 Git clone，以恢复并核验历史 SQL 测试资源。源码 ZIP 可构建新安装交付包，但不能声称已经运行依赖 Git 历史的迁移门禁。不要把历史 SQL 复制回当前应用源码。只有 RealMessageSourcesTest 的两项真实消息源检查可在基础环境中预期跳过，报告须单独列出。

本地 HTTP/SQLite 专项、七套真实后端浏览器 Full、五数据库及容器运行验收见当前 document/ci/basic-ci.md 和 isolated-integration.md。使用新环境、新账号、新目录；绝不依赖维护者的演示或旧 QA 服务。

## 文档和交付检查

```sh
python tools/distribution/docs.py --check
python -m unittest discover -s tools/distribution -p "test_*.py"
python tools/repository/check-license.py
```

配置表来自 setup 工具的 user-settings.tsv；调整配置时同步实现、校验、双语说明和测试，然后运行 `python tools/distribution/docs.py --write` 更新文档表。--check 校验双语页面配对、表格同步和本地链接。命令、配置键和版本号不翻译，术语表位于手册首页。

新增文件按 .repository/allowlist.json 精确登记并生成 .gitignore，扫描不得靠强制添加绕过。打包脚本只复制这份清单，拒绝已有输出目录；构建快照不是第二份维护源码。默认没有公开发布、上传镜像或修改仓库可见性的动作。

## 发布前验收

必须绑定同一源码与最终制品：CI、全新安装、配置变更、启停、样例运行、备份恢复和升级回退。保留失败和重试证据，区分通过、跳过和未执行；构建成功不代表验收成功。

当前公开再分发仍需解决已有第三方许可与漏洞处置待办，包括 vdirs 许可全文溯源、MySQL/Oracle 驱动组合分发条件、镜像与工具链发现的复核，以及最终 SBOM/NOTICE。仓库和候选保持私有；这些事项未签收前不创建公开 Release。
