ALTER TABLE ifj_etl_checkpoint ADD owner_instance_id BIGINT NULL;
ALTER TABLE ifj_etl_checkpoint ADD owner_token VARCHAR(64) COLLATE Latin1_General_100_BIN2 NULL;
CREATE TABLE ifj_etl_file_record (
    task_id BIGINT NOT NULL,
    checkpoint_key NVARCHAR(128) COLLATE Latin1_General_100_BIN2 NOT NULL,
    path_hash VARCHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL,
    content_digest VARCHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL,
    marker_value NVARCHAR(2048) NOT NULL,
    last_instance_id BIGINT NOT NULL,
    CONSTRAINT pk_ifj_etl_file_record PRIMARY KEY (task_id, checkpoint_key, path_hash)
);
