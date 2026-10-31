# Trusted Local JAR plugins

[简体中文](../../../zh-CN/user-guide/plugins/local-jar.md) · [Manual](../../README.md)

Applies to **1.0.0**. Tasks → New/Edit → plugin `LOCAL_JAR`.

## Before you start

Use ADMIN to create, configure and save tasks. ADMIN or OPERATOR can execute published definitions; ADMIN reads detailed logs and input/output variables. Resources must be enabled and reachable from the execution node. See [tasks](../tasks.md) for common timeout, retry and version settings, and the [permission matrix](../users-audits.md).

## Field reference

“Default” means the **initial value of a new 1.0.0 form**. An empty value means the field has not been filled in; required fields still need a value. Visibility follows the selected branch; conditional requirements apply only to that branch.

### Artifact and parameters

| Field | Required | Default | Visible when | Scope |
| --- | --- | --- | --- | --- |
| JAR filename | Required | Empty | Always | Current task version |
| SHA-256 checksum | Required | Empty | Always | Current task version |
| Entry class | Required | Empty | Always | Current task version |
| Business parameters | Defined by the extension contract | Empty object | Always | Current task version |
| File size | Automatically verified | None | After saving | Current task version |
| Artifact version | Automatically verified | None | After saving | Current task version |
| SPI version | Automatically verified | 1 | After saving | Current task version |

- **JAR filename**: Name an actual JAR in the extension directory, without a path.
- **SHA-256 checksum**: Verify the exact bytes of the reviewed JAR.
- **Entry class**: Provide the fully qualified entry class implementing the extension interface.
- **Business parameters**: Provide JSON business parameters according to the extension contract. New forms start with an empty object; the root may be an object, array, string, number, boolean or null.
- **File size**: Actual artifact size recorded by artifact verification.
- **Artifact version**: Extension implementation version recorded from artifact metadata.
- **SPI version**: Interface protocol version verified from the artifact; the current protocol is 1.

## Behavior and boundaries

- Business parameters use the userConfig key.
- The JAR filename has no path; SHA-256 consists of 64 hexadecimal characters.
- The API and Worker both require the same reviewed artifact; the page does not upload JAR files.
- Configuration protocol 1; host SPI Maven version 1.0.0. Configuration limits are 256 KiB, depth 64 and 10000 nodes.
- Extensions are not an untrusted-code sandbox; do not bundle platform classes.

## Typical configuration: Run a trusted HelloTask extension

Fill in the form below, replacing resource names with your enabled resources. This is a configuration guide, not importable JSON. Follow the tutorial linked below for data preparation and execution.

Compile the actual HelloTask artifact from the linked tutorial, deploy it to the API and target Worker, and enter its verified SHA-256. Use an empty object for this example's business parameters.

| Setting | Value |
| --- | --- |
| JAR filename | hello-job-1.0.0.jar |
| SHA-256 checksum | Actual artifact SHA-256; placeholders are invalid |
| Entry class | com.example.jobs.HelloTask |
| Business parameters | {} |

**Expected result:** With HelloTask compiled from the linked tutorial, the instance is SUCCESS, logs contain hello-localjar and output variable rows is the number 3.

[Follow the full tutorial](../../examples/local-jar.md). Save the definition before running it; inspect execution state and business results separately.

## Configuration keys

Use these keys when inspecting imported configuration, API payloads or errors. `[]` denotes a collection item; the form does not require you to enter key names.

| Category / field | Configuration key |
| --- | --- |
| Artifact and parameters / JAR filename | `jarFile` |
| Artifact and parameters / SHA-256 checksum | `sha256` |
| Artifact and parameters / Entry class | `entryClass` |
| Artifact and parameters / Business parameters | `userConfig` |
| Artifact and parameters / File size | `jarSize` |
| Artifact and parameters / Artifact version | `implementationVersion` |
| Artifact and parameters / SPI version | `spiVersion` |

## Related pages

[Tasks and versions](../tasks.md) · [Parameters and expressions](../../reference/variables.md) · [Scenario tutorials](../../examples.md)
