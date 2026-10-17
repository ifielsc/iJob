CREATE TABLE ifj_parameter_template (
 id NUMBER(19) NOT NULL PRIMARY KEY,
 name VARCHAR2(128 CHAR) NOT NULL UNIQUE,
 description VARCHAR2(500 CHAR),
 fields_json CLOB NOT NULL,
 revision NUMBER(19) NOT NULL
);
CREATE TABLE ifj_parameter_binding (
 target_type VARCHAR(16) NOT NULL,
 target_id NUMBER(19) NOT NULL,
 template_id NUMBER(19) NOT NULL,
 PRIMARY KEY (target_type, target_id),
 CONSTRAINT fk_param_binding_template FOREIGN KEY (template_id) REFERENCES ifj_parameter_template(id) ON DELETE CASCADE,
 CONSTRAINT ck_param_binding_type CHECK (target_type IN ('TASK','JOB'))
);
CREATE INDEX idx_param_binding_template ON ifj_parameter_binding(template_id);
ALTER TABLE ifj_schedule ADD parameter_form CLOB;
INSERT INTO ifj_id_segment(segment_key,next_value,segment_size,update_time) VALUES('pk:ifj_parameter_template',1,1,CAST(SYS_EXTRACT_UTC(SYSTIMESTAMP) AS TIMESTAMP(3)));
INSERT INTO ifj_schema_capability(capability_key,capability_version,enabled,create_time,update_time) VALUES('parameter-templates',1,1,CAST(SYS_EXTRACT_UTC(SYSTIMESTAMP) AS TIMESTAMP(3)),CAST(SYS_EXTRACT_UTC(SYSTIMESTAMP) AS TIMESTAMP(3)));
UPDATE ifj_schema_capability SET capability_version=36,update_time=CAST(SYS_EXTRACT_UTC(SYSTIMESTAMP) AS TIMESTAMP(3)) WHERE capability_key='platform-schema';
