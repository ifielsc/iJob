# Deploy and invoke a trusted extension

[简体中文](../../zh-CN/examples/local-jar.md) · [Manual home](../README.md)

**Verification: configuration/workflow example; this round did not complete end-to-end acceptance of its external dependencies.**

Fields: [Trusted Local JAR plugins](../user-guide/plugins/local-jar.md).

## Prepare, configure and run

Compile a harmless hello-localjar extension against new-namespace SPI 1.0.0 with Manifest protocol 1, excluding platform classes. Register SHA-256, implementation version, entry class/dependencies, install in an independent Worker plugin directory and enable the switch.

Select/upload in the UI, verify metadata and use the plugin's minimal userConfigJson. Run and inspect output. Test wrong hashes/entry classes/timeouts/stops separately, preserving the valid artifact. Prepare every Worker; API-node upload does not prove file availability on all nodes. Recompile old-namespace plugins.

## Finish

Retain inputs, definition versions, trigger/instance IDs, sanitized logs and target checks. Stop only processes/schedules owned by this case; other environments and historical evidence are not cleanup targets.
