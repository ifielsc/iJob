// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

class MysqlDatabaseProvisionerIT {
    @Test
    void recreateExplicitIsolatedTargets() throws Exception {
        String adminUrl = required("IJOB_MYSQL_ADMIN_URL");
        if (!adminUrl.matches("jdbc:mysql://[^/]+/mysql(?:\\?.*)?")) {
            throw new IllegalStateException("MySQL admin URL must target the mysql database");
        }
        String owner = identifier(required("IJOB_MYSQL_IT_USERNAME"), "user");

        try (Connection connection =
                DriverManager.getConnection(
                        adminUrl,
                        required("IJOB_MYSQL_ADMIN_USERNAME"),
                        optional("IJOB_MYSQL_ADMIN_PASSWORD"))) {
            assertEquals("MySQL", connection.getMetaData().getDatabaseProductName());
            connection.setAutoCommit(true);
            List<String> hosts = userHosts(connection, owner);
            if (hosts.isEmpty()) {
                throw new IllegalStateException("Configured MySQL integration user is missing");
            }
            recreate(connection, required("IJOB_MYSQL_IT_DATABASE"), owner, hosts);
            recreate(connection, required("IJOB_MYSQL_CLUSTER_IT_DATABASE"), owner, hosts);
        }
    }

    private List<String> userHosts(Connection connection, String owner) throws Exception {
        List<String> hosts = new ArrayList<>();
        try (PreparedStatement statement =
                connection.prepareStatement("SELECT Host FROM mysql.user WHERE User = ?")) {
            statement.setString(1, owner);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    hosts.add(result.getString(1));
                }
            }
        }
        return hosts;
    }

    private void recreate(Connection connection, String database, String owner, List<String> hosts)
            throws Exception {
        String target = identifier(database, "database");
        if (!target.matches("ijob_it_[A-Za-z0-9_]+")) {
            throw new IllegalStateException("Only an isolated ijob_it_ database may be reset");
        }
        try (Statement statement = connection.createStatement()) {
            statement.execute("DROP DATABASE IF EXISTS `" + target + "`");
            statement.execute(
                    "CREATE DATABASE `" + target + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_bin");
            for (String host : hosts) {
                statement.execute(
                        "GRANT ALL PRIVILEGES ON `"
                                + target
                                + "`.* TO '"
                                + owner
                                + "'@'"
                                + host.replace("'", "''")
                                + "'");
            }
        }
    }

    private String identifier(String value, String label) {
        if (!value.matches("[A-Za-z_][A-Za-z0-9_]*")) {
            throw new IllegalStateException("MySQL " + label + " is not a safe identifier");
        }
        return value;
    }

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    "Required MySQL integration setting is missing: " + name);
        }
        return value;
    }

    private static String optional(String name) {
        String value = System.getenv(name);
        return value == null ? "" : value;
    }
}
