# Candidate delivery checks

[简体中文](../../zh-CN/development/release-checklist.md) · [Manual home](../README.md)

This checklist applies to the current iJob 1.0.0 candidate. Repository visibility, release assets and website deployment require separate authorization. Local checks do not publish anything. Historical plans and acceptance records remain in the maintenance workspace; they are neither current tasks nor proof that this candidate passed.

## Source and content

- Compare the candidate against the exact file allowlist; exclude private website engineering, runtime data, real configuration, secrets, caches and generated reports.
- Scan current files and Git history intended for publication, and review image provenance. Heuristic scanning does not prove the absence of secrets.
- Keep both manuals, release notes, configuration tables and local links consistent; remove current-tense claims about retired features and old candidates.
- Keep product/Maven/frontend version 1.0.0, Local JAR protocol 1 and formal first-install database migration V1. The historical database conversion tool is outside current modules and downloads; historical SQL and compatibility tests retain their provenance.

## Downloads and licenses

- Check the asset list in the [release notes](../releases.md), ZIP/TAR paths, file hashes, launchers and runtime payloads.
- Native and Docker reference the same standalone source ZIP and source-manifest. SHA256SUMS.txt must match the actual download bytes. Different candidates named 1.0.0 cannot share hashes or acceptance claims without verification.
- Match the project LICENSE, actual third-party LICENSE/NOTICE files, frontend inventory and image OS materials to the delivered artifacts.
- Users supply MySQL Connector/J and Oracle JDBC/NLS; they are not embedded in runtime bundles. PostgreSQL, SQL Server and SQLite drivers remain bundled with notices. Test-scope dependencies are not runtime distribution.
- Provide the matching `ijob-1.0.0-third-party-sources.zip` and checksums alongside binary downloads at no additional charge. It contains applicable corresponding source, patches and build materials. Application source or a maintainer's local cache is not a substitute.

## Validation and limitations

- Distinguish checks executed in this run, evidence reused through identical runtime bytes, expected skips and checks not executed. Packaging with `-DskipTests` does not pass tests.
- Existing local candidate evidence as of 2026-11-01: backend/Agent 1683 passed and 2 conditional real-message skips; frontend 447 unit, 560 regular browser and 147 production browser tests passed; 39 distribution checks passed. The OS upgrade added 12 low-level compatibility, 13 Docker runtime, 9 Compose configuration and 4 driver-loading checks. Documentation repackaging does not rerun those tests.
- Verify remote CI for the frozen commit, Full browser integration with real backends and the applicable runtime matrix separately. Lightweight CI or Basic does not replace them. Expanded OS/database/MQ/recovery combinations not rerun retain their original evidence limits; do not claim universal coverage.
- Retain raw vulnerability findings and applicability decisions. Ubuntu packages and Spring/MyBatis shaded OGNL still have known scan findings; see the [release notes](../releases.md). Do not claim zero vulnerabilities.
- Users maintain native host operating systems. OS packages inside distributed images are part of the release and must be reviewed. An Ubuntu image does not restrict native installation to Ubuntu.

## At formal publication

Freeze and review the actual commit and its CI/test evidence; make GitHub Issues available and, once the repository is public, enable and verify GitHub Private vulnerability reporting and security-notification subscriptions (no separate mailbox; SECURITY.md does not enable the feature automatically). After publication authorization, upload the complete asset set and checksums, then verify downloads, hashes, third-party source access and manual entry points as a recipient. Update pages that still say downloads are unavailable. Domain registration and website launch are separate; planned addresses are not live links.
