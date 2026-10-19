// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.framework.runtime;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.ifielsc.ijob.common.config.AppProperties;
import com.ifielsc.ijob.common.config.AppProperties.AuthMode;
import com.ifielsc.ijob.common.config.AppProperties.PlatformDatabaseVendor;
import com.ifielsc.ijob.common.config.AppProperties.RuntimeMode;

import org.junit.jupiter.api.Test;

class StartupConfigurationValidatorTest {
    private static final String HASH =
            "$2a$10$123456789012345678901u1234567890123456789012345678901";

    @Test
    void developmentShouldAllowExplicitAnonymousMode() {
        AppProperties properties = properties(RuntimeMode.DEVELOPMENT, AuthMode.NONE);

        assertDoesNotThrow(() -> validator(properties, false).validate());
    }

    @Test
    void standaloneShouldRequireStaticAuthentication() {
        AppProperties properties = properties(RuntimeMode.STANDALONE, AuthMode.NONE);

        IllegalStateException exception =
                assertThrows(
                        IllegalStateException.class, () -> validator(properties, true).validate());

        assertEquals("Standalone mode requires static authentication", exception.getMessage());
    }

    @Test
    void standaloneShouldRequireFlyway() {
        AppProperties properties = configuredStandalone();

        IllegalStateException exception =
                assertThrows(
                        IllegalStateException.class, () -> validator(properties, false).validate());

        assertEquals("Standalone mode requires Flyway validation", exception.getMessage());
    }

    @Test
    void staticAuthenticationShouldFailFastWhenIncomplete() {
        AppProperties properties = properties(RuntimeMode.DEVELOPMENT, AuthMode.STATIC);

        IllegalStateException exception =
                assertThrows(
                        IllegalStateException.class, () -> validator(properties, false).validate());

        assertEquals("Static authentication configuration is incomplete", exception.getMessage());
    }

    @Test
    void configuredStandaloneShouldPass() {
        assertDoesNotThrow(() -> validator(configuredStandalone(), true).validate());
    }

    @Test
    void configuredClusterShouldPass() {
        assertDoesNotThrow(() -> validator(configuredCluster(), true).validate());
    }

    @Test
    void platformDatabaseVendorShouldBeRequired() {
        AppProperties properties = properties(RuntimeMode.DEVELOPMENT, AuthMode.NONE);
        properties.getPlatformDatabase().setVendor(null);

        IllegalStateException exception =
                assertThrows(
                        IllegalStateException.class, () -> validator(properties, false).validate());

        assertEquals("Platform database vendor is required", exception.getMessage());
    }

    @Test
    void postgresqlShouldRequireASafeExplicitSchema() {
        AppProperties properties = properties(RuntimeMode.DEVELOPMENT, AuthMode.NONE);
        properties.getPlatformDatabase().setVendor(PlatformDatabaseVendor.POSTGRESQL);
        properties.getPlatformDatabase().setSchema("public;drop schema public");

        IllegalStateException exception =
                assertThrows(
                        IllegalStateException.class, () -> validator(properties, false).validate());

        assertEquals("PostgreSQL platform schema is invalid", exception.getMessage());
    }

    @Test
    void postgresqlShouldAcceptAValidExplicitSchema() {
        AppProperties properties = properties(RuntimeMode.DEVELOPMENT, AuthMode.NONE);
        properties.getPlatformDatabase().setVendor(PlatformDatabaseVendor.POSTGRESQL);
        properties.getPlatformDatabase().setSchema("ijob_platform_1");

        assertDoesNotThrow(() -> validator(properties, false).validate());
    }

    @Test
    void sqliteShouldBeRejectedForClusterMode() {
        AppProperties properties = configuredCluster();
        properties.getPlatformDatabase().setVendor(PlatformDatabaseVendor.SQLITE);

        IllegalStateException exception =
                assertThrows(
                        IllegalStateException.class, () -> validator(properties, true).validate());

        assertEquals("SQLite does not support cluster runtime mode", exception.getMessage());
    }

    @Test
    void clusterShouldRequireExplicitInstanceName() {
        AppProperties properties = configuredCluster();
        properties.getRuntime().setInstanceName(" ");

        IllegalStateException exception =
                assertThrows(
                        IllegalStateException.class, () -> validator(properties, true).validate());

        assertEquals(
                "Cluster mode requires an instance name and all persistent coordinators",
                exception.getMessage());
    }

    @Test
    void clusterShouldRequirePersistentCoordinators() {
        AppProperties properties = configuredCluster();
        properties.getRuntime().getMaintenance().setEnabled(false);

        assertThrows(IllegalStateException.class, () -> validator(properties, true).validate());
    }

    @Test
    void blankInstanceNameShouldUseGeneratedDefault() {
        AppProperties properties = properties(RuntimeMode.DEVELOPMENT, AuthMode.NONE);
        properties.getRuntime().setInstanceName("");

        assertDoesNotThrow(() -> validator(properties, false).validate());
    }

    @Test
    void scannerBatchSizeShouldFailFastWhenOutOfRange() {
        AppProperties properties = properties(RuntimeMode.DEVELOPMENT, AuthMode.NONE);
        properties.getSchedule().setScanBatchSize(1001);

        IllegalStateException exception =
                assertThrows(
                        IllegalStateException.class, () -> validator(properties, false).validate());

        assertEquals("Schedule scanner configuration is invalid", exception.getMessage());
    }

    @Test
    void retentionShouldFailFastWhenBatchSizeIsInvalid() {
        AppProperties properties = properties(RuntimeMode.DEVELOPMENT, AuthMode.NONE);
        properties.getRetention().setBatchSize(0);

        IllegalStateException exception =
                assertThrows(
                        IllegalStateException.class, () -> validator(properties, false).validate());

        assertEquals("Runtime data retention configuration is invalid", exception.getMessage());
    }

    @Test
    void runtimeShouldRejectMissingRoles() {
        AppProperties properties = properties(RuntimeMode.DEVELOPMENT, AuthMode.NONE);
        properties.getRuntime().setRoles(java.util.Set.of());

        IllegalStateException exception =
                assertThrows(
                        IllegalStateException.class, () -> validator(properties, false).validate());

        assertEquals("Application runtime configuration is invalid", exception.getMessage());
    }

    @Test
    void runtimeShouldRejectUnsafeIdSegmentSize() {
        AppProperties properties = properties(RuntimeMode.DEVELOPMENT, AuthMode.NONE);
        properties.getRuntime().setIdSegmentSize(99);

        IllegalStateException exception =
                assertThrows(
                        IllegalStateException.class, () -> validator(properties, false).validate());

        assertEquals("Application runtime configuration is invalid", exception.getMessage());
    }

    @Test
    void workQueueShouldRejectRenewIntervalNotShorterThanLease() {
        AppProperties properties = properties(RuntimeMode.DEVELOPMENT, AuthMode.NONE);
        properties.getRuntime().getWorkQueue().setRenewInterval(java.time.Duration.ofSeconds(30));
        properties.getRuntime().getWorkQueue().setLeaseDuration(java.time.Duration.ofSeconds(30));

        IllegalStateException exception =
                assertThrows(
                        IllegalStateException.class, () -> validator(properties, false).validate());

        assertEquals("Trigger work queue configuration is invalid", exception.getMessage());
    }

    @Test
    void runtimeCommandShouldRejectNonPositiveTimeout() {
        AppProperties properties = properties(RuntimeMode.DEVELOPMENT, AuthMode.NONE);
        properties.getRuntime().getCommand().setTimeout(java.time.Duration.ZERO);

        IllegalStateException exception =
                assertThrows(
                        IllegalStateException.class, () -> validator(properties, false).validate());

        assertEquals("Runtime command configuration is invalid", exception.getMessage());
    }

    @Test
    void concurrencyPermitShouldRejectRenewIntervalNotShorterThanLease() {
        AppProperties properties = properties(RuntimeMode.DEVELOPMENT, AuthMode.NONE);
        properties
                .getRuntime()
                .getConcurrencyPermit()
                .setRenewInterval(java.time.Duration.ofSeconds(30));
        properties
                .getRuntime()
                .getConcurrencyPermit()
                .setLeaseDuration(java.time.Duration.ofSeconds(30));

        IllegalStateException exception =
                assertThrows(
                        IllegalStateException.class, () -> validator(properties, false).validate());

        assertEquals("Concurrency permit configuration is invalid", exception.getMessage());
    }

    @Test
    void notificationOutboxShouldRejectRenewIntervalNotShorterThanLease() {
        AppProperties properties = properties(RuntimeMode.DEVELOPMENT, AuthMode.NONE);
        properties.getNotification().getOutbox().setRenewInterval(java.time.Duration.ofSeconds(30));
        properties.getNotification().getOutbox().setLeaseDuration(java.time.Duration.ofSeconds(30));

        IllegalStateException exception =
                assertThrows(
                        IllegalStateException.class, () -> validator(properties, false).validate());

        assertEquals("Notification outbox configuration is invalid", exception.getMessage());
    }

    @Test
    void maintenanceShouldRejectRenewIntervalNotShorterThanLease() {
        AppProperties properties = properties(RuntimeMode.DEVELOPMENT, AuthMode.NONE);
        properties.getRuntime().getMaintenance().setRenewInterval(java.time.Duration.ofSeconds(30));
        properties.getRuntime().getMaintenance().setLeaseDuration(java.time.Duration.ofSeconds(30));

        IllegalStateException exception =
                assertThrows(
                        IllegalStateException.class, () -> validator(properties, false).validate());

        assertEquals("Runtime maintenance configuration is invalid", exception.getMessage());
    }

    @Test
    void maintenanceShouldRequireStaleRunWindowLongerThanHeartbeat() {
        AppProperties properties = properties(RuntimeMode.DEVELOPMENT, AuthMode.NONE);
        properties.getRuntime().setHeartbeatInterval(java.time.Duration.ofSeconds(30));
        properties
                .getRuntime()
                .getMaintenance()
                .setStaleRunTimeout(java.time.Duration.ofSeconds(30));

        IllegalStateException exception =
                assertThrows(
                        IllegalStateException.class, () -> validator(properties, false).validate());

        assertEquals("Runtime maintenance configuration is invalid", exception.getMessage());
    }

    @Test
    void cacheConsistencyShouldRejectNonPositiveWindow() {
        AppProperties properties = properties(RuntimeMode.DEVELOPMENT, AuthMode.NONE);
        properties.getCacheConsistency().setMaxStaleness(java.time.Duration.ZERO);

        IllegalStateException exception =
                assertThrows(
                        IllegalStateException.class, () -> validator(properties, false).validate());

        assertEquals("Cache consistency configuration is invalid", exception.getMessage());
    }

    private AppProperties configuredStandalone() {
        AppProperties properties = properties(RuntimeMode.STANDALONE, AuthMode.STATIC);
        properties.getAuth().setUsername("admin");
        properties.getAuth().setPasswordHash(HASH);
        return properties;
    }

    private AppProperties configuredCluster() {
        AppProperties properties = properties(RuntimeMode.CLUSTER, AuthMode.STATIC);
        properties.getRuntime().setInstanceName("cluster-node-a");
        properties.getAuth().setUsername("admin");
        properties.getAuth().setPasswordHash(HASH);
        return properties;
    }

    private AppProperties properties(RuntimeMode runtimeMode, AuthMode authMode) {
        AppProperties properties = new AppProperties();
        properties.getRuntime().setMode(runtimeMode);
        properties.getAuth().setMode(authMode);
        return properties;
    }

    private StartupConfigurationValidator validator(
            AppProperties properties, boolean flywayEnabled) {
        return new StartupConfigurationValidator(properties, flywayEnabled);
    }
}
