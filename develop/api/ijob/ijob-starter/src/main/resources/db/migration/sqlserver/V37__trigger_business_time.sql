-- Preserve legacy UTC timestamps; historical business context remains unknown.
ALTER TABLE ifj_trigger ADD business_date VARCHAR(10) NULL;
ALTER TABLE ifj_trigger ADD business_time VARCHAR(48) NULL;
ALTER TABLE ifj_trigger ADD business_zone_id VARCHAR(128) NULL;
UPDATE ifj_schema_capability SET capability_version=37,update_time=SYSUTCDATETIME() WHERE capability_key='platform-schema';
