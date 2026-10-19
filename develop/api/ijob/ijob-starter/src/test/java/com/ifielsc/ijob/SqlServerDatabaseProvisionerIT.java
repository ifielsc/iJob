// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

class SqlServerDatabaseProvisionerIT {
    @Test
    void recreateExplicitIsolatedTargets() throws Exception {
        String adminUrl = required("IJOB_SQLSERVER_ADMIN_URL");
        if (!adminUrl.matches(
                "(?i)jdbc:sqlserver://[^;]+(?:;[^;=]+=[^;]*)*;databaseName=master(?:;.*)?")) {
            throw new IllegalStateException("SQL Server admin URL must explicitly target master");
        }
        String adminUsername = required("IJOB_SQLSERVER_ADMIN_USERNAME");
        String adminPassword = optional("IJOB_SQLSERVER_ADMIN_PASSWORD");
        String owner = identifier(required("IJOB_SQLSERVER_IT_USERNAME"), "login");

        try (Connection connection =
                DriverManager.getConnection(adminUrl, adminUsername, adminPassword)) {
            assertEquals("Microsoft SQL Server", connection.getMetaData().getDatabaseProductName());
            assertTrue(connection.getMetaData().getDatabaseMajorVersion() >= 15);
            connection.setAutoCommit(true);
            requireLogin(connection, owner);
            recreate(connection, required("IJOB_SQLSERVER_IT_DATABASE"), owner);
            recreate(connection, required("IJOB_SQLSERVER_CLUSTER_IT_DATABASE"), owner);
        }
    }

    private void requireLogin(Connection connection, String owner) throws Exception {
        try (PreparedStatement statement =
                connection.prepareStatement(
                        "SELECT COUNT(*) FROM sys.server_principals WHERE name = ?"); ) {
            statement.setString(1, owner);
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                if (result.getInt(1) != 1) {
                    throw new IllegalStateException(
                            "Configured SQL Server integration login is missing");
                }
            }
        }
    }

    private void recreate(Connection connection, String database, String owner) throws Exception {
        String target = identifier(database, "database");
        if (!target.matches("ijob_it_mssql_[A-Za-z0-9_]+")) {
            throw new IllegalStateException(
                    "Only an isolated ijob_it_mssql_ database may be reset");
        }
        String quotedTarget = quote(target);
        String quotedOwner = quote(owner);
        try (Statement statement = connection.createStatement()) {
            statement.execute(
                    "IF DB_ID(N'"
                            + target
                            + "') IS NOT NULL BEGIN "
                            + "ALTER DATABASE "
                            + quotedTarget
                            + " SET SINGLE_USER WITH ROLLBACK IMMEDIATE; "
                            + "DROP DATABASE "
                            + quotedTarget
                            + "; END");
            statement.execute("CREATE DATABASE " + quotedTarget);
            statement.execute(
                    "ALTER DATABASE "
                            + quotedTarget
                            + " SET READ_COMMITTED_SNAPSHOT OFF WITH ROLLBACK IMMEDIATE");
            statement.execute(
                    "ALTER AUTHORIZATION ON DATABASE::" + quotedTarget + " TO " + quotedOwner);
        }
    }

    private String identifier(String value, String label) {
        if (!value.matches("[A-Za-z_][A-Za-z0-9_]*")) {
            throw new IllegalStateException("SQL Server " + label + " is not a safe identifier");
        }
        return value;
    }

    private String quote(String identifier) {
        return "[" + identifier + "]";
    }

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    "Required SQL Server integration setting is missing: " + name);
        }
        return value;
    }

    private static String optional(String name) {
        String value = System.getenv(name);
        return value == null ? "" : value;
    }
}
