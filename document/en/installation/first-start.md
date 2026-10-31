# First initialization and login

[简体中文](../../zh-CN/installation/first-start.md) · [Manual home](../README.md)

With a complete bundle, configure an independent instance for your platform. See [installation selection](../installation.md) for download availability.

## 1. Configure an instance launcher

Follow [Windows](windows.md), [Linux](linux.md) or [Docker](docker.md) and run configure with separate control and data directories. Configure does not start the application or create a browser login session.

## 2. Start and initialize

1. Run start from the newly configured instance control directory.
2. For a new installation, choose the platform database and create your own administrator credentials.
3. SQLite is an option for single-instance learning. Selecting a server database does not enable clustering.
4. Wait for initialization/startup, then inspect this instance's status and logs.

An existing valid installation keeps its settings. Do not reinitialize it to overwrite configuration. Keep the platform database separate from tutorial business databases.

## 3. Log in

The default local address is `http://127.0.0.1:18080`; use your configured port if changed. Use the account created during initialization. There is no shared default account/password in this guide. Complete an initial password change if prompted.

Inspect node readiness in the workspace and run a harmless script through the [quick start](../quickstart.md). Login, node readiness and business execution success are separate checks.

## Common problems

| Symptom | Next step |
| --- | --- |
| Cannot open the address | Check port, process identity, startup logs and container health |
| Credentials rejected | Confirm STATIC/DATABASE mode and the account source |
| Read access but no create action | Creation requires ADMIN; consult the permission matrix |
| Login works but task waits | Check Workers, execution groups, concurrency and resources |

[Account permissions](../user-guide/users-audits.md) · [Account modes](../operations/accounts.md) · [Troubleshooting](../operations/troubleshooting.md)
