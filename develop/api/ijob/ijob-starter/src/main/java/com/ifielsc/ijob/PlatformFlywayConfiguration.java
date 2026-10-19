// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob;

import com.ifielsc.ijob.common.config.AppProperties;
import com.ifielsc.ijob.common.config.AppProperties.PlatformDatabaseVendor;
import com.ifielsc.ijob.common.config.AppProperties.RuntimeMode;
import com.ifielsc.ijob.framework.database.PlatformDatabaseResolver;
import com.ifielsc.ijob.framework.runtime.SingletonDatabaseLock;

import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class PlatformFlywayConfiguration {
    @Bean
    public FlywayMigrationStrategy platformFlywayMigrationStrategy(
            PlatformDatabaseResolver databaseResolver,
            AppProperties appProperties,
            SingletonDatabaseLock singletonDatabaseLock) {
        return flyway -> {
            if (appProperties.getPlatformDatabase().getVendor() == PlatformDatabaseVendor.SQLITE) {
                if (appProperties.getRuntime().getMode() != RuntimeMode.STANDALONE) {
                    throw new IllegalStateException("SQLite does not support cluster runtime mode");
                }
                singletonDatabaseLock.acquire();
            }
            databaseResolver.verifyMigrationTarget(flyway.getConfiguration().getDataSource());
            ReleaseSchemaBoundary.beforeMigration(
                    flyway,
                    appProperties
                            .getPlatformDatabase()
                            .getVendor()
                            .name()
                            .toLowerCase(java.util.Locale.ROOT));
            if (appProperties.getPlatformDatabase().getVendor()
                    == PlatformDatabaseVendor.SQLSERVER) {
                new SqlServerPreMigrationGuard().verify(flyway.getConfiguration().getDataSource());
            }
            if (appProperties.getPlatformDatabase().getVendor() == PlatformDatabaseVendor.SQLITE) {
                SqliteMigrationBoundary.migrate(flyway);
            } else {
                flyway.migrate();
            }
        };
    }
}
