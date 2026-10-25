# Deploy and invoke a trusted extension

[简体中文](../../zh-CN/examples/local-jar.md) · [Manual home](../README.md)

**Verification: tested in this round’s isolated environment within the scope below.**

Fields: [Trusted Local JAR plugins](../user-guide/plugins/local-jar.md).

## Prepare, configure and run

Compile a harmless hello-localjar extension against new-namespace SPI 1.0.0 with Manifest protocol 1, excluding platform classes. Register SHA-256, implementation version, entry class/dependencies, install in an independent Worker plugin directory and enable the switch.

Deploy the JAR on the execution node first, then select the file and verify metadata in the UI; the UI does not upload files. Enter the plugin's JSON object in the user configuration field (API key userConfig). Run and inspect output. Test wrong hashes/entry classes/timeouts/stops separately, preserving the valid artifact. Prepare every Worker; A file on the API node does not prove availability on every node. Recompile old-namespace plugins.

## Scope verified this round

An isolated Linux Worker verified SPI 1.0.0/protocol 1 success, explicit failure, timeout, manual stop, resource closure and rejection of incorrect hashes/entry classes on save. Artifact consistency across Workers remains separate.

## Finish

Retain inputs, definition versions, trigger/instance IDs, sanitized logs and target checks. Stop only processes/schedules owned by this case; other environments and historical evidence are not cleanup targets.
