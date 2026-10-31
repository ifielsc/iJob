# 安全漏洞报告 / Security vulnerability reporting

## 简体中文

### 当前状态

iJob 1.0.0 尚在准备公开发布。普通问题使用 GitHub Issues，安全漏洞使用 GitHub Private vulnerability reporting（私密漏洞报告），不另设报告邮箱。仓库尚未公开，这两个公共入口尚未开放；本文件不表示私密报告功能已经启用。

### 普通问题与建议

仓库公开且 Issues 开放后，在项目的 Issues 中提交使用问题、一般缺陷或功能建议。请提供版本、操作系统、部署方式、复现步骤、预期与实际结果，以及必要的脱敏日志。

### 安全漏洞

仓库公开且私密报告功能启用后，进入项目的 **Security and quality → Report a vulnerability**，通过 GitHub 表单提交。维护者与报告者在私密报告中沟通；不要把漏洞细节、利用代码或敏感附件放进公开 Issue、PR 或评论。

请提供受影响版本、相关组件与部署条件、影响范围、复现步骤或最小概念验证，以及可能的缓解措施。使用测试数据并脱敏；不要提交真实密码、令牌、私钥、生产数据库或完整部署配置。

如果未看到报告按钮，说明入口尚不可用；不要改用公开 Issue 披露漏洞细节。报告后请留意 GitHub 中的回复。私密报告不会因提交而自动公开，修复与披露安排在私密渠道协商。

### 维护者启用检查

正式公开时，在仓库 **Settings → Advanced Security → Private vulnerability reporting** 启用功能，验证报告入口，并确认仓库的安全通知订阅。`SECURITY.md` 与该开关独立，添加本文件不会启用功能。通知可以使用现有 GitHub 账号邮箱，不需要新增或公开联系邮箱。

## English

### Current status

iJob 1.0.0 is being prepared for public release. General questions use GitHub Issues; security vulnerabilities use GitHub Private vulnerability reporting, with no separate reporting mailbox. The repository is not public yet and these public entry points are not open. This policy does not mean private reporting has been enabled.

### General questions and suggestions

Once the repository is public and Issues is available, use the project's Issues for usage questions, ordinary bugs and feature requests. Include the version, operating system, deployment method, reproduction steps, expected versus actual results, and relevant redacted logs.

### Security vulnerabilities

Once the repository is public and private reporting is enabled, open **Security and quality → Report a vulnerability** and submit the GitHub form. Maintainers and reporters communicate within the private report. Do not post vulnerability details, exploit code or sensitive attachments in public issues, pull requests or comments.

Include affected versions, components and deployment conditions, impact, reproduction steps or a minimal proof of concept, and possible mitigations. Use test data and redact sensitive information. Do not submit real passwords, tokens, private keys, production databases or complete deployment configurations.

If the reporting button is absent, the channel is not available; do not disclose vulnerability details in a public issue instead. Check GitHub for replies after submitting. Submission does not automatically publish the report; coordinate remediation and disclosure privately.

### Maintainer activation check

At formal publication, enable **Settings → Advanced Security → Private vulnerability reporting**, verify the reporting entry point and check security-notification subscriptions. `SECURITY.md` and the feature switch are independent; adding this file does not enable reporting. Notifications can use the existing GitHub account email without creating or publishing a contact mailbox.

[GitHub: enable private reporting and notifications](https://docs.github.com/en/code-security/how-tos/report-and-fix-vulnerabilities/configure-vulnerability-reporting/configure-for-a-repository) · [GitHub: submit a private report](https://docs.github.com/en/code-security/how-tos/report-and-fix-vulnerabilities/report-privately)
