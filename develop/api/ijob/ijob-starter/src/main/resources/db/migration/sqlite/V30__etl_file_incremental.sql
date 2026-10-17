ALTER TABLE ifj_etl_checkpoint ADD COLUMN owner_instance_id BIGINT NULL;
ALTER TABLE ifj_etl_checkpoint ADD COLUMN owner_token VARCHAR(64) NULL;
CREATE TABLE ifj_etl_file_record (
    task_id BIGINT NOT NULL,
    checkpoint_key VARCHAR(128) NOT NULL,
    path_hash VARCHAR(64) NOT NULL,
    content_digest VARCHAR(64) NOT NULL,
    marker_value VARCHAR(2048) NOT NULL,
    last_instance_id BIGINT NOT NULL,
    CONSTRAINT pk_ifj_etl_file_record PRIMARY KEY (task_id, checkpoint_key, path_hash)
);
