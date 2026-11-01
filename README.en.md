# iJob 1.0.0

[简体中文](README.md) · [English manual](document/en/README.md) · [中文用户手册](document/zh-CN/README.md)

iJob is a task scheduling and execution platform for task configuration, visual workflow design, scheduled and event-based triggers, monitoring and execution logs. It includes a Java backend, Vue frontend, optional monitoring Agent, a first-run setup tool.

## Release status

**The official 1.0.0 release is in preparation. Binary downloads are not available yet.** Official website: [https://ijob.ifielsc.com](https://ijob.ifielsc.com) (not live yet). GitHub Release and public feedback links will be added when available. A source ZIP is build input, not a ready-to-run bundle. A successful local build does not establish release acceptance.

An independent online demo is not currently offered, and no opening date is scheduled. Explore the [scenario tutorials](document/en/examples.md) and actual interface screenshots in the manual.

## Get started

1. Read the [core concepts](document/en/concepts.md) and [deployment requirements and compatibility](document/en/installation/requirements.md).
2. Choose an [installation method](document/en/installation.md), then complete [initialization and first login](document/en/installation/first-start.md).
3. Follow the [quick start](document/en/quickstart.md) and explore the [scenario tutorials](document/en/examples.md).

Task capabilities include SQL, scripts, HTTP APIs, file transfer, ETL, batch synchronization, data checks, notifications and Local JAR plugins. See the [user guide](document/en/user-guide/README.md) for parameters, prerequisites and limits, the [operations manual](document/en/operations.md) for administration and the [configuration guide](document/en/configuration.md) for settings.

## Build from source

Prerequisites: Python 3.11+, JDK 21, Maven 3.9.11, Node 24.22.1 and pnpm 11.28.8. From the repository or extracted source ZIP root, use a new output directory outside the source tree:

```sh
python tools/distribution/build.py --target native --output ../ijob-build-001
```

The builder assembles the backend, frontend, Agent, launchers, documentation, source and checksums. A Native runtime needs Java 21 and platform launcher dependencies, but not the frontend build tools. For Docker builds use `--target docker` with a Linux amd64 Docker engine; see [building from source](document/en/installation/source-build.md).

Packaging runs frontend checks, while Maven packaging skips backend tests. Browser and environment-specific acceptance run separately. Build logs and MANIFEST record that scope; unexecuted or skipped gates are not passes.

## Deployment boundaries

- Keep configuration, keys and databases outside the source and program directories. Setup creates the deployer's own administrator and keys; no shared default credentials are supplied.
- SQLite supports one instance. Server databases and clusters require explicit preparation. Multiple processes on one host do not provide physical high availability.
- Stop, timeout and retry do not automatically roll back effects in external systems. Design write tasks for idempotency and verify business results.
- A configurable database or protocol does not mean every version and combination has been verified. Consult the [compatibility guide](document/en/installation/requirements.md) and actual release notes.
- The current product version is 1.0.0, Local JAR protocol is 1 and formal new-install database migrations are V1.

## Source and development

| Directory | Contents |
| --- | --- |
| `develop/api/ijob` | Backend, plugin SPI and first-run setup tool |
| `develop/api/ijob-monitor-agent` | Optional monitoring Agent |
| `develop/web/ijob` | Vue frontend, locked dependencies and automated tests |
| `develop/deployment` | Native launchers, Docker templates and configuration examples |
| `document/zh-CN`, `document/en` | Paired user and operations manuals |
| `tools/distribution` | Distribution builds and documentation checks |

Before developing, read [AGENTS.md](AGENTS.md), the [standards index](document/standards/README.md) and [development guide](document/en/development.md). The [CI guide](document/ci/basic-ci.md) distinguishes current-file checks, Basic, Full and applicable runtime matrices. Historical tests do not automatically establish final-candidate acceptance. Maintenance procedures are in the [repository guide](document/repository/private-repository.md).

## Feedback and security

Use **GitHub Issues** for general questions, ordinary bugs and feature requests. Use **GitHub Private vulnerability reporting** for security vulnerabilities, with no separate reporting mailbox. These entry points will open once the repository is public and the respective features are enabled; they are not available yet.

For general feedback, include the version, operating system, deployment method, reproduction steps, expected versus actual results and redacted logs. For vulnerabilities, submit privately through **Security and quality → Report a vulnerability**, describing affected versions, impact and reproduction. Do not disclose vulnerability details in public issues, pull requests or comments, or submit real passwords, tokens, private keys, databases or complete configurations. See the [security reporting policy](SECURITY.md) for the process and current status.

## Copyright and license

iJob's own code uses the [MIT License](LICENSE), Copyright (c) 2026 ifielsc. Third-party components retain their own copyrights and licenses. Each actual release's LICENSE, NOTICE, component inventory and applicable terms describe its distribution scope. Source licensing, repository visibility and binary redistribution are reviewed separately; see the [licensing and asset standard](document/standards/licensing.md).
