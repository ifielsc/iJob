// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.framework.database;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.ifielsc.ijob.common.config.AppProperties;
import com.ifielsc.ijob.common.config.AppProperties.PlatformDatabaseVendor;
import com.ifielsc.ijob.common.config.AppProperties.RuntimeMode;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.AbstractDataSource;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;

import javax.sql.DataSource;

class PlatformDatabaseResolverTest {
    @Test
    void shouldExposeStableMyBatisDatabaseIds() {
        assertAll(
                () -> assertEquals("mysql", resolved("MySQL", 8, 0, "jdbc:mysql:x").databaseId()),
                () ->
                        assertEquals(
                                "postgresql",
                                resolved("PostgreSQL", 14, 0, "jdbc:postgresql:x").databaseId()),
                () ->
                        assertEquals(
                                "sqlserver",
                                resolved("Microsoft SQL Server", 15, 0, "jdbc:sqlserver:x")
                                        .databaseId()),
                () ->
                        assertEquals(
                                "oracle",
                                resolved("Oracle", 11, 2, "jdbc:oracle:thin:@x").databaseId()),
                () ->
                        assertEquals(
                                "sqlite", resolved("SQLite", 3, 35, "jdbc:sqlite:x").databaseId()),
                () -> assertEquals("h2", resolved("H2", 2, 2, "jdbc:h2:x").databaseId()));
    }

    @Test
    void persistentRuntimeShouldRejectConfiguredVendorMismatch() {
        AppProperties properties = persistentProperties(PlatformDatabaseVendor.POSTGRESQL);
        PlatformDatabaseResolver resolver = new PlatformDatabaseResolver(properties);

        IllegalStateException exception =
                assertThrows(
                        IllegalStateException.class,
                        () -> resolver.databaseId(dataSource("MySQL", 8, 0, "jdbc:mysql:x")));

        assertEquals(
                "Configured platform database vendor does not match the JDBC target",
                exception.getMessage());
    }

    @Test
    void phase63ShouldAcceptPostgresqlFourteenOrNewer() {
        AppProperties properties = persistentProperties(PlatformDatabaseVendor.POSTGRESQL);
        properties.getPlatformDatabase().setSchema("public");
        PlatformDatabaseResolver resolver = new PlatformDatabaseResolver(properties);

        PlatformDatabaseResolver.ResolvedDatabase database =
                resolver.verifyMigrationTarget(
                        dataSource("PostgreSQL", 14, 0, "jdbc:postgresql://localhost/ijob"));

        assertEquals(PlatformDatabaseVendor.POSTGRESQL, database.vendor());
        assertEquals("postgresql", database.databaseId());
    }

    @Test
    void phase66ShouldAcceptOracleElevenGOrNewerWithMatchingUserSchema() {
        AppProperties properties = persistentProperties(PlatformDatabaseVendor.ORACLE);
        properties.getPlatformDatabase().setSchema("IJOB_PLATFORM");
        PlatformDatabaseResolver resolver = new PlatformDatabaseResolver(properties);

        PlatformDatabaseResolver.ResolvedDatabase database =
                resolver.verifyMigrationTarget(
                        dataSource("Oracle", 11, 2, "jdbc:oracle:thin:@localhost:1521/x"));

        assertEquals(PlatformDatabaseVendor.ORACLE, database.vendor());
        assertEquals("oracle", database.databaseId());
    }

    @Test
    void phase64ShouldAcceptSqliteThreeThirtyFiveOrNewer() {
        AppProperties properties = persistentProperties(PlatformDatabaseVendor.SQLITE);
        PlatformDatabaseResolver resolver = new PlatformDatabaseResolver(properties);

        PlatformDatabaseResolver.ResolvedDatabase database =
                resolver.verifyMigrationTarget(
                        dataSource("SQLite", 3, 35, "jdbc:sqlite:C:/data/ijob.db"));

        assertEquals(PlatformDatabaseVendor.SQLITE, database.vendor());
        assertEquals("sqlite", database.databaseId());
    }

    @Test
    void phase65ShouldAcceptSqlServer2019OrNewerWithExplicitSchema() {
        AppProperties properties = persistentProperties(PlatformDatabaseVendor.SQLSERVER);
        properties.getPlatformDatabase().setSchema("dbo");
        PlatformDatabaseResolver resolver = new PlatformDatabaseResolver(properties);

        PlatformDatabaseResolver.ResolvedDatabase database =
                resolver.verifyMigrationTarget(
                        dataSource(
                                "Microsoft SQL Server",
                                15,
                                0,
                                "jdbc:sqlserver://localhost;databaseName=ijob"));

        assertEquals(PlatformDatabaseVendor.SQLSERVER, database.vendor());
        assertEquals("sqlserver", database.databaseId());
    }

    @Test
    void phase64ShouldRejectSqliteBelowThreeThirtyFive() {
        AppProperties properties = persistentProperties(PlatformDatabaseVendor.SQLITE);
        PlatformDatabaseResolver resolver = new PlatformDatabaseResolver(properties);

        IllegalStateException exception =
                assertThrows(
                        IllegalStateException.class,
                        () ->
                                resolver.verifyMigrationTarget(
                                        dataSource(
                                                "SQLite", 3, 34, "jdbc:sqlite:C:/data/ijob.db")));

        assertEquals(
                "The platform database version is below the supported minimum",
                exception.getMessage());
    }

    @Test
    void shouldRejectPostgresqlBelowFourteen() {
        AppProperties properties = persistentProperties(PlatformDatabaseVendor.POSTGRESQL);
        properties.getPlatformDatabase().setSchema("public");
        PlatformDatabaseResolver resolver = new PlatformDatabaseResolver(properties);

        IllegalStateException exception =
                assertThrows(
                        IllegalStateException.class,
                        () ->
                                resolver.verifyMigrationTarget(
                                        dataSource(
                                                "PostgreSQL",
                                                13,
                                                12,
                                                "jdbc:postgresql://localhost/ijob")));

        assertEquals(
                "The platform database version is below the supported minimum",
                exception.getMessage());
    }

    @Test
    void shouldRejectPostgresqlSchemaMismatch() {
        AppProperties properties = persistentProperties(PlatformDatabaseVendor.POSTGRESQL);
        properties.getPlatformDatabase().setSchema("ijob_platform");
        PlatformDatabaseResolver resolver = new PlatformDatabaseResolver(properties);

        IllegalStateException exception =
                assertThrows(
                        IllegalStateException.class,
                        () ->
                                resolver.verifyMigrationTarget(
                                        dataSource(
                                                "PostgreSQL",
                                                14,
                                                0,
                                                "jdbc:postgresql://localhost/ijob")));

        assertEquals(
                "Configured PostgreSQL schema does not match the JDBC target",
                exception.getMessage());
    }

    @Test
    void shouldRejectDatabaseBelowSupportedMinimum() {
        AppProperties properties = persistentProperties(PlatformDatabaseVendor.MYSQL);
        PlatformDatabaseResolver resolver = new PlatformDatabaseResolver(properties);

        IllegalStateException exception =
                assertThrows(
                        IllegalStateException.class,
                        () ->
                                resolver.verifyMigrationTarget(
                                        dataSource("MySQL", 7, 4, "jdbc:mysql:legacy")));

        assertEquals(
                "The platform database version is below the supported minimum",
                exception.getMessage());
    }

    @Test
    void developmentRuntimeShouldNotOpenDatabaseForPersistentVerification() {
        AppProperties properties = new AppProperties();
        properties.getRuntime().setMode(RuntimeMode.DEVELOPMENT);
        PlatformDatabaseResolver resolver = new PlatformDatabaseResolver(properties);

        assertNull(resolver.verifyPersistentDatabase(new FailingDataSource()));
    }

    @Test
    void developmentMyBatisDatabaseIdShouldComeFromConfigurationWithoutConnecting()
            throws SQLException {
        AppProperties properties = new AppProperties();
        properties.getRuntime().setMode(RuntimeMode.DEVELOPMENT);
        properties.getPlatformDatabase().setVendor(PlatformDatabaseVendor.SQLITE);
        PlatformDatabaseResolver resolver = new PlatformDatabaseResolver(properties);

        assertEquals("sqlite", resolver.databaseId(new FailingDataSource()));
    }

    @Test
    void shouldRejectProductAndJdbcUrlDisagreement() {
        assertThrows(IllegalStateException.class, () -> resolved("Oracle", 11, 2, "jdbc:mysql:x"));
    }

    private PlatformDatabaseResolver.ResolvedDatabase resolved(
            String product, int major, int minor, String url) {
        return PlatformDatabaseResolver.fromMetadata(product, major, minor, url);
    }

    private AppProperties persistentProperties(PlatformDatabaseVendor vendor) {
        AppProperties properties = new AppProperties();
        properties.getRuntime().setMode(RuntimeMode.STANDALONE);
        properties.getPlatformDatabase().setVendor(vendor);
        return properties;
    }

    private DataSource dataSource(String product, int major, int minor, String url) {
        DatabaseMetaData metadata =
                proxy(
                        DatabaseMetaData.class,
                        (methodName) ->
                                switch (methodName) {
                                    case "getDatabaseProductName" -> product;
                                    case "getDatabaseMajorVersion" -> major;
                                    case "getDatabaseMinorVersion" -> minor;
                                    case "getURL" -> url;
                                    default -> null;
                                });
        Connection connection =
                proxy(
                        Connection.class,
                        (methodName) ->
                                switch (methodName) {
                                    case "getMetaData" -> metadata;
                                    case "getSchema" ->
                                            product.contains("SQL Server")
                                                    ? "dbo"
                                                    : product.contains("Oracle")
                                                            ? "IJOB_PLATFORM"
                                                            : "public";
                                    default -> null;
                                });
        return new AbstractDataSource() {
            @Override
            public Connection getConnection() {
                return connection;
            }

            @Override
            public Connection getConnection(String username, String password) {
                return connection;
            }
        };
    }

    @SuppressWarnings("unchecked")
    private <T> T proxy(Class<T> type, MethodResult result) {
        return (T)
                Proxy.newProxyInstance(
                        type.getClassLoader(),
                        new Class<?>[] {type},
                        (proxy, method, args) -> {
                            Object value = result.value(method.getName());
                            if (value != null || method.getReturnType() == Void.TYPE) {
                                return value;
                            }
                            if (!method.getReturnType().isPrimitive()) {
                                return null;
                            }
                            if (method.getReturnType() == Boolean.TYPE) {
                                return false;
                            }
                            return 0;
                        });
    }

    @FunctionalInterface
    private interface MethodResult {
        Object value(String methodName);
    }

    private static class FailingDataSource extends AbstractDataSource {
        @Override
        public Connection getConnection() throws SQLException {
            throw new SQLException("must not connect");
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            throw new SQLException("must not connect");
        }
    }
}
