package com.ifielsc.ijob;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.ifielsc.ijob.common.config.AppProperties;
import com.ifielsc.ijob.common.config.AppProperties.PlatformDatabaseVendor;
import com.ifielsc.ijob.framework.database.PlatformDatabaseResolver;
import com.ifielsc.ijob.framework.runtime.SingletonDatabaseLock;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.jdbc.datasource.AbstractDataSource;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DatabaseMetaData;

class PlatformFlywayConfigurationTest {
    @Test
    void oracleWithoutExplicitSchemaShouldBeRejectedBeforeFlywayExecutesSql() {
        AppProperties properties = new AppProperties();
        properties.getPlatformDatabase().setVendor(PlatformDatabaseVendor.ORACLE);
        PlatformDatabaseResolver resolver = new PlatformDatabaseResolver(properties);
        FlywayMigrationStrategy strategy =
                new PlatformFlywayConfiguration()
                        .platformFlywayMigrationStrategy(
                                resolver,
                                properties,
                                new SingletonDatabaseLock(java.util.List.of(), properties));
        Flyway flyway =
                Flyway.configure()
                        .dataSource(new MetadataOnlyDataSource())
                        .locations("classpath:db/migration/oracle")
                        .load();

        IllegalStateException exception =
                assertThrows(IllegalStateException.class, () -> strategy.migrate(flyway));

        assertEquals("ORACLE platform schema is required", exception.getMessage());
    }

    private static class MetadataOnlyDataSource extends AbstractDataSource {
        @Override
        public Connection getConnection() {
            DatabaseMetaData metadata =
                    (DatabaseMetaData)
                            Proxy.newProxyInstance(
                                    DatabaseMetaData.class.getClassLoader(),
                                    new Class<?>[] {DatabaseMetaData.class},
                                    (proxy, method, args) ->
                                            switch (method.getName()) {
                                                case "getDatabaseProductName" -> "Oracle";
                                                case "getDatabaseMajorVersion" -> 11;
                                                case "getDatabaseMinorVersion" -> 2;
                                                case "getURL" ->
                                                        "jdbc:oracle:thin:@localhost:1521/x";
                                                default -> defaultValue(method.getReturnType());
                                            });
            return (Connection)
                    Proxy.newProxyInstance(
                            Connection.class.getClassLoader(),
                            new Class<?>[] {Connection.class},
                            (proxy, method, args) ->
                                    "getMetaData".equals(method.getName())
                                            ? metadata
                                            : defaultValue(method.getReturnType()));
        }

        @Override
        public Connection getConnection(String username, String password) {
            return getConnection();
        }

        private static Object defaultValue(Class<?> type) {
            if (!type.isPrimitive() || type == Void.TYPE) {
                return null;
            }
            if (type == Boolean.TYPE) {
                return false;
            }
            return 0;
        }
    }
}
