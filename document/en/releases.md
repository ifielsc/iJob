# Versions and changelog

[简体中文](../zh-CN/releases.md) · [Manual home](README.md)

This documentation applies to **iJob 1.0.0**. Public downloads are not available yet. Documentation changes and formal product releases are stated separately.

## 1.0.0 scope

| Area | Documented scope |
| --- | --- |
| Definitions and orchestration | Task versions, workflow publication, dependencies, gateways and subjob references |
| Task capabilities | SCRIPT, SQL, API_CALL, EVENT, DATA_CHECK, FILE_TRANSFER, PUSH, ETL, BATCH_ETL, SERVER_MONITOR, LOCAL_JAR |
| Execution | Triggers, instances, topology, logs, inputs/outputs and run controls |
| Scheduling and administration | Time schedules, calendars, routing, concurrency, resources, accounts and audit |
| Deployment | Native Windows/Linux launchers, Linux Docker, standalone and cluster configuration |

See each page for conditions and limitations. Product version 1.0.0, task/workflow definition versions, Local JAR protocol 1 and formal database installation migration V1 are separate identifiers.

## Local 1.0.0 candidate (2026-11-01, not published)

The first formal installation starts with an empty database at V1. `ijob-migration-tool`, which converted development-era databases to V1, is no longer distributed. Do not start this application against an old database. Deployers must lawfully obtain MySQL Connector/J and Oracle JDBC/NLS and place them in the instance's external driver directory. PostgreSQL, SQL Server and SQLite drivers remain bundled. See [platform databases](configuration/platform-database.md).

Native installation requires user-provided Java 21 and OS dependencies. The Docker bundle targets Linux amd64; backend/Agent include Temurin 21.0.13+9 and Ubuntu 24.04 user space. Native installation is not restricted to Ubuntu; see [runtime requirements](installation/requirements.md).

### Download inventory

| File | Purpose |
| --- | --- |
| `ijob-1.0.0-native.zip` / `ijob-1.0.0-native.tar.gz` | Native programs; choose one archive format |
| `ijob-1.0.0-docker.zip` / `ijob-1.0.0-docker.tar.gz` | Docker programs, templates and offline images; choose one archive format |
| `ijob-1.0.0-source.zip` | Project source for this version, not a runtime bundle |
| `ijob-1.0.0-third-party-sources.zip` | Matching applicable third-party corresponding source, patches and build materials |
| `SHA256SUMS.txt` | SHA-256 values for the six archives above |

Runtime bundles contain both manuals and license notices. Matching third-party source must be available from the same release download location at no additional charge. Public downloads are not open; no placeholder download links are provided.

On Linux, run `sha256sum --check --ignore-missing SHA256SUMS.txt` to verify the files you downloaded. In PowerShell, compare `Get-FileHash -Algorithm SHA256 <downloaded-file>` with the inventory before extraction. Do not mix identically named files from different candidates. SHA-256 checks byte integrity; it is not a digital signature.

### Feedback and security vulnerabilities

The source repository is public. Use [GitHub Issues](https://github.com/ifielsc/iJob/issues) for general questions, ordinary bugs and feature requests, and the enabled [GitHub private vulnerability reporting](https://github.com/ifielsc/iJob/security/advisories/new) for security vulnerabilities. Sign in to GitHub to submit a report. No separate reporting mailbox is used; official runtime packages have not been released.

For general feedback, include the version, operating system, deployment method, reproduction steps, expected versus actual results and redacted logs. Submit vulnerabilities privately through **Security and quality → Report a vulnerability**, including affected versions, impact and reproduction. Do not post vulnerability details in public issues, pull requests or comments, or submit real passwords, tokens, private keys, databases or complete configurations. If the reporting button is absent, do not disclose vulnerability details in a public issue instead.

### Known risks and validation scope

The local review as of 2026-11-01 retains known scan findings: 31 Ubuntu package-level findings in each backend/Agent image (27 MEDIUM, 4 LOW; 24 distinct CVEs across both), with no fixed versions available through the official updates checked then. These packages are distributed inside the images and remain in scope even though users supply their hosts. The web image had zero findings in this scan.

Spring MVC CVE-2026-47884 and CVE-2026-47890 retain their raw CRITICAL scanner ratings. Current route assertions found no related XSLT/implicit views or SSE fragment features; that is not a patch. MyBatis shaded OGNL CVE-2026-113730 is MEDIUM. A MyBatis upgrade produced an actual pagination incompatibility with the current PageHelper, so the verified version and risk record remain. Do not describe these findings as fixed or claim zero vulnerabilities. The scan used Trivy 0.76.1 and databases updated on 2026-10-31; check freshness before formal publication.

See [candidate checks](development/release-checklist.md) for evidence and unexecuted scope. Packaging skips, historical environments and unexecuted Full/expanded matrices are not passes in this run. Before the formal release, verify that the actual assets match their commit, remote CI and acceptance evidence, then verify real download links after upload. The source repository and reporting channels are public and enabled; a public repository and successful CI do not mean runtime packages have been released. Runtime downloads are not open yet.

## Documentation update: 2026-10-31

- Added order reconciliation, difference approval, quality blocking, report delivery and receiver monitoring extensions.
- Moved order fixture data and receiver code into a companion setup page.
- Corrected installation and quickstart instructions, unified footers, and tightened homepage and inline search spacing.
- Product version remains 1.0.0.

The tutorials are also grouped into a learning path. The 17 scenarios include concrete SSH/SFTP, Kafka and email, trusted JAR, missed-run recovery, shared concurrency and configuration-transfer steps. New bilingual screenshots show actual runs; execution success, data quality, message delivery and metric health are checked separately.

## Documentation update: 2026-10-29

- Added core concepts, a glossary, variable expressions, first initialization and runtime requirements.
- Added a role/action matrix and explained templates versus execution snapshots.
- Organized plugin fields and separated UI initial values from server omission behavior.
- Restructured scenarios as steps with expected results, troubleshooting and cleanup.
- Added version paths, bilingual navigation and local search; improved screenshot presentation on the website.

These are website/documentation changes, not a new product release or proof that new steps ran against every external environment.

## Documentation versions and links

The documentation site identifies its current version and uses version-specific paths. Existing unversioned links remain accessible. When a later product version is established, retain 1.0.0 pages and identify applicable or evidence-backed introduction versions. Do not offer incorrect language/version links for missing pages.

## Bundles and upgrades

For a formal bundle, verify its manifest, SHA256SUMS.txt, platforms and release notes. Files sharing version 1.0.0 need not be byte-identical. Read [backup/restore](operations/backup-restore.md) and [upgrade/rollback](operations/upgrade-rollback.md) before upgrading.

[Core concepts](concepts.md) · [Runtime requirements](installation/requirements.md) · [Manual index](README.md)
