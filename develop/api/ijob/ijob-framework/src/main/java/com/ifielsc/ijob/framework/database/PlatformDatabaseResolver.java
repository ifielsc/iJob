package com.ifielsc.ijob.framework.database;

import com.ifielsc.ijob.common.config.AppProperties;
import com.ifielsc.ijob.common.config.AppProperties.PlatformDatabaseVendor;

import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.Locale;

import javax.sql.DataSource;

@Component
public class PlatformDatabaseResolver {
    private final AppProperties appProperties;
    private DataSource cachedDataSource;
    private ResolvedDatabase cachedDatabase;

    public PlatformDatabaseResolver(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    public String databaseId(DataSource dataSource) throws SQLException {
        if (!appProperties.getRuntime().getMode().isPersistent()) {
            return databaseId(appProperties.getPlatformDatabase().getVendor());
        }
        ResolvedDatabase database = resolve(dataSource);
        verifyConfiguredDatabase(database);
        return database.databaseId();
    }

    public ResolvedDatabase verifyMigrationTarget(DataSource dataSource) {
        try {
            ResolvedDatabase database = resolve(dataSource);
            verifyConfiguredDatabase(database);
            return database;
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to identify the platform database", exception);
        }
    }

    public ResolvedDatabase verifyPersistentDatabase(DataSource dataSource) {
        if (!appProperties.getRuntime().getMode().isPersistent()) {
            return null;
        }
        return verifyMigrationTarget(dataSource);
    }

    public synchronized ResolvedDatabase resolve(DataSource dataSource) throws SQLException {
        if (dataSource == cachedDataSource && cachedDatabase != null) {
            return cachedDatabase;
        }
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metadata = connection.getMetaData();
            ResolvedDatabase database =
                    fromMetadata(
                            metadata.getDatabaseProductName(),
                            metadata.getDatabaseMajorVersion(),
                            metadata.getDatabaseMinorVersion(),
                            metadata.getURL(),
                            connection.getCatalog(),
                            connection.getSchema());
            cachedDataSource = dataSource;
            cachedDatabase = database;
            return database;
        }
    }

    static ResolvedDatabase fromMetadata(
            String productName, int majorVersion, int minorVersion, String jdbcUrl) {
        return fromMetadata(productName, majorVersion, minorVersion, jdbcUrl, null, null);
    }

    private static ResolvedDatabase fromMetadata(
            String productName,
            int majorVersion,
            int minorVersion,
            String jdbcUrl,
            String catalog,
            String schema) {
        String normalizedProduct = normalize(productName);
        PlatformDatabaseVendor vendor;
        String databaseId;
        if (normalizedProduct.contains("mysql")) {
            vendor = PlatformDatabaseVendor.MYSQL;
            databaseId = "mysql";
        } else if (normalizedProduct.contains("postgresql")) {
            vendor = PlatformDatabaseVendor.POSTGRESQL;
            databaseId = "postgresql";
        } else if (normalizedProduct.contains("microsoft sql server")) {
            vendor = PlatformDatabaseVendor.SQLSERVER;
            databaseId = "sqlserver";
        } else if (normalizedProduct.contains("oracle")) {
            vendor = PlatformDatabaseVendor.ORACLE;
            databaseId = "oracle";
        } else if (normalizedProduct.contains("sqlite")) {
            vendor = PlatformDatabaseVendor.SQLITE;
            databaseId = "sqlite";
        } else if (normalizedProduct.equals("h2")) {
            vendor = null;
            databaseId = "h2";
        } else {
            vendor = null;
            databaseId = null;
        }
        PlatformDatabaseVendor urlVendor = vendorFromUrl(jdbcUrl);
        if (vendor != null && urlVendor != null && vendor != urlVendor) {
            throw new IllegalStateException(
                    "JDBC metadata product and URL identify different database vendors");
        }
        return new ResolvedDatabase(
                vendor, databaseId, majorVersion, minorVersion, catalog, schema);
    }

    private void verifyConfiguredDatabase(ResolvedDatabase database) {
        PlatformDatabaseVendor expected = appProperties.getPlatformDatabase().getVendor();
        if (expected == null) {
            throw new IllegalStateException("Platform database vendor is required");
        }
        if (database.vendor() == null) {
            throw new IllegalStateException("The configured platform database product is unknown");
        }
        if (expected != database.vendor()) {
            throw new IllegalStateException(
                    "Configured platform database vendor does not match the JDBC target");
        }
        verifyMinimumVersion(database);
        verifySchema(database);
        if (expected != PlatformDatabaseVendor.MYSQL
                && expected != PlatformDatabaseVendor.POSTGRESQL
                && expected != PlatformDatabaseVendor.SQLSERVER
                && expected != PlatformDatabaseVendor.ORACLE
                && expected != PlatformDatabaseVendor.SQLITE) {
            throw new IllegalStateException(
                    "Platform database adapter "
                            + expected.name().toLowerCase(Locale.ROOT)
                            + " is not enabled in implementation phase 6.6");
        }
    }

    private void verifySchema(ResolvedDatabase database) {
        if (database.vendor() != PlatformDatabaseVendor.POSTGRESQL
                && database.vendor() != PlatformDatabaseVendor.SQLSERVER
                && database.vendor() != PlatformDatabaseVendor.ORACLE) {
            return;
        }
        String expectedSchema = appProperties.getPlatformDatabase().getSchema();
        if (expectedSchema == null || expectedSchema.isBlank()) {
            throw new IllegalStateException(database.vendor() + " platform schema is required");
        }
        if (!expectedSchema.equalsIgnoreCase(database.schema())) {
            throw new IllegalStateException(
                    "Configured "
                            + switch (database.vendor()) {
                                case POSTGRESQL -> "PostgreSQL";
                                case SQLSERVER -> "SQL Server";
                                case ORACLE -> "Oracle";
                                default -> database.vendor().name();
                            }
                            + " schema does not match the JDBC target");
        }
    }

    private void verifyMinimumVersion(ResolvedDatabase database) {
        boolean supported =
                switch (database.vendor()) {
                    case MYSQL -> database.majorVersion() >= 8;
                    case POSTGRESQL -> database.majorVersion() >= 14;
                    case SQLSERVER -> database.majorVersion() >= 15;
                    case ORACLE -> database.majorVersion() >= 11;
                    case SQLITE ->
                            database.majorVersion() > 3
                                    || (database.majorVersion() == 3
                                            && database.minorVersion() >= 35);
                };
        if (!supported) {
            throw new IllegalStateException(
                    "The platform database version is below the supported minimum");
        }
    }

    private static PlatformDatabaseVendor vendorFromUrl(String jdbcUrl) {
        String url = normalize(jdbcUrl);
        if (url.startsWith("jdbc:mysql:")) {
            return PlatformDatabaseVendor.MYSQL;
        }
        if (url.startsWith("jdbc:postgresql:")) {
            return PlatformDatabaseVendor.POSTGRESQL;
        }
        if (url.startsWith("jdbc:sqlserver:")) {
            return PlatformDatabaseVendor.SQLSERVER;
        }
        if (url.startsWith("jdbc:oracle:")) {
            return PlatformDatabaseVendor.ORACLE;
        }
        if (url.startsWith("jdbc:sqlite:")) {
            return PlatformDatabaseVendor.SQLITE;
        }
        return null;
    }

    private static String databaseId(PlatformDatabaseVendor vendor) {
        if (vendor == null) {
            return null;
        }
        return switch (vendor) {
            case MYSQL -> "mysql";
            case POSTGRESQL -> "postgresql";
            case SQLSERVER -> "sqlserver";
            case ORACLE -> "oracle";
            case SQLITE -> "sqlite";
        };
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    public record ResolvedDatabase(
            PlatformDatabaseVendor vendor,
            String databaseId,
            int majorVersion,
            int minorVersion,
            String catalog,
            String schema) {}
}
