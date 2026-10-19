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

class OracleDatabaseProvisionerIT {
    @Test
    void recreateExplicitIsolatedUsers() throws Exception {
        String adminUrl = required("IJOB_ORACLE_ADMIN_URL");
        if (!adminUrl.matches("(?i)jdbc:oracle:thin:@[^/\\s]+(?::|/)[A-Za-z0-9_.-]+")) {
            throw new IllegalStateException(
                    "Oracle admin URL must explicitly identify an instance");
        }
        String password = password(required("IJOB_ORACLE_IT_PASSWORD"));
        String tablespace = identifier(required("IJOB_ORACLE_IT_TABLESPACE"), "tablespace");

        try (Connection connection =
                DriverManager.getConnection(
                        adminUrl,
                        required("IJOB_ORACLE_ADMIN_USERNAME") + " as sysdba",
                        required("IJOB_ORACLE_ADMIN_PASSWORD"))) {
            assertEquals("Oracle", connection.getMetaData().getDatabaseProductName());
            if (connection.getMetaData().getDatabaseMajorVersion() < 11) {
                throw new IllegalStateException("Oracle 11g or newer is required");
            }
            connection.setAutoCommit(true);
            recreate(connection, required("IJOB_ORACLE_IT_USERNAME"), password, tablespace);
            recreate(connection, required("IJOB_ORACLE_CLUSTER_IT_USERNAME"), password, tablespace);
        }
    }

    private void recreate(Connection connection, String user, String password, String tablespace)
            throws Exception {
        String target = identifier(user, "user").toUpperCase();
        if (!target.matches("IJOB_IT_ORA_(PRIMARY|CLUSTER)")) {
            throw new IllegalStateException(
                    "Only IJOB_IT_ORA_PRIMARY or IJOB_IT_ORA_CLUSTER may be reset");
        }
        try (PreparedStatement statement =
                connection.prepareStatement("SELECT COUNT(*) FROM dba_users WHERE username = ?")) {
            statement.setString(1, target);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                if (result.getInt(1) == 1) {
                    try (Statement drop = connection.createStatement()) {
                        drop.execute("DROP USER " + target + " CASCADE");
                    }
                }
            }
        }
        try (Statement statement = connection.createStatement()) {
            statement.execute(
                    "CREATE USER "
                            + target
                            + " IDENTIFIED BY \""
                            + password
                            + "\" DEFAULT TABLESPACE "
                            + tablespace
                            + " TEMPORARY TABLESPACE TEMP QUOTA UNLIMITED ON "
                            + tablespace);
            statement.execute(
                    "GRANT CREATE SESSION, CREATE TABLE, CREATE TRIGGER, CREATE PROCEDURE TO "
                            + target);
            statement.execute("GRANT EXECUTE ON SYS.DBMS_LOCK TO " + target);
        }
    }

    private String identifier(String value, String label) {
        if (!value.matches("[A-Za-z][A-Za-z0-9_$#]{0,29}")) {
            throw new IllegalStateException("Oracle " + label + " is not a safe identifier");
        }
        return value;
    }

    private String password(String value) {
        if (!value.matches("[A-Za-z][A-Za-z0-9_$#]{11,29}")) {
            throw new IllegalStateException("Oracle integration password is not safely formable");
        }
        return value;
    }

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    "Required Oracle integration setting is missing: " + name);
        }
        return value;
    }
}
