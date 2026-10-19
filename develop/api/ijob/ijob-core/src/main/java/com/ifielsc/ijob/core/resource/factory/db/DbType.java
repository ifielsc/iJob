// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.core.resource.factory.db;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum DbType {
    MYSQL("com.mysql.cj.jdbc.Driver"),
    ORACLE("oracle.jdbc.OracleDriver"),
    POSTGRESQL("org.postgresql.Driver"),
    SQLSERVER("com.microsoft.sqlserver.jdbc.SQLServerDriver");

    private final String driverClassName;
}
