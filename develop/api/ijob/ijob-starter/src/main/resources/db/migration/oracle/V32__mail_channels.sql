CREATE TABLE ifj_mail_channel (
 id NUMBER(19) NOT NULL PRIMARY KEY,
 name NVARCHAR2(128) NOT NULL,
 config CLOB NOT NULL,
 enabled NUMBER(1) NOT NULL,
 revision NUMBER(19) NOT NULL,
 create_time TIMESTAMP(3) NOT NULL,
 update_time TIMESTAMP(3) NOT NULL
);
CREATE INDEX idx_mail_channel_updated ON ifj_mail_channel (update_time, id);
