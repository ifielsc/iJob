# Worked examples

[简体中文](../zh-CN/examples.md) · [Manual home](README.md)

Complete the quick start in an isolated practice installation first. Use dedicated accounts, directories, databases and mock endpoints. Enter credentials in resource secret fields, not scripts, environment-variable tables or reports. Importing a task does not install its external dependencies.

## A. Script success, failure and stopping

Create the SCRIPT task from the quick start: BAT on Windows, SHELL on Linux/Docker. echo hello-ijob followed by exit 0 should produce SUCCESS; publish exit 7 and run it to observe FAILED. Keep retries at 0 to inspect one execution.

For a stop exercise, use sleep 30 on Linux, or the PowerShell engine with Start-Sleep -Seconds 30 on a Worker with PowerShell installed. Stop the specific instance in the Run center and wait for a terminal state. Closing the browser does not stop execution. Inspect the node, stop record and final state. Restore the normal script and republish afterward. Lifecycle logs record exit status, not arbitrary script stdout.

## B. HTTP requests and extraction

Create a Service resource pointing to a practice HTTP endpoint with its actual authentication mode. Create an API_CALL task using that resource, HTTP, GET and the practice path /orders/count. Leave the body empty. Have the fixture return HTTP 200 and {"count":2}.

Save, run once and inspect state/results. Then add an extraction rule for count using the expression syntax supported by the form and its validation. A job can consume a direct upstream result. Do not represent mock responses as business results or test retry behavior with a side-effecting POST. In Docker, localhost means the executing container, not the host/browser computer.

## C. SQL preview and file browsing

Prepare an isolated practice database. Configure its driver, connection and account in Database resources and test the connection. Create a SQL task selecting that resource and enter SELECT 1 AS sample_value. Preview the result, save, run manually and inspect execution state. Preview permits constrained read-only queries; it does not execute DDL/DML or prove a write task will succeed.

For file resources, use a dedicated test root and the browser to choose a file. LOCAL paths belong to the current node; browsing LOCAL drafts requires app.resource-browser.local-roots. FTP/SFTP/S3 require their own service/accounts; verify an SFTP host fingerprint on first connection. Transfer a small text file between separate source/destination directories, review overwrite policy, run and compare destination contents. Never use platform database or key directories as transfer sources.

## D. ETL and batch table selection

Prepare sample_orders(id, amount), two sample rows and a target table in independent practice databases. Define primary keys and types. Create source/target database resources, then an ETL task with table/query input, column mappings and the intended write mode. Disable retries initially, run a small dataset and compare row counts, keys and amount values. SUCCESS alone is not a data-integrity check.

For incremental mode, define the incremental column, starting watermark and boundaries, then inspect checkpoints/progress. Old checkpoints do not prove a changed configuration ran. Do not delete production checkpoints to force replay.

For BATCH_ETL, open table selection, search and choose two practice tables, review source/target mappings and counts, then save and run. Recheck the list after switching resources. Validate existing-target and create/overwrite behavior in the sample databases first.

## E. Branches, child jobs and calendars

Extend hello-flow with a gateway and two downstream tasks. Give branches distinct keys/priorities and exactly one default branch. Use an input such as the boolean approved for a non-default condition. Choose variable, operator and value in the condition editor rather than inventing condition JSON. Run with inputs that exercise both the matching branch and fallback, then inspect topology states.

Publish a child job independently before inserting it into the parent and publishing the parent. Publishing a parent refreshes current references; historical run topology reflects the references used then, not future edits. Layout and connection styles affect presentation only.

For business-day execution, configure allowed/exception dates in Calendars, attach the calendar to a schedule and preview excluded dates. Check timezone, validity period and missed-trigger policy together. Catch-up produces real runs; do not experiment on production schedules.

## F. Export and import between environments

Select practice tasks/jobs and export them, optionally including non-secret resource configuration and schedule/notification references. Prepare target resources and credentials first. Upload, inspect, map dependencies, choose create/reuse/update, review differences and confirm import.

New tasks/jobs and schedules are disabled by default. Reuse retains the target's enabled state; confirm that it is the correct target object. Review resources, versions, parameters and schedules before manually enabling imported objects. For partial completion, inspect the same import record and resume after correcting the cause. Do not repeatedly create new plans after network errors. HTML/JSON reports describe configuration transfer, not connectivity or successful business execution. Keep CLI connection files in a restricted directory and passwords off command lines.
