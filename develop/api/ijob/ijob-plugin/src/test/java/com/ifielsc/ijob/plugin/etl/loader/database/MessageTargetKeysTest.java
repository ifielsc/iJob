// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.plugin.etl.loader.database;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

class MessageTargetKeysTest {
    private Connection database() throws SQLException {
        Connection connection = DriverManager.getConnection("jdbc:h2:mem:" + UUID.randomUUID());
        try (var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE ORDERS (ID INT PRIMARY KEY, VAL VARCHAR(30))");
            statement.execute("CREATE SCHEMA OTHER");
            statement.execute("CREATE TABLE OTHER.ORDERS (ID INT, VAL VARCHAR(30))");
            statement.execute("CREATE INDEX ORDINARY ON OTHER.ORDERS(ID)");
            statement.execute("CREATE TABLE UNIQUE_TARGET (ID INT UNIQUE, VAL VARCHAR(30))");
        }
        return connection;
    }

    private ResultSetMetaData missingIdentity() {
        return (ResultSetMetaData)
                Proxy.newProxyInstance(
                        getClass().getClassLoader(),
                        new Class<?>[] {ResultSetMetaData.class},
                        (proxy, method, args) ->
                                switch (method.getName()) {
                                    case "getTableName", "getSchemaName", "getCatalogName" -> "";
                                    default -> throw new AssertionError(method.getName());
                                });
    }

    @Test
    void missingDriverIdentityUsesCurrentSchema() throws Exception {
        try (var connection = database()) {
            assertDoesNotThrow(
                    () ->
                            MessageTargetKeys.verify(
                                    connection, missingIdentity(), List.of("ID"), "ORDERS", null));
        }
    }

    @Test
    void qualifiedTargetCannotBorrowKeyFromSameNamedTable() throws Exception {
        try (var connection = database()) {
            assertThrows(
                    SQLException.class,
                    () ->
                            MessageTargetKeys.verify(
                                    connection,
                                    missingIdentity(),
                                    List.of("ID"),
                                    "OTHER.ORDERS",
                                    "PUBLIC"));
        }
    }

    @Test
    void configuredSchemaCannotBorrowKeyFromCurrentSchema() throws Exception {
        try (var connection = database()) {
            assertThrows(
                    SQLException.class,
                    () ->
                            MessageTargetKeys.verify(
                                    connection,
                                    missingIdentity(),
                                    List.of("ID"),
                                    "ORDERS",
                                    "OTHER"));
        }
    }

    @Test
    void unfilteredUniqueIndexRemainsSupported() throws Exception {
        try (var connection = database()) {
            assertDoesNotThrow(
                    () ->
                            MessageTargetKeys.verify(
                                    connection,
                                    missingIdentity(),
                                    List.of("ID"),
                                    "UNIQUE_TARGET",
                                    null));
        }
    }

    @Test
    void differentLogicalKeyIsRejected() throws Exception {
        try (var connection = database()) {
            assertThrows(
                    SQLException.class,
                    () ->
                            MessageTargetKeys.verify(
                                    connection, missingIdentity(), List.of("VAL"), "ORDERS", null));
            assertThrows(
                    SQLException.class,
                    () ->
                            MessageTargetKeys.verify(
                                    connection,
                                    missingIdentity(),
                                    List.of("ID", "VAL"),
                                    "ORDERS",
                                    null));
        }
    }

    @Test
    void qualifiedCatalogAndSchemaAreResolved() throws Exception {
        try (var connection = database()) {
            assertDoesNotThrow(
                    () ->
                            MessageTargetKeys.verify(
                                    connection,
                                    missingIdentity(),
                                    List.of("ID"),
                                    connection.getCatalog() + ".PUBLIC.ORDERS",
                                    "OTHER"));
            assertThrows(
                    SQLException.class,
                    () ->
                            MessageTargetKeys.verify(
                                    connection,
                                    missingIdentity(),
                                    List.of("ID"),
                                    "MISSING.PUBLIC.ORDERS",
                                    null));
        }
    }

    @Test
    void driverIdentityStillTakesPrecedence() throws Exception {
        try (var connection = database();
                var statement = connection.createStatement();
                var rows = statement.executeQuery("SELECT * FROM ORDERS WHERE 1=0")) {
            assertDoesNotThrow(
                    () ->
                            MessageTargetKeys.verify(
                                    connection,
                                    rows.getMetaData(),
                                    List.of("ID"),
                                    "OTHER.ORDERS",
                                    null));
        }
    }

    @Test
    void unknownDefaultSchemaFailsClosed() throws Exception {
        try (var connection = database()) {
            Connection unknownSchema =
                    (Connection)
                            Proxy.newProxyInstance(
                                    getClass().getClassLoader(),
                                    new Class<?>[] {Connection.class},
                                    (proxy, method, args) -> {
                                        if (method.getName().equals("getSchema")) return null;
                                        return method.invoke(connection, args);
                                    });
            assertThrows(
                    SQLException.class,
                    () ->
                            MessageTargetKeys.verify(
                                    unknownSchema,
                                    missingIdentity(),
                                    List.of("ID"),
                                    "ORDERS",
                                    null));
        }
    }
}
