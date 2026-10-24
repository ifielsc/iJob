# Remote SSH script

[简体中文](../../zh-CN/examples/remote-script.md) · [Manual home](../README.md)

**Verification: configuration/workflow example; this round did not complete end-to-end acceptance of its external dependencies.**

Fields: [Script](../user-guide/plugins/script.md).

## Prepare, configure and run

Prepare an independent SSH host/account/work directory and verified host fingerprint. Test the SSH resource, then choose SSH, that resource and an engine installed remotely. Begin with `echo hello-remote` in the remote test directory.

Check executing node, remote output and exit code; test an unauthorized directory and missing interpreter separately. A Windows Worker can connect to Linux, but engine/paths must match the remote host. After stop, verify remote processes/effects; a closed connection is not transaction rollback. Clean only files created for this case and retain identity/log evidence.

## Finish

Retain inputs, definition versions, trigger/instance IDs, sanitized logs and target checks. Stop only processes/schedules owned by this case; other environments and historical evidence are not cleanup targets.
