# iJob 1.0.0 user manual

[简体中文](../zh-CN/README.md)

iJob configures, orchestrates, schedules and observes tasks. The repository remains private; documentation and distributions are prepared for independent installation. The first-party MIT license does not constitute approval to redistribute every third-party binary.

Read these guides in order:

1. [Installation, builds and lifecycle](installation.md): build a clone/source ZIP or use a complete distribution.
2. [Quick start](quickstart.md): create, run and inspect your first task.
3. [Configuration reference](configuration.md): external settings, supported adjustments and validation.
4. [Examples](examples.md): scripts, HTTP, SQL, files, ETL, DAGs and package transfer.
5. [Operations and troubleshooting](operations.md): backup, restore, upgrades, accounts and cluster boundaries.
6. [Development and verification](development.md): toolchains, quality gates and documentation maintenance.

| Term | Meaning |
| --- | --- |
| Task | One plugin action and its configuration, such as a script or SQL statement |
| Job | A DAG containing tasks or child jobs and their dependencies |
| Published version | A version created by saving; jobs reference specific task/child-job versions |
| Schedule | When to trigger a target, independently of that target's enabled state |
| Run | One accepted execution, with its own state, timestamps and logs |
| Resource | A database, file or service connection configuration |
| Worker | The host/container that executes tasks; paths and interpreters belong to that node |

The UI supports Chinese/English, light/dark themes and display timezones. Display timezone and schedule business timezone are separate. Saving a task does not run it; starting the platform does not create example business objects.
