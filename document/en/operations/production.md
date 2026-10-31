# Production ingress and service management

[简体中文](../../zh-CN/operations/production.md) · [Manual home](../README.md)

The bundle provides basic runtime capability. Production still needs TLS, ingress access control, a restricted service account, backup/restore, monitoring and capacity planning in the target environment. Configure upload sizes, SSE/long-connection timeouts and readiness-based traffic removal in the proxy; do not expose detailed management health publicly by default.

Ordinary `configure` / `start` commands do not install operating-system services. For Windows service or systemd management, complete first initialization and stop the instance, then explicitly run `service install` as described in [Service management](../installation/services.md). Run `service enable` if boot startup is required. The service uses its registered Java, program directory, IJOB_HOME and execution account. Check normal shutdown, failure recovery and application readiness, and avoid a second process manager starting the same instance.

Docker needs memory/CPU/log limits, non-root runtime identity and explicit mounts. Default images do not contain every business interpreter/dependency; provide trusted tools as needed. Do not add privileged mode or docker.sock to supply task capabilities. Plan local files and Local JAR artifacts for each Worker.

Use representative workloads to measure CPU, memory, database connections and log growth before selecting limits and retention periods. Before deployment, review the bundle manifest, third-party license notices and applicable security advisories. Recheck these conditions after upgrades or dependency changes.