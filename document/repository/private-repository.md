# 私有仓库本地准备

本说明记录初始白名单、忽略规则与敏感信息检查。后续本地 Git 初始化与首次提交从已核验的干净候选目录进行，不能直接把历史工作区整体入库。远程 GitHub 仓库尚未创建，未设置 remote 或推送；实际本地提交状态以 Git 日志为准。

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

启发式扫描不等于证明不存在秘密。二进制仅允许审查后的应用图片，另核对内容/用途；未扫描不存在的 Git 历史。后续真正提交时还需复查 staged diff、实际 Git 忽略行为与提交历史；若发现实际暴露，按泄露范围撤回并轮换，而非仅删文件。

新增文件先核实用途/内容，再加入 `allowlist.json`，运行 `python tools/repository/prepare.py ignore` 重新生成根 `.gitignore`，复验并导出新的候选目录。不能通过批量扩大通配符或 `git add -f` 跳过审查。根规则使用精确路径，新增秘密或构建产物默认被拒绝；`.gitignore` 对将来已经跟踪的文件不提供保护。

## 后续需要准备

1. GitHub 归属：个人账号或组织，以及准确 owner 和期望仓库名；初次必须为 Private。
2. 协作者名单和所需权限；仅自己维护可先不邀请任何人。
3. 本机 Git 提交显示姓名、邮箱（可选择平台提供的隐私邮箱）；本机登录方式选择 SSH 或 HTTPS。实际授权时在本机登录，不在聊天、源码或文档中粘贴 token/私钥。
4. 若组织要求指定默认分支、分支保护或 CI 运行器，提供要求。没有特殊要求时可后续采用 main；本轮尚未创建分支或 CI。

现在不需要生产数据库密码、QA/cluster 密钥或云服务秘密。CI 真正接入隔离数据库时再配置受限运行器和平台 Secrets。未来转公开前另行确定版权归属、许可证、品牌素材/字体/驱动及依赖再分发条件；本轮不擅自添加许可证。
