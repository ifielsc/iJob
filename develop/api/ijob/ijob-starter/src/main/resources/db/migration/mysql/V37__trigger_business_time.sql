-- Preserve legacy UTC timestamps; historical business context remains unknown.
ALTER TABLE ifj_trigger ADD COLUMN business_date VARCHAR(10) NULL;
ALTER TABLE ifj_trigger ADD COLUMN business_time VARCHAR(48) NULL;
ALTER TABLE ifj_trigger ADD COLUMN business_zone_id VARCHAR(128) NULL;
UPDATE ifj_schema_capability SET capability_version=37,update_time=CURRENT_TIMESTAMP(3) WHERE capability_key='platform-schema';
