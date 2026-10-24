# Quick start: your first task and job

[简体中文](../zh-CN/quickstart.md) · [Manual home](README.md)

Prerequisite: complete installation, confirm READY, and sign in with your own initialized administrator. Use an isolated practice installation. Language, theme and display timezone are available in the page header; defaults are sufficient to begin.

## 1. Create a task without external dependencies

1. Open Task management, choose New task and name it hello-ijob.
2. Select SCRIPT and Local Worker. Choose BAT on Windows, or SHELL on Linux/the official Docker backend (using /bin/sh inside that host/container).
3. Enter the two lines below. Leave working directory, environment variables and execution groups empty. Keep retries at 0 and set timeout to 30 seconds.
4. Keep the task enabled and save. Saving publishes a version; it does not run the script.

```text
echo hello-ijob
exit 0
```

The default image does not include Python, PowerShell or business commands. The browser's computer is not necessarily the Worker. Do not select BAT on Linux or confuse local paths with remote paths.

## 2. Run and inspect

1. Click Run in the task list. Check task, engine and execution location before confirming once.
2. Find the new instance through Run history or the Run center and note its instance ID.
3. Wait for SUCCESS. Inspect start/end times, executing node and instance logs. Script lifecycle logs should show execution started and exitCode=0.

Script stdout/stderr is not automatically included in lifecycle logs. Missing hello-ijob output does not mean execution failed. If network acceptance is uncertain, inspect history by target and time before submitting again.

Change the script to exit 7, save a new version and run it to observe FAILED with a nonzero exit code. Restore exit 0 afterward. This controlled failure exercise requires no external database or real messages.

## 3. Create a two-node job

Create a second task named hello-finish using the same engine/script. In Job orchestration, create hello-flow, add both task nodes and drag a connection from the first node to the second. Keep the second node's all-upstream-success execution condition and stop-on-failure policy.

Apply Auto layout, Validate and Save and publish. Enable and run the job, then inspect its topology in the Run center. Both nodes should succeed in dependency order. Editing a task creates a new version; an already-published job keeps its published references. Review and republish the job to use newer task versions.

The layout direction applies when Auto layout is clicked; connection style changes immediately and does not change execution. The help icon supports hover, keyboard focus and touch.

## 4. Preview a schedule

Create a schedule for hello-flow, select CRON and enter 0 */5 * * * * (every five minutes). Explicitly choose the business timezone. Keep it disabled while using Preview to inspect candidate times. Preview does not execute the job.

Enable only when automatic execution is intended. The job, global schedule scanner and a SCHEDULER node must also be available. Disable the practice schedule when finished. See examples for calendars and missed-trigger behavior.

## 5. Completion checklist

You should now be able to publish a version, submit one manual execution, inspect its logs, distinguish success/failure, publish and observe a DAG, and preview/disable a schedule. Deployment scripts stop the platform; the Run center stops a running task. These are different operations.
