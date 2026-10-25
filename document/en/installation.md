# Installation, build and lifecycle

[简体中文](../zh-CN/installation.md) · [Manual home](README.md)

## Choose an entry point

| What you have | Next step | Requirements on the installation machine |
| --- | --- | --- |
| GitHub clone / source ZIP | [Build once](installation/source-build.md) to obtain a complete bundle | Build toolchain; a separate build machine is fine |
| Complete native bundle | [Windows](installation/windows.md) or [Linux](installation/linux.md) | Java 21; provide task interpreters separately |
| Complete Docker bundle | [Linux Docker](installation/docker.md) | Docker Engine, Compose v2, Linux containers |

`python tools/distribution/build.py --target native --output ../ijob-build-001` **compiles and packages the application**. `pnpm check` and Maven `-DskipTests` are internal steps, not extra commands to run after receiving the bundle. `-DskipTests` means backend tests were not run by packaging. Complete [quality gates](development/quality-gates.md) are for development/release acceptance.

The generated `ijob-1.0.0/`, ZIP and tar.gz use the same complete delivery format as native deliverables. Different source revisions or build runs may have different bytes and hashes. Updating Git does not update an older delivery directory. Compare the manifest and SHA256SUMS.txt, not filenames alone.

## Separate program and data

```text
programs/ijob-1.0.0/     Complete extracted program; upgrade into another directory
data/ijob-a/             IJOB_HOME: config, keys, database, logs, installation markers
```

Keep the data home outside source and program directories. First start prompts for an administrator and generates random keys. Defaults are SQLite, STATIC authentication and STANDALONE execution. Retain the administrator password and restrict access to the data home. Later starts reuse existing configuration.

## Installation acceptance

1. The launcher reports readiness; open the configured address and log in.
2. Run a task without external side effects using [Quick start](quickstart.md); inspect the actual instance and logs.
3. Stop, check status and start again using the platform chapter; confirm definitions and history persist.
4. Read [Configuration](configuration.md) before tuning. Use [Operations](operations.md) for account or runtime mode changes.

## Recommended daily entry point

Configure the [instance control entry](installation/instance-launcher.md) once using your platform guide. All lifecycle/status/log commands then share saved paths and language. The same page documents legacy defaults and environment variables.
