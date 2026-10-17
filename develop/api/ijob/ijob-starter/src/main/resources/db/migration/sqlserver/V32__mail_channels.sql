CREATE TABLE ifj_mail_channel (
 id BIGINT NOT NULL PRIMARY KEY,
 name NVARCHAR(128) NOT NULL,
 config NVARCHAR(MAX) NOT NULL,
 enabled BIT NOT NULL,
 revision BIGINT NOT NULL,
 create_time DATETIME2(3) NOT NULL,
 update_time DATETIME2(3) NOT NULL
);
CREATE INDEX idx_mail_channel_updated ON ifj_mail_channel (update_time, id);
