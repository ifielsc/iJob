# Trusted Local JAR plugins

[简体中文](../../../zh-CN/user-guide/plugins/local-jar.md) · [Manual home](../../README.md)

## Entry and prerequisites

Tasks → New/Edit → plugin `LOCAL_JAR`. Prepare the resources, permissions and execution-node dependencies listed below. See [Tasks](../tasks.md) for shared timeout/retry/publication behavior.

## Fields and behavior

| Page field/action | Purpose and rules |
| --- | --- |
| Configuration format / SPI protocol (validated automatically) | New-namespace configuration/protocol use 1; SPI Maven version is 1.0.0. |
| JAR filename / File size / SHA-256 checksum | Deploy the reviewed artifact to the API and Worker artifact directories, then enter its filename and actual SHA-256. The page does not upload files or accept paths; the server validates size. |
| Artifact version / Entry class | Record implementation version and entry class implementing this SPI. |
| Business parameters | Enter names, types and values in the parameter editor; objects and arrays can be edited level by level. The plugin defines the schema and validation. Stored JSON does not require pasting a complete task JSON blob. |

## Minimal example and verification

Prepare the dedicated resources above, fill the form, save, then trigger manually and verify the instance, outputs and target system as described below. Saving alone does not execute.

| Setting | Value to enter |
| --- | --- |
| JAR filename | `hello-job-1.0.0.jar` (deploy to API and Worker artifact directories first) |
| SHA-256 checksum | Enter the actual 64-digit hexadecimal checksum |
| Entry class | `com.example.jobs.HelloTask` (must match the actual artifact) |
| Business parameters | Enter names, types and values required by this plugin |

Enable the plugin and prepare Worker directories as documented. Use the host SPI without bundling platform classes under com.ifielsc.ijob, com.ifelio.ijob or com.ifeel.ijob. Recompile old-namespace plugins and register entry/hash again. Begin with harmless code and verify outputs, stop and exceptions. Local JAR is not an untrusted-code sandbox.

Replace example resources, paths and object names with values from your environment. See the [case index](../../examples.md) for actual external-protocol verification status.

## Technical key reference (optional)

These stored keys help developers and troubleshooting; they are not a task JSON blob to paste into the UI. Automatically validated metadata is not a manual form input.

| Page field/group | Stored keys |
| --- | --- |
| Configuration format / SPI protocol (validated automatically) | `formatVersion / spiVersion` |
| JAR filename / File size / SHA-256 checksum | `jarFile / jarSize / sha256` |
| Artifact version / Entry class | `implementationVersion / entryClass` |
| Business parameters | `userConfigJson` |

SPI: `com.ifielsc:ijob-plugin-spi:1.0.0`; Manifest: `IJob-Plugin-Api-Version: 1`.
