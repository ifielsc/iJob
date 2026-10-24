# iJob 1.0.0 user manual

[简体中文](../zh-CN/README.md) · [Manual home](README.md)

## Start with your goal

| Goal | Reading path |
| --- | --- |
| First install/build | [Installation](installation.md) → Windows/Linux/Docker → [Quick start](quickstart.md) |
| Tune platform settings | [Configuration](configuration.md) → routing/plugins/capacity → [249-entry catalog](configuration-catalog.md) |
| Configure/use pages | [Page reference](user-guide/README.md) → [11 plugins](user-guide/plugins/README.md) → [16 scenarios](examples.md) |
| Accounts, mode changes, backup/upgrades | [Operations](operations.md) → relevant workflow |
| Source changes/release candidates | [Development](development.md) → [Follow-up](development/release-checklist.md) |

## Terms

A task performs one plugin action; a workflow combines tasks/subjobs/dependencies. Saving publishes a definition version. A trigger is an execution request; an instance is actual execution. Resources hold external connections/secrets; parameter templates describe inputs. Node roles differ from account roles; execution groups are not user groups.

Keep program bundles separate from IJOB_HOME, which contains configuration, secrets and data. Screenshots use an independent real environment with synthetic data and separate language captures. Each scenario states its verification scope. GitHub remains private. The [approved structure/scope](documentation-plan.md) records organizational principles.
