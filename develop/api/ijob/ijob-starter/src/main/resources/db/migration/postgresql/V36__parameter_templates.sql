CREATE TABLE ifj_parameter_template (
 id BIGINT NOT NULL PRIMARY KEY,
 name VARCHAR(128) NOT NULL UNIQUE,
 description VARCHAR(500),
 fields_json TEXT NOT NULL,
 revision BIGINT NOT NULL
);
CREATE TABLE ifj_parameter_binding (
 target_type VARCHAR(16) NOT NULL,
 target_id BIGINT NOT NULL,
 template_id BIGINT NOT NULL,
 PRIMARY KEY (target_type, target_id),
 CONSTRAINT fk_param_binding_template FOREIGN KEY (template_id) REFERENCES ifj_parameter_template(id) ON DELETE CASCADE,
 CONSTRAINT ck_param_binding_type CHECK (target_type IN ('TASK','JOB'))
);
CREATE INDEX idx_param_binding_template ON ifj_parameter_binding(template_id);
ALTER TABLE ifj_schedule ADD parameter_form TEXT;
INSERT INTO ifj_id_segment(segment_key,next_value,segment_size,update_time) VALUES('pk:ifj_parameter_template',1,1,CURRENT_TIMESTAMP);
INSERT INTO ifj_schema_capability(capability_key,capability_version,enabled,create_time,update_time) VALUES('parameter-templates',1,TRUE,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP);
UPDATE ifj_schema_capability SET capability_version=36,update_time=CURRENT_TIMESTAMP WHERE capability_key='platform-schema';
