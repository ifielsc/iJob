# Candidate delivery and follow-up

[简体中文](../../zh-CN/development/release-checklist.md) · [Manual home](../README.md)

## Candidate checks

- Trace source, manifest, hashes, docs and screenshot provenance; preserve old deliveries in separate directories.
- Pass bilingual pairing/links/tables, repository allowlist and license scans.
- Report actual backend/Agent, frontend and distribution-tool passes, expected skips and unexecuted checks.
- Bind install/lifecycle/config/sample/recovery evidence to each platform/version; do not generalize one platform's result.
- Stop only services owned by this exercise, preserving the demo and historical data.

## Follow-up priority

1. Add isolated end-to-end evidence for SSH/SFTP, S3, Kafka/RabbitMQ, mail receivers, Agent and Local JAR according to deployment priorities; retain configuration-example status until verified.
2. Extend recovery cases for incremental/equal watermarks, SCD2, multi-table partial commits, complex gateways, approvals and two-environment imports.
3. Validate production TLS, service management, capacity, alert receipt, backup RPO/RTO and multi-node failures in target environments.
4. Before public release, resolve existing license provenance, MySQL/Oracle driver redistribution, vulnerabilities and SBOM/NOTICE reconciliation; make publication a separate decision.

GitHub and candidates remain private. This checklist does not publish releases/images or change repository visibility.
