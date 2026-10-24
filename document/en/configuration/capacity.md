# Capacity, concurrency and shutdown

[简体中文](../../zh-CN/configuration/capacity.md) · [Manual home](../README.md)

Executor threads, queues, in-flight root runs, database connections and business concurrency policies impose different limits. Raising one does not raise the others. `app.scheduling.pool-size` controls internal periodic scans, not total business-task concurrency.

Measure CPU, memory, connections, queue time and external-system limits first. Change one bound gradually and run representative work. Diagnose slow SQL/remote timeouts before adding threads. A larger connection pool does not make SQLite a cluster database.

Coordinate executor shutdown, Spring shutdown phases and Docker stop_grace_period/service-manager limits so graceful shutdown can finish before forced termination. Longer waits cannot undo requests already sent externally. See [Business concurrency](../user-guide/concurrency.md) for resource exclusion.

## Settings in this topic

These are source/implementation defaults; see the [complete catalog](../configuration-catalog.md) for setup overrides and provenance.

| ID / key | Level / type | Source defaults | Explanation |
| --- | --- | --- | --- |
| C010 / `spring.lifecycle.timeout-per-shutdown-phase` | B / duration | 30s | Wait per Spring shutdown phase; verify with executor and container limits. |
| C023 / `app.scheduling.pool-size` | B / integer | ${IJOB_SCHEDULING_POOL_SIZE:8} | Internal scheduled-scanner threads; not the maximum business-task concurrency. |
| C024 / `app.scheduling.shutdown-timeout` | B / duration | ${IJOB_SCHEDULING_SHUTDOWN_TIMEOUT:30s} | Shutdown wait for scheduled-scanner executor. |
| C049 / `app.runtime.work-queue.max-inflight-roots` | B / integer | ${IJOB_WORK_QUEUE_MAX_INFLIGHT_ROOTS:20} | In-flight root-run limit per node; size with executors, concurrency controls and capacity. |
| C104 / `app.execution.shutdown-timeout` | B / duration | ${IJOB_EXECUTION_SHUTDOWN_TIMEOUT:30s} | Executor shutdown wait; coordinate with container grace and Spring shutdown limits. |
| C107 / `app.execution.trigger.queue-capacity` | B / integer | ${IJOB_TRIGGER_POOL_QUEUE_CAPACITY:0} | trigger executor Queue capacity; size jointly, distinct from business concurrency groups. |
| C111 / `app.execution.job.queue-capacity` | B / integer | ${IJOB_JOB_POOL_QUEUE_CAPACITY:0} | job executor Queue capacity; size jointly, distinct from business concurrency groups. |
| C115 / `app.execution.dag.queue-capacity` | B / integer | ${IJOB_DAG_POOL_QUEUE_CAPACITY:500} | dag executor Queue capacity; size jointly, distinct from business concurrency groups. |
| C119 / `app.execution.task.queue-capacity` | B / integer | ${IJOB_TASK_POOL_QUEUE_CAPACITY:1000} | task executor Queue capacity; size jointly, distinct from business concurrency groups. |
| C123 / `app.execution.script.queue-capacity` | B / integer | ${IJOB_SCRIPT_POOL_QUEUE_CAPACITY:0} | script executor Queue capacity; size jointly, distinct from business concurrency groups. |
| C171 / `spring.datasource.hikari.maximum-pool-size` | B / integer | ${IJOB_SQLITE_POOL_SIZE:4} | Pool limit; coordinate with database capacity. More SQLite connections do not resolve write contention. |
