# iJob maintenance rules

Read `document/standards/README.md` and the applicable language/UI/database standard before changing code. Follow `document/operations/workspace-and-environments.md` for all configuration, runtime resources and evidence. The user’s explicit instructions take precedence.

This checkout is the only maintained source tree. Never use archived copies as a second source tree. Keep runtime data, secrets, personal tool paths and reports outside the checkout in the environment directory. Do not implicitly reuse an existing database, SFTP server, account or service. Register each new resource before creating it; preserve old environments unless the user explicitly authorizes their cleanup.

Keep product/Maven/frontend versions at 1.0.0, the new `com.ifielsc.ijob` Local JAR API protocol at 1, and formal database installation at V1. Logical database capability and runtime protocol counters have separate meanings. Historical SQL bytes must not change. Build legacy migration/testing resources using `tools/ci/prepare-legacy-db.py`; do not restore them to current application sources.

Preserve all quality gates and distinguish expected skips from passed tests. Update the exact `.repository/allowlist.json` and regenerate `.gitignore` for reviewed additions/removals. Never use force-add to bypass the policy. Do not commit credentials, runtime configuration, databases or generated evidence.
