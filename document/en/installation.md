# Installation and lifecycle

[简体中文](../zh-CN/installation.md) · [Manual home](README.md)

## Bundle availability

Download the complete Native or Docker bundle and checksums from the [v1.0.0 Release](https://github.com/ifielsc/iJob/releases/tag/v1.0.0). Verify SHA-256 before extraction. Read the [release notes](releases.md) for validation scope and known risks before choosing an installation method.

## Choose an installation method

With a complete bundle, choose the guide for your operating system and runtime. Installation does not require compiling the application again. Provide any interpreters and business dependencies needed by script tasks separately.

| Installation | Guide | Prerequisites |
| --- | --- | --- |
| Native Windows | [Windows](installation/windows.md) | Java 21 and task interpreters |
| Native Linux | [Linux](installation/linux.md) | Java 21, Bash, flock and task interpreters |
| Linux Docker | [Docker](installation/docker.md) | Linux Docker Engine, the Compose plugin providing `docker compose`, and Bash |

Neither Native nor Docker runtime bundles include the application source archive. Installation and startup do not require it. For development or source review, download the matching `ijob-1.0.0-source.zip` separately; see [source builds](installation/source-build.md).

After download, verify the runtime archive against the Release-level `SHA256SUMS.txt`; after extraction, check the bundled `MANIFEST.json` and `SHA256SUMS.txt`. The bundled `source-manifest.json` contains source file hashes only; `source_archive` in `MANIFEST.json` identifies the separate source download and its hash. A version number or filename does not prove two bundles are identical. Retain the original bundle when upgrading and extract its replacement into a new program directory.

## Separate program and data

```text
programs/ijob-1.0.0/     Complete extracted program; upgrade into another directory
data/ijob-a/             IJOB_HOME: config, keys, database, logs, installation markers
```

Keep the data home outside source and program directories. First start prompts for an administrator and generates random keys. Defaults are SQLite, STATIC authentication and STANDALONE execution. Retain the administrator password and restrict access to the data home. Later starts reuse existing configuration.

## Check the installation

1. The launcher reports readiness; open the configured address and log in.
2. Run a task without external side effects using [Quick start](quickstart.md); inspect the actual instance and logs.
3. Stop, check status and start again using the platform chapter; confirm definitions and history persist.
4. Read [Configuration](configuration.md) before tuning. Use [Operations](operations.md) for account or runtime mode changes.

## Recommended daily entry point

Configure the [instance control entry](installation/instance-launcher.md) once using your platform guide. All lifecycle/status/log commands then share saved paths and language. The same page documents legacy defaults and environment variables.

For Windows services, systemd and autostart, complete initial operation and follow [service management](installation/services.md). Interactive setup selects instance paths; service management supervises OS processes. Platform configuration remains separate.

## JDBC drivers

SQLite, PostgreSQL JDBC and Microsoft JDBC for SQL Server remain included. MySQL Connector/J and Oracle JDBC/NLS are **not included** in the application, migration tool or Docker images. Database support remains available with drivers that you obtain from their official vendors under the applicable terms. The database server license is separate from the JDBC driver license.

1. Configure the instance and run the first start. If MySQL or Oracle is selected, setup retains the generated configuration and reports the missing driver directory.
2. Place a trusted MySQL Connector/J JAR or Oracle JDBC JAR in `<IJOB_HOME>/drivers/`; add the matching Oracle `orai18n` JAR when required by your character sets. Keep one driver version per vendor, verify its upstream checksum, and retain its license. Do not put these files in the product/source directory or distribute them as iJob assets.
3. Start again. Native launchers and services load this directory; the generated Docker Compose file mounts it read-only at `/opt/ijob/drivers`. Container UID 10001 must be able to read the JARs and traverse the directory. Only the installation administrator should replace drivers.

The same directory supplies MySQL/Oracle drivers for ETL resources even when the platform database is SQLite, PostgreSQL or SQL Server. Stop the instance before changing a driver and restart afterwards. Driver presence checks do not verify database connectivity or grant a license.

For an existing installation created before this change, create the external directory without replacing configuration or data. For Docker, add a read-only bind from that directory to `/opt/ijob/drivers` in the existing `compose.yaml`; retain its other settings. For source-based Compose, set `IJOB_JDBC_DRIVERS_DIR` to an absolute external directory (empty is valid for the included databases), or pass `-DriversDirectory` to `prepare.ps1`. Cluster nodes need the same driver versions.

For a direct backend JAR launch, pass `-Dloader.path=file:/absolute/path/to/drivers/` **before** `-jar`; use a percent-encoded file URI for paths containing spaces, commas or non-ASCII characters. For example: `java -Dloader.path=file:/srv/ijob-a/drivers/ -jar ijob-starter-1.0.0.jar ...`. Never copy drivers into a release JAR.
