# Trusted Local JAR plugins

[简体中文](../../../zh-CN/user-guide/plugins/local-jar.md) · [Manual home](../../README.md)

## Entry and prerequisites

Tasks → New/Edit → plugin `LOCAL_JAR`. Prepare the resources, permissions and execution-node dependencies listed below. See [Tasks](../tasks.md) for shared timeout/retry/publication behavior.

## Fields and behavior

| Field/action | Purpose and rules |
| --- | --- |
| `formatVersion / spiVersion` | New-namespace configuration/protocol use 1; SPI Maven version is 1.0.0. |
| `jarFile / jarSize / sha256` | Upload/select reviewed artifacts; verify filename, size and SHA-256 rather than fabricating metadata. |
| `implementationVersion / entryClass` | Record implementation version and entry class implementing this SPI. |
| `userConfigJson` | Business JSON passed to the plugin; its schema/validation belongs to the plugin. |

## Minimal example and verification

```text
Maven SPI: com.ifielsc:ijob-plugin-spi:1.0.0
Manifest: IJob-Plugin-Api-Version: 1
```

Enable the plugin and prepare Worker directories as documented. Use the host SPI without bundling platform classes under com.ifielsc.ijob, com.ifelio.ijob or com.ifeel.ijob. Recompile old-namespace plugins and register entry/hash again. Begin with harmless code and verify outputs, stop and exceptions. Local JAR is not an untrusted-code sandbox.

Snippets require your resource IDs/paths. See the [case index](../../examples.md) for actual external-protocol verification status.
