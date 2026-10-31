# Launch variables, Compose and standalone Agent

[简体中文](../../zh-CN/configuration/deployment-agent.md) · [Manual home](../README.md)

## Deployment entry points

With an [instance launcher](../installation/instance-launcher.md), the data directory and prompt language come from `instance.conf`, which takes precedence over inherited `IJOB_HOME` and `IJOB_LANG`. With legacy launchers, `IJOB_HOME` selects the private installation directory and `IJOB_LANG=en/zh` controls setup prompts; neither changes browser language. `JAVA_HOME` selects Java 21. Native `IJOB_START_TIMEOUT` / `IJOB_STOP_TIMEOUT` differ from application-executor timeouts. After first setup, edit actual files; initialization variables do not rewrite an existing installation.

Treat advanced Compose templates separately from generated first-run Compose. The former may consume IJOB_HTTP_BIND and other template variables; the latter uses its actual mappings, mounts and resource limits. After changing generated Compose, run the corresponding instance’s `ijob.sh restart`. Use legacy launchers only in compatibility workflows that explicitly select the same `IJOB_HOME`. Do not reuse another environment's project name, directories or secret files.

## Agent

The Agent is a separate process/optional image, not a scheduling Worker. Register its own address, port, token, configuration and logs; restrict network sources. Native deployment uses an external configuration with `server.port` and `agent.*`, keeping the token in a restricted file. Images use their configtree secret mount. Configure its address and matching token in a platform monitor-server resource.

`agent.hostname` is detected when absent. collect-timeout is in milliseconds (default 10000), collect-concurrency defaults to 2 and collect-queue-capacity to 4. Test connectivity and one collection, verifying metrics, collection time and errors before using monitor tasks. Connectivity does not prove an alert rule or notification has fired.

## Settings in this topic

These are source/implementation defaults; see the [complete catalog](../configuration-catalog.md) for setup overrides and provenance.

| ID / key | Level / type | Source defaults | Explanation |
| --- | --- | --- | --- |
| C178 / `agent.collect-timeout` | B / milliseconds | ${IJOB_AGENT_COLLECT_TIMEOUT:10000} | Collection timeout in milliseconds; assess monitored-host load. |
| C179 / `agent.collect-concurrency` | B / integer | ${IJOB_AGENT_COLLECT_CONCURRENCY:2} | Concurrent collections; assess target-host resources. |
| C180 / `agent.collect-queue-capacity` | B / integer | ${IJOB_AGENT_COLLECT_QUEUE_CAPACITY:4} | Collection queue capacity; excessive queues can delay responses. |
| C181 / `agent.hostname` | A / text | unset: detected automatically | Reported hostname; detected when omitted, may be set to identify the monitored host. |
| C197 / `server.port` | B / port | 8081 in image; not explicitly set in Agent YAML | Agent image internal port 8081; update probes and platform resource connection together. |
| C199 / `IJOB_HOME` | A / path | Legacy launcher defaults: Windows uses sibling ijob-data; Linux uses sibling ijob-data-linux; Docker uses sibling ijob-data-docker | Installation data root; use consistently for all commands. Changing it does not migrate data. |
| C200 / `IJOB_LANG` | A / enum | en; zh selects Chinese | Setup/management language, distinct from UI preference and notification locale. |
| C201 / `JAVA_HOME` | A / path | unset: resolve Java from PATH | Java 21 installation directory for native launch. |
| C202 / `IJOB_START_TIMEOUT` | B / seconds | 120 | Readiness wait in seconds, range 1–600; Linux scripts only, Windows waits are currently fixed. |
| C203 / `IJOB_STOP_TIMEOUT` | B / seconds | 120 | Graceful-stop wait in seconds, range 1–600; Linux scripts only, Windows waits are currently fixed. |
| C204 / `IJOB_HTTP_PORT` | A / port | 18080 | Docker host published port; recreate using the same bundle after changing. |
| C205 / `IJOB_HTTP_BIND` | B / text | 127.0.0.1 | Only advanced repository Compose supports this variable; setup-generated Compose fixes loopback binding, so setting this alone has no effect. |
| C216 / `JAVA_TOOL_OPTIONS` | B / text | images: -XX:MaxRAMPercentage=70 -Duser.timezone=UTC | JVM options; retain UTC contract in containers and size with memory limits. build.py rejects inherited values. |
| C217 / `IJOB_DEV_API_TARGET` | B / uri | unset: no proxy | Frontend development proxy target; not deployed runtime configuration. Avoid connecting demo environments. |
| C227 / `services.web.ports` | B / compose-field | 127.0.0.1:<IJOB_HTTP_PORT>:8080 | Host bind address and port; external access needs ingress and access control. |
| C230 / `services.<service>.mem_limit` | B / compose-field | backend 1g; web 128m; agent 256m | Memory limit; size with JVM fraction and workload. |
| C231 / `services.<service>.cpus` | B / compose-field | backend 2.0; advanced web/agent 1.0; setup web unset | CPU quota; setup web leaves it unset. Distinguish setup and advanced templates. |
| C232 / `services.<service>.restart` | B / compose-field | unless-stopped | Process restart policy; not task recovery or idempotency assurance. |
| C233 / `services.<service>.stop_grace_period` | B / compose-field | backend 120s; advanced web/agent 30s; setup web unset | Stop grace; setup web leaves it unset. Allow backend draining and shutdown. |
| C234 / `services.<service>.logging.driver` | B / compose-field | json-file | Container log driver; verify collection and access when changing. |
| C235 / `services.<service>.logging.options.max-size` | A / compose-field | 10m | Container log rollover size. |
| C236 / `services.<service>.logging.options.max-file` | A / compose-field | backend/web 5; agent 3 | Retained container log files. |
| C237 / `services.<service>.tmpfs` | B / compose-field | backend 256m; web 128m; agent 64m; template permissions | Temporary storage size, permissions and exec flag; review with plugin needs. |
| C245 / `services.<service>.healthcheck.interval` | B / duration | backend/web 15s; Agent 30s | Health-check interval; image defaults come from Dockerfiles. Revalidate readiness after overrides. |
| C246 / `services.<service>.healthcheck.timeout` | B / duration | backend/Agent 6s; web 5s | Per-probe timeout; image defaults come from Dockerfiles. Revalidate readiness after overrides. |
| C247 / `services.<service>.healthcheck.start_period` | B / duration | backend 90s; Agent 30s; web not explicitly set | Startup probe grace; image defaults come from Dockerfiles. Revalidate readiness after overrides. |
| C248 / `services.<service>.healthcheck.retries` | B / integer | 3 | Failure threshold; image defaults come from Dockerfiles. Revalidate readiness after overrides. |

[Five persistent launcher settings (C250–C254)](../installation/instance-launcher.md) are generated by configure. Use this entry point for daily management; saved home and language take precedence over terminal environment variables.
