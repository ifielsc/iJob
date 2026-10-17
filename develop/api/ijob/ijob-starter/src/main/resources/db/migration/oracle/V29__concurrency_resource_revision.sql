ALTER TABLE ifj_concurrency_resource ADD (version NUMBER(19) DEFAULT 1 NOT NULL);
-- Keep new plugin revisions above every legacy revision, including after deletion/recreation.
UPDATE ifj_runtime_guard SET revision = (SELECT COALESCE(MAX(version),0) + 1 FROM ifj_plugin_concurrency_policy)
WHERE guard_key='concurrency-policy'
  AND revision <= (SELECT COALESCE(MAX(version),0) FROM ifj_plugin_concurrency_policy);
