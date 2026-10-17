ALTER TABLE ifj_etl_checkpoint ADD owner_instance_id NUMBER(19) NULL;
ALTER TABLE ifj_etl_checkpoint ADD owner_token VARCHAR2(64 CHAR) NULL;
CREATE TABLE ifj_etl_file_record (
    task_id NUMBER(19) NOT NULL,
    checkpoint_key VARCHAR2(128 CHAR) NOT NULL,
    path_hash VARCHAR2(64 CHAR) NOT NULL,
    content_digest VARCHAR2(64 CHAR) NOT NULL,
    marker_value VARCHAR2(2048 CHAR) NOT NULL,
    last_instance_id NUMBER(19) NOT NULL,
    CONSTRAINT pk_ifj_etl_file_record PRIMARY KEY (task_id, checkpoint_key, path_hash)
);
