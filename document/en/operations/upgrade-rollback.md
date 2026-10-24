# Upgrade candidates and rollback

[简体中文](../../zh-CN/operations/upgrade-rollback.md) · [Manual home](../README.md)

1. Record current commit, artifact hash, configuration location and database version; create a restorable backup.
2. Extract the new complete bundle into a new program directory. Verify SHA256SUMS.txt, manifest and candidate results without overwriting running files.
3. Stop the old program during maintenance and start the new one with the original IJOB_HOME, preserving data, configuration, installation markers and original keys.
4. Verify readiness, login, resources, published versions, history and an independent small task; record deployment time/results.
5. Before rollback, assess database changes and external writes. If compatible, stop the new program and use the old bundle; otherwise restore a matching backup into a new target. Replacing a JAR cannot reverse migrations or external actions.

Product/Maven remain 1.0.0, formal installation migration V1 and new Local JAR protocol 1. Distinguish same-version candidates by source and hashes, not version strings. Docker must use each candidate's own image lock and launcher.

Do not connect historical databases directly to the formal application; conversion needs separate tools/resources and migration review. Updating source does not update existing bundles or running instances automatically.
