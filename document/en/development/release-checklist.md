# Candidate delivery and follow-up

[简体中文](../../zh-CN/development/release-checklist.md) · [Manual home](../README.md)

## Candidate checks

- Trace source, manifest, hashes, docs and screenshot provenance; preserve old deliveries in separate directories.
- Pass bilingual pairing/links/tables, repository allowlist and license scans.
- Report actual backend/Agent, frontend and distribution-tool passes, expected skips and unexecuted checks.
- Bind install/lifecycle/config/sample/recovery evidence to each platform/version; do not generalize one platform's result.
- Stop only services owned by this exercise, preserving the demo and historical data.

## Existing baseline and evidence boundaries

The 2026-10-25 source baseline is 6f452e8; launcher layout candidates were built from 8f624a1, not rebuilt from the latest commit. Independent 2026-10-20 evidence already covers five databases, four server-database dual-node configurations, historical migrations/offline conversion, selected recovery and real Kafka/RabbitMQ. Select revalidation by changes; do not label these as never tested. Historical source and screenshots retain their provenance and do not establish new-scenario acceptance.

## Approved implementation order

| Stage | Deliverable | Acceptance |
| --- | --- | --- |
| 0 | Coverage matrix and CI selection | Automatic lightweight checks; manual module/complete Basic; local selection and unexecuted scope recorded |
| 1 | Bilingual terminal configuration | Preserve scripting; path/mode/language preview, cancellation, validation and update backups; no database initialization or secret reset |
| 2 | Windows services and systemd | Foreground lifecycle and service-host provenance/license; separate identities, restricted permissions, lifecycle/logs/autostart controls, data-preserving uninstall; two instances and graceful/abnormal shutdown |
| 3 | Docker recovery | Isolated VM reboot validates daemon/Compose ordering, manual-stop semantics and persistence without duplicate supervision |
| 4 | All plugins and external protocols | Complete matrix below, reconcile external results, preserve success/failure/timeout/stop/retry/permission evidence |
| 5 | Composite recovery | Watermarks/ties, SCD2, partial commits, DAG/subjobs/versions, catch-up, approvals, routing/concurrency, two-environment imports |
| 6 | Operations | Service upgrades/rollback, independent restore and RPO/RTO, logs, TLS/proxy/paths, capacity and node failures |
| 7 | New candidate | Rebuild actual source, bind versions/provenance/hashes, clean install/upgrade/rollback, complete candidate gates |

Register environments before provisioning. Test autostart in new isolated Windows/Linux VMs; never reboot the maintenance computer. Send new messages only to this exercise's receivers. Update both manuals and separately captured real screenshots at each stage. Complete Basic is not the seven real-backend Full configurations; final candidates require both and the applicable runtime matrix.

## Plugin coverage matrix

Status describes current user-facing examples; automated and historical integration results have separate evidence. Each environment report records environment, commit, run ID, expected/actual result, evidence and cleanup outcome. Unexecuted cases remain pending; screenshots cannot manufacture execution evidence.

| Plugin | Current example baseline | Remaining acceptance |
| --- | --- | --- |
| SCRIPT | Local success/failure/stop exercised | SSH, timeout, retries, permission/fingerprint errors, remote cancellation |
| SQL | Basic PostgreSQL query exercised | Supported sources, reads/writes/transactions, failure/timeout/permissions and results |
| API_CALL | Operational example | Isolated HTTP receiver, parameters/extraction, business errors/timeouts/idempotent retries |
| EVENT | Operational example | Matching/duplicate/unmatched events, lookback/timeouts, approval authorization/expiry, file stability and recovery |
| DATA_CHECK | Basic scalar exercised | Counts/thresholds/failure gates, applicable sources and recovery |
| FILE_TRANSFER | Basic LOCAL exercised | FTP/SFTP/S3, overwrite/archive, hashes, permission errors and interruption |
| PUSH | Operational example | HTTP, Kafka/RabbitMQ and other supported targets; separate business push from runtime notifications; isolated mail receiver |
| ETL | Full DATABASE→DATABASE exercised | Supported sources/targets, files/messages, incremental/tied watermarks, SCD2 and checkpoint recovery |
| BATCH_ETL | Operational example | Table selection/mapping/naming, idempotency, partial commits and recovery |
| SERVER_MONITOR | Operational example | Agent authentication, collection/unreachable, threshold trigger/recovery and notification receipt |
| LOCAL_JAR | Operational example | Build/upload/execute, metadata/hash rejection, timeout/stop and multi-Worker deployment |

## Sixteen example acceptance items

| Example | Baseline | Remaining acceptance |
| --- | --- | --- |
| Script lifecycle | Basic exercised | Timeout, retries and target-node differences |
| Remote SSH script | End-to-end pending | Identity/fingerprint, output, errors and cancellation |
| HTTP/parameters | End-to-end pending | Parameter snapshots, response extraction, errors and idempotency |
| SQL/quality gates | Basic exercised | Writes, failure gates and resource isolation |
| File transfer | LOCAL exercised | Remote protocols, overwrite/archive and interruption |
| Full/incremental ETL | Full exercised | Watermarks/ties, messages/files and SCD2 |
| Multi-table ETL | End-to-end pending | Partial commits, recovery and target naming |
| DAG control flow | Basic sequence exercised | Parallel/join/exclusive/failure policies |
| Subjobs/versions | End-to-end pending | Published references, version changes and execution snapshots |
| Timezones/calendars | Preview only | Automatic triggers, exclusions and missed-run catch-up |
| Events/approvals | End-to-end pending | Authorization, expiry and wait recovery |
| Messages/notifications | End-to-end pending | Actual receipt, template language/timezone and retries |
| Monitoring | End-to-end pending | Normal, threshold trigger/recovery and unreachable |
| Local JAR | End-to-end pending | Trusted artifacts, rejection paths and multiple nodes |
| Routing/concurrency | End-to-end pending | Groups, task/workflow allowlists and shared limits |
| Environment transfer | End-to-end pending | Independent A/B mapping, secret completion, disabled defaults and partial failure recovery |

## Conditions before publication (no automatic publication)

Reconcile current artifacts with vdirs license-text provenance, combined MySQL/Oracle driver redistribution conditions, nested-library/frontend/image-OS/toolchain SBOM and NOTICE, and vulnerability applicability/remediation. Check latest evidence before closing historical findings. Capacity claims bind hardware/workload; same-host dual nodes do not prove physical high availability, and external writes have no exactly-once guarantee.

GitHub and candidates remain private. This checklist does not publish releases/images or change repository visibility.
