// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.Statement;

class PostgresqlDatabaseProvisionerIT {
    @Test
    void recreateExplicitIsolatedTargets() throws Exception {
        String adminUrl = required("IJOB_POSTGRES_ADMIN_URL");
        if (!adminUrl.matches("jdbc:postgresql://[^/]+/postgres(?:\\?.*)?")) {
            throw new IllegalStateException(
                    "PostgreSQL admin URL must target the postgres database");
        }
        String adminUsername = required("IJOB_POSTGRES_ADMIN_USERNAME");
        String adminPassword = optional("IJOB_POSTGRES_ADMIN_PASSWORD");
        String owner = identifier(required("IJOB_POSTGRES_IT_USERNAME"), "role");

        try (Connection connection =
                DriverManager.getConnection(adminUrl, adminUsername, adminPassword)) {
            assertEquals("PostgreSQL", connection.getMetaData().getDatabaseProductName());
            connection.setAutoCommit(true);
            recreate(connection, required("IJOB_POSTGRES_IT_DATABASE"), owner);
            recreate(connection, required("IJOB_POSTGRES_CLUSTER_IT_DATABASE"), owner);
        }
    }

    private void recreate(Connection connection, String database, String owner) throws Exception {
        String target = identifier(database, "database");
        if (!target.matches("ijob_it_pg_[A-Za-z0-9_]+")) {
            throw new IllegalStateException("Only an isolated ijob_it_pg_ database may be reset");
        }
        try (PreparedStatement statement =
                connection.prepareStatement(
                        "SELECT pg_terminate_backend(pid) FROM pg_stat_activity "
                                + "WHERE datname = ? AND pid <> pg_backend_pid()")) {
            statement.setString(1, target);
            statement.execute();
        }
        try (Statement statement = connection.createStatement()) {
            statement.execute("DROP DATABASE IF EXISTS \"" + target + "\"");
            statement.execute("CREATE DATABASE \"" + target + "\" OWNER \"" + owner + "\"");
        }
    }

    private String identifier(String value, String label) {
        if (!value.matches("[A-Za-z_][A-Za-z0-9_]*")) {
            throw new IllegalStateException("PostgreSQL " + label + " is not a safe identifier");
        }
        return value;
    }

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    "Required PostgreSQL integration setting is missing: " + name);
        }
        return value;
    }

    private static String optional(String name) {
        String value = System.getenv(name);
        return value == null ? "" : value;
    }
}
