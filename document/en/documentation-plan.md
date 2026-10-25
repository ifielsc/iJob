# Documentation structure and implementation scope (approved)

[简体中文](../zh-CN/documentation-plan.md) · [Complete configuration inventory](configuration-catalog.md) · [Current manual](README.md)

This page records the approved 1.0.0 structure, classifications and maintenance principles. Manuals follow this tree; the checker covers 54 settings and the full catalog retains 254. Screenshots use newly registered independent resources. GitHub remains private and the existing demo is unchanged.

## 1. Reading routes

| Reader goal | Recommended route | Completion criterion |
| --- | --- | --- |
| First installation | Installation choice → platform guide → quick start | Independently initialize, start, sign in, stop and start again |
| Daily business configuration | Quick start → user guide → relevant example | Configure, publish, execute, inspect results and handle common failures |
| Configure deployment nodes | Configuration → groups/roles/allowlists → capacity and operations | Know where to edit, whether to restart and how to verify scope |
| Manage accounts and deployment modes | Account operations / standalone-cluster operations | Follow separate change procedures without confusing the two mode dimensions |
| Modify source or prepare release | Development and release verification | Distinguish successful builds, passing tests and runtime acceptance |

## 2. Proposed directory

Chinese `document/zh-CN/` and English `document/en/` use identical relative paths. One tree is shown below. Filenames and code examples stay the same; titles, prose and screenshots use the corresponding language.

```text
README.md                         Navigation, reader routes and terminology
installation.md                   Entry: complete distribution / source build
installation/
  source-build.md                  Tooling, native/docker build, outputs and failures
  windows.md                      First and routine Windows lifecycle
  linux.md                        First and routine Linux lifecycle
  docker.md                       Linux Docker, offline images, recreation and status
quickstart.md                     First task/job, results and schedule preview
configuration.md                  Levels, location, precedence, activation and checks
configuration/
  common.md                       Network, HTTP, locale, logs and common settings
  routing.md                      Node name, roles, groups and execution targets
  plugins.md                      All 11 switches and Local JAR deployment settings
  scheduling-retention.md          Schedules, events, notifications and retention
  capacity.md                     Executors, in-flight runs, pools and shutdown waits
  deployment-agent.md              Startup variables, Compose and standalone Agent
  controlled.md                    Accounts/keys/database/mode procedures and links
  internals.md                     Internal coordination and framework appendix
user-guide/
  README.md                       User-guide entry and page map
  workspace.md                    Login, locale/theme/time zone, overview and nodes
  tasks.md                        Shared fields, versions, enabled state and execution
  plugins/README.md               Plugin selection and shared conventions
  plugins/script.md               Local/SSH scripts
  plugins/sql.md                  SQL
  plugins/api-call.md             API calls
  plugins/event.md                Events
  plugins/data-check.md           Data checks
  plugins/file-transfer.md        File transfer
  plugins/push.md                 Push
  plugins/etl.md                  ETL
  plugins/batch-etl.md            Batch ETL
  plugins/server-monitor.md       Server monitoring
  plugins/local-jar.md            Local JAR
  jobs.md                         Shared job configuration, publication and references
  job-nodes.md                    Nodes, edges, conditions, failure policies and gateways
  parameters.md                   Parameter templates, inputs and variable references
  schedules.md                    Configuration, preview, enable/disable and catch-up
  calendars.md                    Calendar rules and exceptions
  runs.md                         Triggers, instances, states, logs, topology, stop/retry
  resources.md                    Resource index and shared secret-field rules
  resources-database.md           Database resources
  resources-ssh.md                SSH resources and host fingerprints
  resources-service.md            Service resources and authentication
  resources-file.md               LOCAL/FTP/SFTP/S3 resources
  resources-messaging.md          Kafka/RabbitMQ resources
  resources-monitor.md            Monitored servers and Agent connections
  events.md                       Event approval and processing history
  notifications.md                Mail channels, notification rules, templates and history
  concurrency.md                  Business concurrency controls
  diagnostics.md                  Runtime diagnostics and health
  users-audits.md                 Users, permissions and audit
  import-export.md                Export, mappings, differences, import and recovery
examples.md                       Index: goals, dependencies, difficulty and verification
examples/
  script-lifecycle.md             Success, failure, timeout, stop and retry
  remote-script.md                SSH execution and worker-local paths
  api-and-parameters.md           HTTP, authentication, extraction and parameter passing
  sql-and-validation.md           SQL queries/writes and DATA_CHECK
  file-transfer.md                Local, SFTP, S3 and overwrite policies
  etl-full-incremental.md         Full/incremental ETL, checkpoints and result checks
  batch-etl.md                    Multi-table selection and mapping
  dag-control-flow.md             Serial, parallel, joins, branches and failures
  subjobs-and-versions.md         Child jobs and published version references
  schedules-and-calendars.md      Time zones, workdays, missed fires and catch-up
  events-and-approval.md          Event waits, approval and recovery
  push-and-notifications.md       Message push and run-result notifications
  monitoring.md                   Monitoring collection and abnormal results
  local-jar.md                    Trusted plugin deployment, invocation and results
  routing-and-concurrency.md      Node groups, allowlists and concurrency
  environment-transfer.md         Configuration transfer between isolated environments
operations.md                     Operations entry and change categories
operations/
  daily.md                        Daily checks, logs and maintenance windows
  accounts.md                     STATIC/DATABASE and daily account management
  account-mode-change.md          First admin, switching, passwords and failures
  standalone-cluster.md           Runtime modes, database requirements and roles
  runtime-mode-change.md          Standalone/cluster changes and rollback conditions
  backup-restore.md               SQLite/server database backups and independent restore
  upgrade-rollback.md             Package, configuration, data and version consistency
  production.md                   TLS, service management, monitoring, capacity and ingress
  troubleshooting.md              Symptoms, causes, evidence and recovery
development.md                    Development and release verification entry
development/
  quality-gates.md                What builds, tests and runtime acceptance prove
  documentation.md                Bilingual catalog, screenshots and link maintenance
  release-checklist.md            Provenance, checks and private-release boundaries
images/                           Real screenshots in this language, grouped by chapter
```

The scope references are configuration-catalog.md and this page. Configuration is split by the themes above while retaining a complete index and traceable entry IDs, instead of putting 254 entries on the everyday configuration landing page. The shared machine inventory `document/reference/configuration-review.json` stores bilingual definitions so configuration facts do not diverge.

## 3. Page structure and length rules

| Page type | Consistent structure | Images/diagrams |
| --- | --- | --- |
| Platform installation | Prerequisites → extraction/directories → first initialization → start/access → status → logs → stop → configuration restart → subsequent start → troubleshooting | Keep commands copyable; show terminal examples only where initial interaction needs explanation |
| Quick start | Prerequisites → create → publish → execute → inspect → two-node job → schedule preview → completion check | About 4–5 key real screenshots, not every click |
| Page reference | Purpose/permission → entry → common path → field groups → action effects → result interpretation → troubleshooting → examples | Complex forms/results; field tables include defaults, requiredness, conditional visibility and scope |
| Each plugin | Use cases → dependencies/execution location → fields → minimal configuration → outputs/variables → failures/retries → examples | Key settings/results where useful; copyable configuration stays outside images |
| Scenario example | Goal → prerequisites → sample data → configuration → execution → business-result checks → failures → finishing the exercise | Simple cases may need no image; complex cases generally use 1–3 focused images |
| Controlled procedure | Applicability → checks/backup → ordered steps → success criteria → failures → recovery/rollback | Small diagrams for relationships; actual UI screenshots for pages |
| Configuration reference | Key/type → source defaults → classification → example → dependencies → activation → verification | Prefer text; use small tables/diagrams for routing and scope |

Avoid repeated explanations: plugin fields live in the reference, examples link to them and supply concrete values; operations owns mode changes and configuration links to those procedures. Keep terms, commands, versions, enums and paths consistent.

## 4. Confirmed corrections for the body-writing stage

| ID | Current problem | Planned correction |
| --- | --- | --- |
| F01 | Relationship of build.py to pnpm check/-DskipTests is unclear | Explain that these are internal script steps; installers need no second build, while full gates serve developers/publishers |
| F02 | Windows command block suggests initialize must precede every start | Explain automatic initialization by start; initialize only configures without starting; separate first/routine use |
| F03 | Docker recreation instructions conflict with pinned-image entrypoint | Use the corresponding distribution legacy/start-docker.sh --recreate consistently; separate status/logs/stop |
| F04 | Quick start/examples say script stdout is not recorded, contrary to implementation/evidence | Correct output path, limits and UI location; demonstrate with real run screenshots |
| F05 | The 21-setting table omits routing, switches, retention durations, sessions and Agent | Present the complete classified inventory; explicitly state checker coverage rather than claiming future support |
| F06 | Multi-account, cluster and production ingress are compressed together | Separate authentication mode from runtime mode, each with prerequisites, steps, checks and failure handling |
| F07 | Examples summarize operations without concrete values/results | Supply sample input, actions, output and business checks using a consistent template |

## 5. Page-field and scenario coverage

During body writing, track “field/action → explanation → example → evidence” for every page. Shared task fields, all 11 plugins, jobs and detailed node configuration are first priorities. Each plugin must explain unsuitable execution locations, required resources, timeout/retry behavior and output limits in addition to the success path.

Jobs must explain publication-time version binding, parameter sources, execution conditions, failure policies, gateway default branches/priorities, child jobs and execution topology. The run center must distinguish trigger acceptance from execution, and explain the effects of saving, publishing, enabling, stopping the platform and stopping an instance. Button names alone are insufficient evidence of semantics.

Register and provision external databases, SSH, object storage, messaging and Agent resources independently for examples. Mark cases as “verified / configuration example only / pending verification”; prior SQLite rehearsal does not prove every external plugin. The 16 scenario groups may contain multiple variants; one screenshot cannot verify every variant.

## 6. Bilingual and screenshot plan

| Image group | Planned views | Data and acceptance requirements |
| --- | --- | --- |
| Q01–Q05 Quick start | SCRIPT configuration, successful instance/log, two-node editor, run topology, schedule preview | Same real sample set; retain execution/log evidence |
| U Page reference | Complex plugin forms, job nodes/gateways, parameters, schedules, resources and accounts | One focus per image, with action location and expected effect |
| E Scenarios | ETL mapping/results, selected branch, import differences, approval or notification preview | Match real steps; previews are not sends and simulated responses are not real service results |

- Register a new screenshot environment, accounts, data paths, ports and external services before configuring/running examples. Do not use the existing demo for capture or testing.
- Capture Chinese and English by switching the real UI, using identical synthetic data and version. Use consistent light theme/dimensions; show dark mode only when explaining themes.
- Run explicitly required verification scenarios first, then capture results. Do not send extra notifications, repeat tasks or operate real business solely for an attractive screenshot.
- Exclude passwords, tokens, complete private configuration and real business data. Never fabricate success states; put numbered explanations in captions and preserve actual UI text.
- Track language, page, step, source version and evidence for each image. Commands/scripts/SQL/configuration always remain copyable text with alternative descriptions.
- Translate each reviewable Chinese unit promptly, rather than waiting for all Chinese prose. Match structure, keys, defaults, enums, sample data, images and links.

## 7. Approved decisions and acceptance

Use Markdown without a documentation-site stack. A/B are common/advanced, C uses controlled workflows and D remains an appendix. Prioritize installation/configuration/operations, then page/plugin/scenario references, followed by bilingual, real-image and bundle validation.

See [Scenarios](examples.md) for external-protocol/extension verification scope and [Release checklist](development/release-checklist.md) for acceptance and follow-up priorities. Screenshots do not replace end-to-end evidence for each protocol.
