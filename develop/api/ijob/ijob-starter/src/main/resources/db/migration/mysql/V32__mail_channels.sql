CREATE TABLE ifj_mail_channel (
 id BIGINT NOT NULL PRIMARY KEY,
 name VARCHAR(128) NOT NULL,
 config LONGTEXT NOT NULL,
 enabled BOOLEAN NOT NULL,
 revision BIGINT NOT NULL,
 create_time DATETIME(3) NOT NULL,
 update_time DATETIME(3) NOT NULL
);
CREATE INDEX idx_mail_channel_updated ON ifj_mail_channel (update_time, id);
