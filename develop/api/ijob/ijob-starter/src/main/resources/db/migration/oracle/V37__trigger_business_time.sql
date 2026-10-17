-- Preserve legacy UTC timestamps; historical business context remains unknown.
ALTER TABLE ifj_trigger ADD business_date VARCHAR2(10);
ALTER TABLE ifj_trigger ADD business_time VARCHAR2(48);
ALTER TABLE ifj_trigger ADD business_zone_id VARCHAR2(128);
UPDATE ifj_schema_capability SET capability_version=37,update_time=SYS_EXTRACT_UTC(SYSTIMESTAMP) WHERE capability_key='platform-schema';
