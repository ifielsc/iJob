# Build a complete bundle from source

[简体中文](../../zh-CN/installation/source-build.md) · [Manual home](../README.md)

## Prerequisites

Clone with an authorized GitHub account or extract the source ZIP of a specific commit. Work in the repository root containing `tools/`. Native builds require Python 3.11+, JDK 21, Maven 3.9.11, Node 24.14.1 and pnpm 11.19.0 on PATH; point JAVA_HOME to the JDK. Preserve lockfiles. The first build needs Maven/npm dependency access.

## Native bundle: one command

```sh
python tools/distribution/build.py --target native --output ../ijob-build-001
```

Use a new, nonexistent output directory outside the repository with an existing parent. The script copies an isolated snapshot from the exact source allowlist, packages backend/setup/Agent, installs frontend dependencies, runs complete `pnpm check`, and assembles backend, frontend, optional Agent/SPI, launchers and bilingual manuals. No manual target/dist copying or second compilation is required.

| Output | Purpose |
| --- | --- |
| `ijob-1.0.0/` | Complete runnable program directory |
| ZIP / tar.gz and SHA256SUMS.txt | Transfer and integrity checks |
| Bundle manifest / source snapshot | Source, toolchain and packaging-result provenance |
| `reports/` | Logs for individual build stages |
| `build-source/` | This build's isolated working snapshot, not another maintained checkout |

Windows and Linux use the same Java/Web artifacts with platform-specific launchers. BAT, SHELL, Python and other task interpreters must still match the executing node's operating system.

## Docker bundle

On a build machine with Docker Engine/Compose:

```sh
python tools/distribution/build.py --target docker --output ../ijob-docker-build-001
```

Docker builds require base-image and dependency downloads. Output includes an offline image archive, exact image-ID locks and launchers. Transfer it to the target Linux host and follow [Docker installation](docker.md). A native bundle is not a complete Docker bundle.

## Diagnose a failed build

Inspect the report named by the error: tool versions → dependency download → Java compilation → frontend check → image build/export. Fix the cause and choose a new output directory, retaining the failed attempt. A source ZIP can build a fresh-install bundle but cannot establish passage of Git-history-dependent migration tests. Backend and browser tests are separate [quality gates](../development/quality-gates.md).
