# 容量、并发与关闭时限

[English](../../en/configuration/capacity.md) · [手册首页](../README.md)

线程池、队列、在途根运行数、数据库连接池和业务并发策略是不同限制；增加其中一项不会自动扩大其他项。`app.scheduling.pool-size` 是内部周期扫描线程，不是业务任务总并发。

先记录 CPU、内存、连接数、排队时间和外部系统限制，再小幅调整单项并运行代表性样例。慢 SQL/远端超时应先定位，不靠无限加线程解决。SQLite 不通过扩池变成集群数据库。

关闭时间需协调执行器、Spring 关闭阶段与 Docker stop_grace_period/服务管理器超时；容器被强杀前应留出应用优雅停止时间。设置更长等待也不能撤销已经发送的外部请求。业务资源互斥另见[并发控制](../user-guide/concurrency.md)。

## 本主题配置索引

下列为源码/实现默认值；初始化覆盖、来源和完整说明见[完整清单](../configuration-catalog.md)。

| ID / 配置键 | 级别 / 类型 | 默认值来源 | 说明 |
| --- | --- | --- | --- |
| C010 / `spring.lifecycle.timeout-per-shutdown-phase` | B / duration | 30s | 每个 Spring 关闭阶段的等待时间，与执行器和容器时限共同验证。 |
| C023 / `app.scheduling.pool-size` | B / integer | ${IJOB_SCHEDULING_POOL_SIZE:8} | 内部周期扫描线程数量；不是业务任务最大并发数。 |
| C024 / `app.scheduling.shutdown-timeout` | B / duration | ${IJOB_SCHEDULING_SHUTDOWN_TIMEOUT:30s} | 周期扫描执行器关闭等待。 |
| C049 / `app.runtime.work-queue.max-inflight-roots` | B / integer | ${IJOB_WORK_QUEUE_MAX_INFLIGHT_ROOTS:20} | 节点在途根运行上限；结合线程池、任务并发和资源容量评估。 |
| C104 / `app.execution.shutdown-timeout` | B / duration | ${IJOB_EXECUTION_SHUTDOWN_TIMEOUT:30s} | 执行器关闭等待；与容器停止宽限和 Spring 关闭时限一起规划。 |
| C107 / `app.execution.trigger.queue-capacity` | B / integer | ${IJOB_TRIGGER_POOL_QUEUE_CAPACITY:0} | trigger 执行池队列容量；需联合评估，不等于业务并发组限制。 |
| C111 / `app.execution.job.queue-capacity` | B / integer | ${IJOB_JOB_POOL_QUEUE_CAPACITY:0} | job 执行池队列容量；需联合评估，不等于业务并发组限制。 |
| C115 / `app.execution.dag.queue-capacity` | B / integer | ${IJOB_DAG_POOL_QUEUE_CAPACITY:500} | dag 执行池队列容量；需联合评估，不等于业务并发组限制。 |
| C119 / `app.execution.task.queue-capacity` | B / integer | ${IJOB_TASK_POOL_QUEUE_CAPACITY:1000} | task 执行池队列容量；需联合评估，不等于业务并发组限制。 |
| C123 / `app.execution.script.queue-capacity` | B / integer | ${IJOB_SCRIPT_POOL_QUEUE_CAPACITY:0} | script 执行池队列容量；需联合评估，不等于业务并发组限制。 |
| C171 / `spring.datasource.hikari.maximum-pool-size` | B / integer | ${IJOB_SQLITE_POOL_SIZE:4} | 连接池上限；结合数据库连接预算，SQLite 不靠增大连接池解决写竞争。 |
