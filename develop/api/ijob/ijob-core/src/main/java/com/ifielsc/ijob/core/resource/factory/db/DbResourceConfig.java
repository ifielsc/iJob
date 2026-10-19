package com.ifielsc.ijob.core.resource.factory.db;

import lombok.Data;
import lombok.ToString;

@Data
@ToString(exclude = {"jdbcUrl", "username", "password", "schema"})
public class DbResourceConfig {
    private DbType dbType;
    private String jdbcUrl;
    private String username;
    private String password;
    private String schema;
}
