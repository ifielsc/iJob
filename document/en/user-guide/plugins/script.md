# Script

[简体中文](../../../zh-CN/user-guide/plugins/script.md) · [Manual home](../../README.md)

## Entry and prerequisites

Tasks → New/Edit → plugin `SCRIPT`. Prepare the resources, permissions and execution-node dependencies listed below. See [Tasks](../tasks.md) for shared timeout/retry/publication behavior.

## Fields and behavior

| Page field/action | Purpose and rules |
| --- | --- |
| Execution mode / SSH resource | LOCAL executes on the Worker/container; SSH uses an SSH resource and executes remotely. |
| Script engine / Python interpreter | Match the execution OS; provide an installed interpreter path where needed. Use BAT on Windows and SHELL /bin/sh on Linux. |
| Script content / Working directory / Environment variables | Script text, working directory and environment. Paths belong to the execution machine, not the browser. Store secrets in resources. |
| Script encoding / Output encoding | Configure script-file encoding and output decoding independently; prefer UTF-8. charset is a compatibility field. |

## Minimal example and verification

Prepare the dedicated resources above, fill the form, save, then trigger manually and verify the instance, outputs and target system as described below. Saving alone does not execute.

| Setting | Value to enter |
| --- | --- |
| Execution mode | Local Worker (LOCAL) |
| Script engine | BAT on Windows; SHELL on Linux |
| Working directory | Leave empty for the first exercise |

```bat
echo hello-ijob
echo __VAR__:rows=3
exit 0
```

Choose LOCAL/BAT for the example; on Linux use SHELL with /bin/sh and the same echo/exit commands. A nonzero exit code fails the task. stdout/stderr are recorded in instance logs with STDOUT/STDERR labels. Limits include 16 KiB per line, 256 KiB capture/log budgets, up to 1000 captured lines, 16 KiB per variable and 100 variables. `__VAR__:rows=3` creates an output variable whose value is redacted in logs. Truncated output is not a large-file store. Stop requests terminate the process but cannot undo completed writes.

Replace example resources, paths and object names with values from your environment. See the [case index](../../examples.md) for actual external-protocol verification status.

## Real page examples

Windows LOCAL/BAT example.

![Windows LOCAL/BAT example.](../../images/manual/script-editor.png)

## Technical key reference (optional)

These stored keys help developers and troubleshooting; they are not a task JSON blob to paste into the UI. Automatically validated metadata is not a manual form input.

| Page field/group | Stored keys |
| --- | --- |
| Execution mode / SSH resource | `executionMode / sshResourceId` |
| Script engine / Python interpreter | `engine / interpreter` |
| Script content / Working directory / Environment variables | `script / workingDir / env` |
| Script encoding / Output encoding | `scriptCharset / outputCharset` |
