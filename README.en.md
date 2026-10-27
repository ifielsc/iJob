# iJob 1.0.0

[简体中文](README.md) · [English manual](document/en/README.md) · [中文手册](document/zh-CN/README.md)

iJob is a task scheduling and execution platform with a Java backend, Vue frontend, optional monitoring Agent and first-run setup tool. The GitHub repository remains private.

Start with [installation](document/en/installation.md), then [your first task](document/en/quickstart.md). A clone or source ZIP can produce a complete native package:

```sh
python tools/distribution/build.py --target native --output ../ijob-build-001
```

Build prerequisites are Python 3.11+, JDK 21, Maven 3.9.11, Node 24.14.1 and pnpm 11.19.0. A native runtime needs Java 21 and its platform's launcher dependencies, not the frontend build tools. For container builds use --target docker with a Linux amd64 Docker engine; see the manual for setup and lifecycle commands.

| Location | Purpose |
| --- | --- |
| develop/api/ijob | Backend, plugin SPI, setup and historical migration tooling |
| develop/api/ijob-monitor-agent | Optional monitoring Agent |
| develop/web/ijob | Frontend, locked dependencies and browser/unit tests |
| develop/deployment | Native launchers and Docker templates |
| document/en and document/zh-CN | Paired user/operator manuals |
| tools/distribution | Clean-source packaging and manual checks |

Keep configuration, credentials and databases outside the source and program directory. Setup creates your own administrator and key; no shared default password is supplied. SQLite supports one instance. Server-database and cluster operation require explicit preparation. Product versions stay 1.0.0, Local JAR protocol 1 and formal new-install migrations V1.

Before contributing, read AGENTS.md and document/standards/README.md. Full quality gates and isolated integration boundaries are documented in [development](document/en/development.md). Packaging logs distinguish checks executed from tests skipped; compilation is not release acceptance.

The current Linux, Docker, Windows service and local-cache scope is collected in the [acceptance summary](document/ci/isolated-integration.md#2026-10-27-验收汇总). Two Windows instances and PostgreSQL have passed isolated VM checks. Historical matrices, tested candidates and each CI run keep separate evidence; they are not combined into one execution or a complete release approval.

First-party code uses the [MIT License](LICENSE), Copyright (c) 2026 ifielsc. Third-party license and vulnerability reconciliation remains a separate public-redistribution gate. Source synchronization does not update an existing deployment or authorize public publication.
