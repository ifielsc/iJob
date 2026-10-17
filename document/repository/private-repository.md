# 私有仓库维护与同步

2026-10-18 已创建 [ifelio/iJob](https://github.com/ifelio/iJob)，状态为 Private，默认分支 main，只有所有者 ifelio，无其他协作者或待接受邀请。首个提交为 `92eed059e605833fcd6f98a1a1c512297c7973c9`，首次导入 2,190 个文件；远程路径、模式及 blob 哈希与审核后的本地提交一致。

本机使用 HTTPS + GCM 浏览器授权，提交身份为 ifelio 与 GitHub 隐私邮箱，仅在当前仓库设置。认证秘密不写入仓库。GitHub 仓库是源码库，完整交付包和历史验收资料继续保存在维护工作区。

## 在哪里继续开发

在已连接 `https://github.com/ifelio/iJob.git` 的 Git 根目录下继续维护 `develop/api/ijob`、`develop/api/ijob-monitor-agent`、`develop/web/ijob` 和文档。维护电脑已将整个仓库（含 .git 和未提交文档）迁移到独立的正式源码目录，不再使用 test 下的候选目录；具体本机路径见外层工作区入口。此目录不能按临时测试产物清理或重新导出覆盖。

用 `git rev-parse --show-toplevel`、`git remote -v` 确认位置和远程。旧外层 develop 树是导入前保留副本，不能与本仓库同时作为编辑入口，也不能整目录复制覆盖本仓库。历史报告和机器专用路径留在外层工作区索引。

当前冻结交付名为 `release-1.0.0-20261017-setup-guide`，位于维护工作区 outputs。源码仓库不含 outputs、用户数据或发布二进制，也没有创建 GitHub Release。提交/推送不会更新已交付 JAR 或部署应用。

## 日常同步 GitHub

开始修改前确认工作区干净；有未提交改动时先审阅并保存，不执行清理或强制重置。然后在本仓库执行：

```sh
git status --short
git switch main
git pull --ff-only origin main
```

修改、执行与改动相关的检查后，运行敏感信息扫描。将 RUN_ID 替换为每次唯一的证据目录名：

```sh
python tools/repository/prepare.py scan --report test/reports/RUN_ID/scan.json
```

新增文件须先加入逐文件白名单并重新生成忽略规则，见下文。扫描通过后，仅暂存本次要交付的文件；下面以文档修改为例，其他修改请替换路径和提交说明：

```sh
git add -- README.md document/repository/private-repository.md develop/deployment/first-run/README.md
git diff --cached --stat
git diff --cached
git commit -m "docs: update repository and configuration guidance"
git push origin main
git fetch origin
git rev-parse HEAD
git rev-parse origin/main
git status --short
```

只有完成检查且希望同步时才执行 commit / push。最后两个提交号应相同，工作区应符合预期。若远程已有新提交导致拒推，先获取并审阅差异、解决冲突和重新验证；不使用 force push 覆盖远程。GCM 如要求重新登录，在浏览器完成授权，不把 token 拼入 origin URL。

## 白名单

机器可读清单为 [allowlist.json](../../.repository/allowlist.json)，每个文件明确列出。包含主后端所有模块源码/测试/迁移 SQL、Agent、前端源码/测试/资源/锁文件/补丁、构建描述、受维护工具和脱敏部署模板。保留 Java 源码中名为 `target` 的业务包；只排除构建输出，不能用无例外的 `**/target/**` 过滤源码。

| 不入库的内容 | 保留方式 |
| --- | --- |
| 本机账户文件、local-secrets、真实 application-local 配置、密钥/数据库 | 原地受限保存，不复制到候选目录 |
| outputs / output、backup、QA/cluster、test/environments | 原地保留，既有备份和证据索引继续有效 |
| 历史报告/截图/trace/日志、test/builds、历史验收脚本 | 原地保留；初始仓库不承载环境历史 |
| Maven/Node 缓存、target 构建产物、dist、IDE 设置 | 不复制，按锁定工具链重建 |
| 旧审计/数据库维护及历史制品采集脚本 | 原件保留；依赖本机账户文件、旧版本或旧证据链的脚本不进入初始白名单 |
| 历史设计与过程文档 | 原地保留；入库只收当前自洽入口和必要配置示例 |

排除项不是删除清单。此轮不迁移或清理任何运行数据、QA/cluster 或旧备份。`test/tools/verify-frontend.ps1` 是根 test 树唯一入库文件，其生成的报告继续被排除。

## 复验和导出

工具只依赖 Python 3.10+ 标准库，无网络操作、无 Git 调用。进入仓库根目录，用新的报告路径和候选目录执行：

```sh
python tools/repository/prepare.py scan --report test/reports/private-prep-new/scan.json
python tools/repository/prepare.py export --destination test/repository-candidates/private-prep-new --report test/reports/private-prep-new/export.json
```

工具拒绝覆盖已有报告/候选目录。导出前后扫描、逐文件 SHA-256 校验，清单与报告保存在候选目录外。当前工作区额外执行了 `--local-input` 与被排除本地凭据的内存比较；只记录命中位置和规则，不输出秘密值。独立克隆者不需要原维护者的本地凭据。

扫描包含常见私钥/供应商 token、凭据赋值/Setter、URL 中凭据、BCrypt 字面量、内网地址、个人路径和邮件地址。命中记录只含文件、行、规则和行摘要；模拟测试凭据、占位符、界面文案等在 [scan-reviews.json](../../.repository/scan-reviews.json) 逐条注明理由。审查项绑定精确行摘要，修改后须重新审查；真实本地值、私钥和供应商 token 不能用该表豁免。没有整目录免检规则。

启发式扫描不等于证明不存在秘密。二进制仅允许审查后的应用图片，另核对内容/用途；prepare.py 只扫描白名单文件，不扫描 Git 历史。首次提交的完整文件树已经核验；后续每次提交仍需复查 staged diff、实际 Git 忽略行为和必要的历史检查；若发现实际暴露，按泄露范围撤回并轮换，而非仅删文件。

新增文件先核实用途/内容，再加入 `allowlist.json`，运行 `python tools/repository/prepare.py ignore` 重新生成根 `.gitignore`，复验并导出新的候选目录。不能通过批量扩大通配符或 `git add -f` 跳过审查。根规则使用精确路径，新增秘密或构建产物默认被拒绝；`.gitignore` 对将来已经跟踪的文件不提供保护。

## 剩余事项（按建议顺序）

1. 仓库维护：准备 CI，覆盖后端、Agent、前端和 Docker；后续文档与源码变更按上述审阅、检查、提交和同步流程维护。五库测试使用隔离资源，受限数据库的运行器/Secrets 单独配置，不连接现有 QA/cluster。
2. 发布基线：从已记录提交重新构建候选，补齐依赖/SBOM、漏洞适用性与处置、第三方许可、制品哈希、镜像 digest、迁移线和变更日志。既有隔离验收不能替代新候选的必要回归。
3. 生产目标环境：确定数据库精确版本/字符集、主机资源、单机或双节点、插件解释器/文件资源；落实 TLS、访问控制、readiness 负载均衡、日志、告警与负责人。
4. 迁移与恢复：在另行授权后准备最新一致快照，验证原密钥/文件/在途任务、真实同包迁移、首次写入及回退；目前外层数据与 QA/cluster 仍保持原样。生产备份周期、恢复和长期实际负载仍需签收。
5. 后续目录治理：继续核对剩余目录、测试与备份恢复用途；有引用或未确认的材料保留，不按日期/命名直接删除。
6. 未来公开：确定许可证/版权、素材和数据库驱动再分发条件，补公开贡献/安全文档并再次检查历史。保持私有，不把此次入库视作公开授权。

仓库名、可见性、分支、协作者、提交身份和 HTTPS 认证已经落实，不再列为待准备信息。暂不需要生产数据库密码或 QA/cluster 密钥。
