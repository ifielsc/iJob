# Production ingress and service management

[简体中文](../../zh-CN/operations/production.md) · [Manual home](../README.md)

The bundle provides basic runtime capability. Production still needs TLS, ingress access control, a restricted service account, backup/restore, monitoring and capacity planning in the target environment. Configure upload sizes, SSE/long-connection timeouts and readiness-based traffic removal in the proxy; do not expose detailed management health publicly by default.

Native launchers manage processes but do not install Windows services or systemd units. For automatic startup, explicitly configure the same Java, program directory, IJOB_HOME, service account, environment and shutdown timeout in a service manager. Exercise clean stops and crash restarts, avoiding duplicate management/startup.

Docker needs memory/CPU/log limits, non-root runtime identity and explicit mounts. Default images do not contain every business interpreter/dependency; provide trusted tools as needed. Do not add privileged mode or docker.sock to supply task capabilities. Plan local files and Local JAR artifacts for each Worker.

Validate safety and capacity with actual load. Public release additionally requires license/driver redistribution review, vulnerability disposition and SBOM/NOTICE reconciliation. The repository and candidates remain private.
