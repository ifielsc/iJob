# Transfer configuration to another environment

[简体中文](../../zh-CN/examples/environment-transfer.md) · [Manual home](../README.md)

Import a harmless task into a separate target and inspect disabled state, mappings and execution.

## Audience and outcome

For workflow authors who have completed the quick start.

## Before you start

Read the [shared tutorial preparation](../examples.md). Estimates exclude external-service installation. Use your own tutorial resources, paths and ports.

| Resource | Requirement |
| --- | --- |
| Source A | One working harmless SCRIPT and ADMIN export permission. |
| Target B | A separate installation/database/accounts/keys/ports/resources with ADMIN import permission. |

## Configuration

| Field / setting | Example value |
| --- | --- |
| Initial export scope | One SCRIPT without schedule or notification bindings |
| Package | Format-1 .ijob.zip; credentials are excluded |

## Steps

### 1. Export one task

Select the harmless SCRIPT in A and export it. Check expected objects/dependencies and save the .ijob.zip.

### 2. Inspect and map in B

Sign in to B and upload the package. Review object list and diff first. Resource-bearing packages require mappings to B's independent resources and B's secrets, not source addresses/accounts reused as target setup.

### 3. Apply and inspect disabled defaults

Apply the reviewed plan. The initial creation action should be CREATE and the import record should finish as COMPLETED. The new task is disabled and has not run. Check definitions/references, then verify the import; expect VERIFIED. New or updated tasks/jobs and newly created schedules are disabled by default. Distinguish plan READY, import COMPLETED, verification VERIFIED, and business execution SUCCESS.

### 4. Inspect a repeat import after restart

Leave the imported object unchanged and disabled. After restarting B normally, upload and inspect the same package. Expect plan READY, task action REUSE, step status UNCHANGED, and the same target object ID as before. Applying it produces COMPLETED with the step DONE and action still REUSE, without another same-name object. Review the plan before applying again.

### 5. Enable and run one sample

Enable B’s sample SCRIPT and save a new version, then execute it manually. Expect SUCCESS and the same `hello-ijob` message as in A. The intentional change can make verification of the original plan DRIFTED, indicating a baseline difference rather than execution failure. Import COMPLETED does not replace this run result.

### 6. Extension: resource mapping and parent/child jobs

Create separate PostgreSQL business databases and resources in A and B, never using their platform databases. Enter each environment’s own connection and credentials. Use three rows in A and two in B to identify the database actually queried:

```sql
-- A's dedicated business database
CREATE TABLE orders (id integer PRIMARY KEY);
INSERT INTO orders (id) VALUES (1),(2),(3);
```

```sql
-- B's separate business database
CREATE TABLE orders (id integer PRIMARY KEY);
INSERT INTO orders (id) VALUES (1),(2);
```

In A, create an SQL task using resource A, query `SELECT COUNT(*) AS total FROM orders`, and result variable `mappedRows`. Create a single-node child job referencing the task and a single-node parent referencing the child, with ALL_SUCCESS / STOP. Publish the task, then child, then parent. A direct task run should return numeric `mappedRows=3`.

Export the parent from A, including its task, child, and resource configuration but no credentials. Upload it in B. A first-seen resource with neither a target mapping nor credentials should block the plan. If a mapping for that source resource was saved previously, automatic reuse may produce READY; an empty manual selection does not always mean BLOCKED.

Explicitly choose REUSE for the resource and select B’s enabled resource. Check that the plan is READY, then apply and expect COMPLETED. Open the imported SQL task and confirm that its actual resource reference points to B. Placeholder references in an import preview are not final object IDs.

Imported task, child, and parent are disabled by default. **Enable and save/publish in order: SQL task → child job → parent job**, adopting each newly published enabled version. Do not publish the parent first and pin disabled child versions. If groups do not match B’s Workers, adjust them before publishing up the hierarchy.

Execute B’s parent. Parent, child, and SQL nodes should all reach SUCCESS and return numeric `mappedRows=2`. A retains three rows and B two: the difference shows that the run used B’s resource, while import copied no business data from A.

## Expected results

| Check | Expected value |
| --- | --- |
| Initial import | New objects disabled; verification VERIFIED |
| Repeat import of unchanged objects | Plan REUSE / UNCHANGED; apply COMPLETED; target ID unchanged |
| First-seen unmapped resource without credentials | Plan BLOCKED; an existing persisted mapping may enable automatic reuse |
| Explicit reuse of resource B | Plan READY, import COMPLETED, actual SQL resource reference points to B |
| After enabling/publishing from leaf to parent | Parent/child SUCCESS, `mappedRows=2`; A keeps 3 rows and B 2 |
| Single task after deliberate enablement | SUCCESS with `hello-ijob`; baseline can show DRIFTED |

## Common failures

| Symptom | Action |
| --- | --- |
| Partial failure | Inspect your import record and completed objects before continuing to avoid duplicate creation. |

## Safety and cleanup

Disable practice schedules in B and remove imported examples in dependency order; leave A unchanged. Packages are not database-history or secret backups.

## Version and limitations

Applies to iJob 1.0.0, package format 1. The example covers Windows SCRIPT transfer between two independent installations, disabled defaults, reuse after a target restart, PostgreSQL resource mapping, and leaf-to-parent publication, with corresponding results in both languages. Concurrent import conflicts, partial-failure resume, and other resource protocols are outside the confirmed scope. A package is not a backup of business data, historical databases, or secrets.

## Related pages

- [Configuration export, mapping and import](../user-guide/import-export.md)

- [Backup and independent restore](../operations/backup-restore.md)
