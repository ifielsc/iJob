# Runtime requirements and compatibility

[简体中文](../../zh-CN/installation/requirements.md) · [Manual home](../README.md)

Before choosing an installation, check the OS, runtime, platform database and task dependencies. This page applies to iJob 1.0.0 and distinguishes requirements from known reference combinations. A formal bundle's manifest determines its exact scope.

## Operating systems and runtimes

| Area | Requirement | Specific reference | Boundary |
| --- | --- | --- | --- |
| Native Windows | x64, Java 21, PowerShell launcher | Windows Server 2022 build 20348; Oracle JDK 21.0.10 x64 | Server Core service management and desktop business combinations have separate coverage; Windows 10/11, other Server versions and ARM64 are not confirmed here |
| Native Linux | Java 21, Bash, util-linux flock | Ubuntu 24.04 amd64 | Not a claim for every distribution; systemd management requires systemd |
| Linux Docker | Linux containers, Bash, Docker Engine and the `docker compose` plugin | Identified software inputs: Engine/CLI 29.9.1, Compose plugin 5.6.0, Ubuntu 24.04 amd64 | Identified software versions do not establish every business combination; not a Windows Docker Desktop support claim |
| Java | Major runtime requirement 21 | Current Linux image build pins Temurin 21.0.13+9; Windows reference uses 21.0.10 | A compatibility sample is not an ongoing security-patch recommendation; inspect the actual bundle/runtime |
| Task interpreters | Available on the execution machine | Windows BAT/PowerShell, Linux SHELL, configured Python | Default containers do not include every interpreter; SSH execution supports SHELL/PYTHON only |

The Compose plugin means the `docker compose` command, not a requirement for a particular 2.x version. Compare `java -version`, `docker version` and `docker compose version` with the bundle instructions. The browser machine does not need task interpreters.

## Five platform databases

Deployment modes describe product connectivity and coordination capabilities. Version references do not establish arbitrary older/newer version compatibility. Platform databases store definitions, history and coordination; plugins configure business databases separately.

| Platform database | Version reference | Standalone | CLUSTER | Level and boundary |
| --- | --- | --- | --- | --- |
| SQLite | 3.49.1, JDBC 3.49.1.0 | Yes | No | Embedded single-instance platform storage, not a selectable SQLite business DB resource |
| MySQL | Windows 8.0.35; Linux 8.4.11 Community | Yes | Yes | Server database; Windows single-node and Linux two-node coverage are separate combinations |
| PostgreSQL | 18.6 | Yes | Yes | Server database; check schema, permissions and connectivity |
| SQL Server | Windows 2022 Developer RTM 16.00.1000; Linux 2022 Developer 16.0.4305.5 | Yes | Yes | Developer is the compatibility sample; choose production edition/licensing separately and satisfy collation requirements |
| Oracle | AI Database 26ai Free 23.26.3.0.0 | Yes | Yes | Free is the reference edition; this does not establish support for older versions such as 11g |

Cluster nodes must share one logical platform database/schema. Do not mix STANDALONE and CLUSTER against that schema. Oracle platform schemas require applicable `SYS.DBMS_LOCK` execute access; do not grant DBA as a substitute for minimal permissions. See [platform databases](../configuration/platform-database.md).

## Browsers

| Browser | Current scope |
| --- | --- |
| Chromium | Engine configured for product browser automation; a release should identify its browser version |
| Chrome / Edge | No independently stated minimum branded-browser version or complete range; Chromium coverage is not proof for every version |
| Firefox / Safari | Not currently included in confirmed product browser coverage |
| Mobile browsers | The documentation site adapts to narrow screens; this does not certify all mobile product operations |

## Node topology

| Scenario | Role arrangement | Considerations |
| --- | --- | --- |
| Learning / light workloads | API, SCHEDULER, WORKER and MAINTENANCE together | Default arrangement reduces deployment complexity |
| Isolate execution load | Separate Workers; combine other roles according to load | Prepare scripts, files, Local JARs and resource access on each Worker |
| Multiple nodes | Cover all four roles and share the logical platform database | Independent node identities, homes and logs; compatible configuration |
| Physical high availability | Multiple failure domains, redundant roles and database availability | Multiple processes on one host do not survive host failure |

See [roles and clusters](../operations/standalone-cluster.md). API handles management/trigger requests; SCHEDULER scans plans; WORKER executes; MAINTENANCE performs background maintenance.

## CPU, memory, disk and connection budgets

There is no measured universal hardware minimum or production-throughput promise. An existing Windows reference environment uses **4 vCPU, 8 GiB RAM and 96 GiB disk**. This is an environment specification, not a minimum. Task types, concurrency, databases and retention change requirements.

| Item | Sizing approach |
| --- | --- |
| CPU | Account for platform threads and task subprocesses; measure representative workloads before selecting concurrency |
| Memory | JVM heap/native memory + concurrent script peaks + ETL buffers + OS/containers/databases + headroom |
| Disk | Bundles/retained upgrades + platform data/indexes + daily logs×retention + temporary-file peaks + backups |
| Connections | Sum of platform node pool limits + business resource connections + monitoring/operations headroom |
| Default connection/execution settings | Platform pool limit 4, Worker inflight roots 20, scheduler threads 8; distinct limits, not a promise of 20 heavy simultaneous tasks |
| Docker default limits | Generated backend limit 1 GiB / 2 CPUs, web memory limit 128 MiB; container limits are not host minimums |

## Ports and logs

| Item | Default / planning |
| --- | --- |
| Native HTTP / Docker host entry | 18080; use separate ports for co-hosted instances |
| Docker internal web / backend | 8080; use the Compose network configuration |
| Monitor Agent container | 8081; configure the actual monitoring resource address |
| Native application logs | 10 MB per file, 14-day history, 200 MB archived total; the cap may remove files earlier |
| Container logs | 10 MB × 5 rotated files per service |
| Platform business history | Separate from application logs; retention cleanup is disabled and dry-run=true by default, so nominal retention periods do not cause automatic deletion |

Databases, SSH, HTTP, MQ and file services use their actual ports. Only allow connections required by the node's responsibilities.

[Installation](../installation.md) · [Capacity settings](../configuration/capacity.md) · [Retention](../configuration/scheduling-retention.md) · [Production deployment](../operations/production.md)
