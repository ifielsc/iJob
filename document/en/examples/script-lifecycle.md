# Script success, failure, stop and retry

[简体中文](../../zh-CN/examples/script-lifecycle.md) · [Manual home](../README.md)

**Verification: the baseline path was exercised in isolation; extensions do not inherit that result.**

Fields: [Script](../user-guide/plugins/script.md).

## Prepare, configure and run

Use an independent Worker with BAT on Windows or SHELL on Linux. Save docs-hello with 0 retries and a 60-second timeout:

```sh
echo hello-ijob
exit 0
```

Run manually and verify SUCCESS, executing node and STDOUT. Copy to docs-failure, replace exit 0 with exit 7 and expect FAILED. HTTP trigger acceptance is not script success. For a long task use `ping -n 31 127.0.0.1 > nul` on Windows or `sleep 30` on Linux, request stop while running and wait for STOPPED.

Extensions: use a separate copy with a 2-second timeout, then one retry and count attempts. Verify these variants separately on the target node; success/stop screenshots do not prove them. Disable practice schedules afterward and retain failed instances for diagnosis.

## Finish

Retain inputs, definition versions, trigger/instance IDs, sanitized logs and target checks. Stop only processes/schedules owned by this case; other environments and historical evidence are not cleanup targets.

## Real page examples

Filtered real output for the successful path; failure and stop have separate instance records.

![Filtered real output for the successful path; failure and stop have separate instance records.](../images/manual/script-run.png)
