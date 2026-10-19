// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ifielsc.ijob.common.config.AppProperties;
import com.ifielsc.ijob.common.config.AppProperties.RuntimeMode;
import com.ifielsc.ijob.framework.database.PlatformDatabaseResolver;
import com.ifielsc.ijob.framework.database.lock.MySqlSingletonLockStrategy;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.Executors;

class RealMysqlMigrationIT {
    @Test
    void gatewaySnapshotConstraintShouldAcceptGatewaysAndRejectMissingTaskSnapshots()
            throws Exception {
        MysqlTarget target = MysqlTarget.fromEnvironment();
        flyway(target).migrate();
        long jobId = positiveId();
        long definitionId = positiveId();
        long nodeId = positiveId();
        try (Connection connection =
                DriverManager.getConnection(target.url(), target.username(), target.password())) {
            connection.setAutoCommit(false);
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate(
                        "INSERT INTO ifj_job(id,name) VALUES (" + jobId + ",'gateway-it')");
                statement.executeUpdate(
                        "INSERT INTO ifj_job_definition(id,job_id,revision,name,enabled) VALUES ("
                                + definitionId
                                + ","
                                + jobId
                                + ",1,'gateway-it',1)");
                assertEquals(
                        1,
                        statement.executeUpdate(
                                "INSERT INTO ifj_job_definition_node"
                                        + "(id,definition_id,node_key,name,node_kind,gateway_mode)"
                                        + " VALUES ("
                                        + nodeId
                                        + ","
                                        + definitionId
                                        + ",'choice','choice','GATEWAY','EXCLUSIVE')"));
                SQLException rejected =
                        assertThrows(
                                SQLException.class,
                                () ->
                                        statement.executeUpdate(
                                                "UPDATE ifj_job_definition_node SET"
                                                    + " node_kind='EXECUTION',gateway_mode=NULL,node_type='TASK',ref_id=1"
                                                    + " WHERE id="
                                                        + nodeId));
                assertEquals(3819, rejected.getErrorCode());
            } finally {
                connection.rollback();
            }
        }
    }

    @Test
    void platformDatabaseResolverShouldAcceptTheIsolatedMysqlTarget() {
        MysqlTarget target = MysqlTarget.fromEnvironment();
        AppProperties properties = new AppProperties();
        properties.getRuntime().setMode(RuntimeMode.STANDALONE);
        PlatformDatabaseResolver resolver = new PlatformDatabaseResolver(properties);
        DriverManagerDataSource dataSource =
                new DriverManagerDataSource(target.url(), target.username(), target.password());

        PlatformDatabaseResolver.ResolvedDatabase database =
                resolver.verifyMigrationTarget(dataSource);

        assertEquals("mysql", database.databaseId());
        assertTrue(database.majorVersion() >= 8);
    }

    @Test
    void extractedMysqlStrategiesShouldRetainLockAndSchemaBehavior() {
        MysqlTarget target = MysqlTarget.fromEnvironment();
        flyway(target).migrate();
        DriverManagerDataSource dataSource =
                new DriverManagerDataSource(target.url(), target.username(), target.password());
        MySqlSingletonLockStrategy lockStrategy = new MySqlSingletonLockStrategy(dataSource);
        String lockName = "ijob-it-strategy-" + UUID.randomUUID();

        lockStrategy.acquire(lockName);
        try {
            assertTrue(lockStrategy.isHeld(lockName, 2));
            assertDoesNotThrow(() -> new MySqlSchemaCompatibilityInspector(dataSource).verify());
        } finally {
            lockStrategy.release(lockName);
        }
        assertFalse(lockStrategy.isHeld(lockName, 2));
    }

    @Test
    void singletonLockIsExclusiveAndReleasedOnDisconnect() throws Exception {
        MysqlTarget target = MysqlTarget.fromEnvironment();
        String name = "ijob-it-" + java.util.UUID.randomUUID();
        Connection owner =
                DriverManager.getConnection(target.url(), target.username(), target.password());
        try (Connection contender =
                DriverManager.getConnection(target.url(), target.username(), target.password())) {
            assertEquals(1, lock(owner, "GET_LOCK", name));
            assertEquals(0, lock(contender, "GET_LOCK", name));
            owner.close();
            assertEquals(1, awaitLock(contender, name, Duration.ofSeconds(5)));
            assertEquals(1, lock(contender, "RELEASE_LOCK", name));
        } finally {
            owner.close();
        }
    }

    private int awaitLock(Connection connection, String name, Duration timeout) throws Exception {
        long deadline = System.nanoTime() + timeout.toNanos();
        int result;
        do {
            result = lock(connection, "GET_LOCK", name);
            if (result == 1) {
                return result;
            }
            Thread.sleep(25);
        } while (System.nanoTime() < deadline);
        return result;
    }

    private int lock(Connection connection, String operation, String name) throws Exception {
        String sql =
                operation.equals("GET_LOCK") ? "SELECT GET_LOCK(?, 0)" : "SELECT RELEASE_LOCK(?)";
        try (var statement = connection.prepareStatement(sql)) {
            statement.setString(1, name);
            try (var result = statement.executeQuery()) {
                assertTrue(result.next());
                return result.getInt(1);
            }
        }
    }

    @Test
    void isolatedMysqlShouldMigrateAndValidateIdempotently() throws Exception {
        MysqlTarget target = MysqlTarget.fromEnvironment();
        // Validate the server-selected catalog before Flyway performs any writes.
        try (Connection connection =
                DriverManager.getConnection(target.url(), target.username(), target.password())) {
            assertEquals(integrationDatabaseName(target.url()), connection.getCatalog());
            assertEquals("MySQL", connection.getMetaData().getDatabaseProductName());
            assertTrue(connection.getMetaData().getDatabaseMajorVersion() >= 8);
        }

        Flyway flyway = flyway(target);

        MigrateResult first = flyway.migrate();
        flyway.validate();
        MigrateResult second = flyway.migrate();

        assertTrue(first.success);
        assertTrue(second.success);
        assertEquals(0, second.migrationsExecuted);

        try (Connection connection =
                        DriverManager.getConnection(
                                target.url(), target.username(), target.password());
                Statement statement = connection.createStatement();
                ResultSet result =
                        statement.executeQuery(
                                "SELECT version FROM ifj_schema_history "
                                        + "WHERE success = 1 ORDER BY installed_rank DESC "
                                        + "LIMIT 1")) {
            assertTrue(result.next());
            assertEquals("1", result.getString(1));
        }

        try (Connection connection =
                        DriverManager.getConnection(
                                target.url(), target.username(), target.password());
                Statement statement = connection.createStatement();
                ResultSet result =
                        statement.executeQuery(
                                "SELECT COUNT(*) FROM information_schema.tables "
                                        + "WHERE table_schema = DATABASE() "
                                        + "AND table_name LIKE 'ifj_%' "
                                        + "AND table_name <> 'ifj_schema_history'")) {
            assertTrue(result.next());
            assertEquals(48, result.getInt(1));
        }

        try (Connection connection =
                        DriverManager.getConnection(
                                target.url(), target.username(), target.password());
                Statement statement = connection.createStatement();
                ResultSet result =
                        statement.executeQuery(
                                "SELECT COUNT(*) FROM ifj_schema_capability WHERE enabled = 1 AND"
                                    + " ((capability_key = 'platform-schema' AND capability_version"
                                    + " >= 38) OR (capability_key = 'event-wait-operator-relation'"
                                    + " AND capability_version >= 1) OR (capability_key ="
                                    + " 'stable-id-segment-seed' AND capability_version >= 1) OR"
                                    + " (capability_key = 'execution-group-routing' AND"
                                    + " capability_version >= 1) OR (capability_key ="
                                    + " 'conditional-dag' AND capability_version >= 1) OR"
                                    + " (capability_key = 'execution-target-routing' AND"
                                    + " capability_version >= 1) OR (capability_key ="
                                    + " 'composite-task-concurrency' AND capability_version >= 1)"
                                    + " OR (capability_key = 'schedule-misfire-policy' AND"
                                    + " capability_version >= 1) OR (capability_key ="
                                    + " 'runtime-history-v2' AND capability_version >= 1))")) {
            assertTrue(result.next());
            assertEquals(9, result.getInt(1));
        }

        try (Connection connection =
                        DriverManager.getConnection(
                                target.url(), target.username(), target.password());
                var statement =
                        connection.prepareStatement(
                                "SELECT COUNT(*) FROM information_schema.statistics "
                                        + "WHERE table_schema = DATABASE() "
                                        + "AND table_name = 'ifj_runtime_command' "
                                        + "AND index_name = "
                                        + "'idx_ifj_runtime_command_expiration'")) {
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                assertEquals(3, result.getInt(1));
            }
        }
    }

    @Test
    void boundedExpirationAndClusterCleanupSqlShouldExecute() throws Exception {
        MysqlTarget target = MysqlTarget.fromEnvironment();
        flyway(target).migrate();
        String idempotencyKey = "MANUAL:TASK:1:expiry-it-" + UUID.randomUUID();
        String requestId = "expiry-it-" + UUID.randomUUID();
        String statePrefix = "expiry-it:" + UUID.randomUUID() + ":";
        long triggerId = positiveId();
        long instanceId = positiveId();
        insertIdempotentTrigger(target, triggerId, idempotencyKey);
        insertWorkItem(target, triggerId, 3);
        insertRootInstance(target, triggerId, instanceId, 1L);

        try (Connection connection =
                DriverManager.getConnection(target.url(), target.username(), target.password())) {
            assertEquals(1, insertRuntimeCommand(target, requestId, instanceId));
            try (var statement =
                    connection.prepareStatement(
                            "UPDATE ifj_runtime_command SET expires_at = "
                                    + "TIMESTAMPADD(SECOND, -1, UTC_TIMESTAMP(3)) "
                                    + "WHERE request_id = ?")) {
                statement.setString(1, requestId);
                assertEquals(1, statement.executeUpdate());
            }
            try (var statement = connection.createStatement()) {
                assertEquals(
                        1,
                        statement.executeUpdate(
                                "UPDATE ifj_runtime_command command_record JOIN "
                                        + "(SELECT request_id FROM ifj_runtime_command "
                                        + "WHERE status IN ('PENDING', 'PROCESSING') "
                                        + "AND expires_at <= UTC_TIMESTAMP(3) "
                                        + "ORDER BY expires_at, request_id LIMIT 1) expired "
                                        + "ON expired.request_id = command_record.request_id "
                                        + "SET command_record.status = 'EXPIRED', "
                                        + "command_record.processing_owner = NULL, "
                                        + "command_record.processing_token = NULL, "
                                        + "command_record.processing_fence = NULL, "
                                        + "command_record.processed_at = UTC_TIMESTAMP(3), "
                                        + "command_record.update_time = UTC_TIMESTAMP(3)"));
            }
            assertEquals("EXPIRED", runtimeCommandStatus(target, requestId));

            try (var statement =
                    connection.prepareStatement(
                            "INSERT INTO ifj_cluster_state "
                                    + "(state_key, state_value, expires_at) VALUES "
                                    + "(?, 'expired', '1000-01-01 00:00:00.000'), "
                                    + "(?, 'live', TIMESTAMPADD(HOUR, 1, UTC_TIMESTAMP(3)))")) {
                statement.setString(1, statePrefix + "expired");
                statement.setString(2, statePrefix + "live");
                assertEquals(2, statement.executeUpdate());
            }
            try (var statement = connection.createStatement()) {
                assertEquals(
                        1,
                        statement.executeUpdate(
                                "DELETE state_record FROM ifj_cluster_state state_record JOIN "
                                        + "(SELECT state_key FROM ifj_cluster_state "
                                        + "WHERE expires_at IS NOT NULL "
                                        + "AND expires_at <= UTC_TIMESTAMP(3) "
                                        + "ORDER BY expires_at, state_key LIMIT 1) expired "
                                        + "ON expired.state_key = state_record.state_key"));
            }
            try (var statement =
                    connection.prepareStatement(
                            "SELECT COUNT(*) FROM ifj_cluster_state WHERE state_key = ?")) {
                statement.setString(1, statePrefix + "expired");
                try (ResultSet result = statement.executeQuery()) {
                    assertTrue(result.next());
                    assertEquals(0, result.getInt(1));
                }
            }
        } finally {
            try (Connection connection =
                            DriverManager.getConnection(
                                    target.url(), target.username(), target.password());
                    var statement =
                            connection.prepareStatement(
                                    "DELETE FROM ifj_cluster_state WHERE state_key LIKE ?")) {
                statement.setString(1, statePrefix + "%");
                statement.executeUpdate();
            }
            deleteInstance(target, instanceId);
            deleteTrigger(target, idempotencyKey);
        }
    }

    @Test
    void maintenanceLeaseShouldElectOneLeaderAndFenceItsTakeover() throws Exception {
        MysqlTarget target = MysqlTarget.fromEnvironment();
        flyway(target).migrate();
        String leaseName = "runtime-maintenance-it-" + UUID.randomUUID();
        String firstToken = UUID.randomUUID().toString();
        String secondToken = UUID.randomUUID().toString();
        insertMaintenanceLease(target, leaseName);

        try (var executor = Executors.newFixedThreadPool(2)) {
            var first =
                    executor.submit(
                            () -> claimMaintenanceLease(target, leaseName, "node-a", firstToken));
            var second =
                    executor.submit(
                            () -> claimMaintenanceLease(target, leaseName, "node-b", secondToken));
            int firstRows = first.get();
            int secondRows = second.get();
            assertEquals(1, firstRows + secondRows);

            String owner = firstRows == 1 ? "node-a" : "node-b";
            String token = firstRows == 1 ? firstToken : secondToken;
            assertEquals(1, renewMaintenanceLease(target, leaseName, owner, token, 1));
            assertEquals(1, releaseMaintenanceLease(target, leaseName, owner, token, 1));

            String takeoverToken = UUID.randomUUID().toString();
            assertEquals(1, claimMaintenanceLease(target, leaseName, "node-c", takeoverToken));
            assertEquals(2, maintenanceFence(target, leaseName));
            assertEquals(0, renewMaintenanceLease(target, leaseName, owner, token, 1));
        } finally {
            deleteMaintenanceLease(target, leaseName);
        }
    }

    @Test
    void timedOutRootShouldHaveOneMaintenanceOwnerAndRejectItsPreviousFence() throws Exception {
        MysqlTarget target = MysqlTarget.fromEnvironment();
        flyway(target).migrate();
        String idempotencyKey = "MANUAL:TASK:1:timeout-it-" + UUID.randomUUID();
        long triggerId = positiveId();
        long instanceId = positiveId();
        String originalToken = UUID.randomUUID().toString();
        String firstRecoveryToken = UUID.randomUUID().toString();
        String secondRecoveryToken = UUID.randomUUID().toString();
        insertIdempotentTrigger(target, triggerId, idempotencyKey);
        insertWorkItem(target, triggerId, 3);

        try {
            assertEquals(1, claimWork(target, triggerId, "node-a", originalToken));
            long originalFence = fenceToken(target, triggerId);
            insertRootInstance(target, triggerId, instanceId, originalFence);
            bindRoot(target, triggerId, instanceId, originalToken, originalFence);
            expireRootDeadlineAndClaim(target, triggerId, instanceId);

            try (var executor = Executors.newFixedThreadPool(2)) {
                var first =
                        executor.submit(
                                () ->
                                        claimTimedOutWork(
                                                target,
                                                triggerId,
                                                "maintenance-a",
                                                firstRecoveryToken));
                var second =
                        executor.submit(
                                () ->
                                        claimTimedOutWork(
                                                target,
                                                triggerId,
                                                "maintenance-b",
                                                secondRecoveryToken));
                int firstRows = first.get();
                int secondRows = second.get();
                assertEquals(1, firstRows + secondRows);

                String recoveryToken = firstRows == 1 ? firstRecoveryToken : secondRecoveryToken;
                long recoveryFence = fenceToken(target, triggerId);
                assertEquals(originalFence + 1, recoveryFence);
                assertEquals(1, workAttemptCount(target, triggerId));
                adoptRoot(target, triggerId, instanceId, recoveryToken, recoveryFence);
                assertEquals(
                        0, fencedProgress(target, instanceId, originalToken, originalFence, 25));
                assertEquals(
                        1, fencedProgress(target, instanceId, recoveryToken, recoveryFence, 50));
            }
        } finally {
            deleteInstance(target, instanceId);
            deleteTrigger(target, idempotencyKey);
        }
    }

    @Test
    void concurrentTriggerIdempotencyShouldCreateOneLogicalTrigger() throws Exception {
        MysqlTarget target = MysqlTarget.fromEnvironment();
        flyway(target).migrate();
        String idempotencyKey = "MANUAL:TASK:1:it-" + UUID.randomUUID();
        long firstId = positiveId();
        long secondId = positiveId();

        try (var executor = Executors.newFixedThreadPool(2)) {
            var first =
                    executor.submit(
                            () -> {
                                insertIdempotentTrigger(target, firstId, idempotencyKey);
                                return null;
                            });
            var second =
                    executor.submit(
                            () -> {
                                insertIdempotentTrigger(target, secondId, idempotencyKey);
                                return null;
                            });
            first.get();
            second.get();

            try (Connection connection =
                            DriverManager.getConnection(
                                    target.url(), target.username(), target.password());
                    var statement =
                            connection.prepareStatement(
                                    "SELECT COUNT(*), MIN(id) FROM ifj_trigger "
                                            + "WHERE idempotency_key = ?")) {
                statement.setString(1, idempotencyKey);
                try (ResultSet result = statement.executeQuery()) {
                    assertTrue(result.next());
                    assertEquals(1, result.getInt(1));
                    assertTrue(result.getLong(2) == firstId || result.getLong(2) == secondId);
                }
            }
        } finally {
            deleteTrigger(target, idempotencyKey);
        }
    }

    @Test
    void concurrentScheduleClaimsShouldCreateOneOwnerAndRequireItsTokenToAdvance()
            throws Exception {
        MysqlTarget target = MysqlTarget.fromEnvironment();
        flyway(target).migrate();
        long scheduleId = positiveId();
        insertDueSchedule(target, scheduleId);
        String firstToken = UUID.randomUUID().toString();
        String secondToken = UUID.randomUUID().toString();

        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> claimSchedule(target, scheduleId, firstToken));
            var second = executor.submit(() -> claimSchedule(target, scheduleId, secondToken));
            int firstRows = first.get();
            int secondRows = second.get();
            assertEquals(1, firstRows + secondRows);

            String winningToken = firstRows == 1 ? firstToken : secondToken;
            String losingToken = firstRows == 1 ? secondToken : firstToken;
            assertEquals(0, advanceSchedule(target, scheduleId, losingToken));
            assertEquals(1, advanceSchedule(target, scheduleId, winningToken));
            assertNull(scheduleClaimToken(target, scheduleId));
        } finally {
            deleteSchedule(target, scheduleId);
        }
    }

    @Test
    void concurrentEventReceiptsShouldResolveToOnePersistedEvent() throws Exception {
        MysqlTarget target = MysqlTarget.fromEnvironment();
        flyway(target).migrate();
        String sourceKey = "HTTP:it:event-" + UUID.randomUUID();
        String idempotencyKey = "receipt-" + UUID.randomUUID();
        long firstId = positiveId();
        long secondId = positiveId();

        try (var executor = Executors.newFixedThreadPool(2)) {
            var first =
                    executor.submit(() -> receiveEvent(target, firstId, sourceKey, idempotencyKey));
            var second =
                    executor.submit(
                            () -> receiveEvent(target, secondId, sourceKey, idempotencyKey));
            long firstResolvedId = first.get();
            long secondResolvedId = second.get();

            assertEquals(firstResolvedId, secondResolvedId);
            assertTrue(firstResolvedId == firstId || firstResolvedId == secondId);
            assertEquals(1, eventCount(target, sourceKey, idempotencyKey));
        } finally {
            deleteEvent(target, sourceKey, idempotencyKey);
        }
    }

    @Test
    void notificationOutboxShouldClaimOnceRenewRetryAndComplete() throws Exception {
        MysqlTarget target = MysqlTarget.fromEnvironment();
        flyway(target).migrate();
        long outboxId = positiveId();
        String deduplicationKey = "NOTIFY:it-" + UUID.randomUUID();
        insertNotificationOutbox(target, outboxId, deduplicationKey);
        String firstToken = UUID.randomUUID().toString();
        String secondToken = UUID.randomUUID().toString();

        try (var executor = Executors.newFixedThreadPool(2)) {
            var first =
                    executor.submit(
                            () -> claimNotification(target, outboxId, "node-a", firstToken));
            var second =
                    executor.submit(
                            () -> claimNotification(target, outboxId, "node-b", secondToken));
            int firstRows = first.get();
            int secondRows = second.get();
            assertEquals(1, firstRows + secondRows);

            String owner = firstRows == 1 ? "node-a" : "node-b";
            String token = firstRows == 1 ? firstToken : secondToken;
            assertEquals(1, renewNotification(target, outboxId, owner, token));
            assertEquals(1, retryNotification(target, outboxId, owner, token));
            assertEquals(1, claimNotification(target, outboxId, "node-c", secondToken));
            assertEquals(1, completeNotification(target, outboxId, "node-c", secondToken));

            try (Connection connection =
                            DriverManager.getConnection(
                                    target.url(), target.username(), target.password());
                    var statement =
                            connection.prepareStatement(
                                    "SELECT status, attempt_count, lease_owner, claim_token "
                                            + "FROM ifj_notification_outbox WHERE id = ?")) {
                statement.setLong(1, outboxId);
                try (ResultSet result = statement.executeQuery()) {
                    assertTrue(result.next());
                    assertEquals("DELIVERED", result.getString(1));
                    assertEquals(2, result.getInt(2));
                    assertNull(result.getString(3));
                    assertNull(result.getString(4));
                }
            }
        } finally {
            deleteNotificationOutbox(target, outboxId);
        }
    }

    @Test
    void persistentWorkItemShouldClaimOnceRenewRetryAndComplete() throws Exception {
        MysqlTarget target = MysqlTarget.fromEnvironment();
        flyway(target).migrate();
        String idempotencyKey = "MANUAL:TASK:1:queue-it-" + UUID.randomUUID();
        long triggerId = positiveId();
        insertIdempotentTrigger(target, triggerId, idempotencyKey);
        insertWorkItem(target, triggerId, 3);

        String firstToken = UUID.randomUUID().toString();
        String secondToken = UUID.randomUUID().toString();
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> claimWork(target, triggerId, "node-a", firstToken));
            var second = executor.submit(() -> claimWork(target, triggerId, "node-b", secondToken));
            int firstRows = first.get();
            int secondRows = second.get();
            assertEquals(1, firstRows + secondRows);

            String owner = firstRows == 1 ? "node-a" : "node-b";
            String token = firstRows == 1 ? firstToken : secondToken;
            assertEquals(1, renewWork(target, triggerId, owner, token));
            assertEquals(1, retryWork(target, triggerId, owner, token, "retry"));
            assertEquals(1, claimWork(target, triggerId, "node-c", secondToken));
            assertEquals(1, completeWork(target, triggerId, "node-c", secondToken));

            try (Connection connection =
                            DriverManager.getConnection(
                                    target.url(), target.username(), target.password());
                    var statement =
                            connection.prepareStatement(
                                    "SELECT status, attempt_count, lease_owner, claim_token "
                                            + "FROM ifj_trigger_work_item WHERE trigger_id = ?")) {
                statement.setLong(1, triggerId);
                try (ResultSet result = statement.executeQuery()) {
                    assertTrue(result.next());
                    assertEquals("COMPLETED", result.getString(1));
                    assertEquals(2, result.getInt(2));
                    assertNull(result.getString(3));
                    assertNull(result.getString(4));
                }
            }
        } finally {
            deleteTrigger(target, idempotencyKey);
        }
    }

    @Test
    void newerFenceShouldRejectThePreviousOwnerStateWrite() throws Exception {
        MysqlTarget target = MysqlTarget.fromEnvironment();
        flyway(target).migrate();
        String idempotencyKey = "MANUAL:TASK:1:fence-it-" + UUID.randomUUID();
        long triggerId = positiveId();
        long instanceId = positiveId();
        insertIdempotentTrigger(target, triggerId, idempotencyKey);
        insertWorkItem(target, triggerId, 3);

        String firstToken = UUID.randomUUID().toString();
        String secondToken = UUID.randomUUID().toString();
        try {
            assertEquals(1, claimWork(target, triggerId, "node-a", firstToken));
            long firstFence = fenceToken(target, triggerId);
            insertRootInstance(target, triggerId, instanceId, firstFence);
            bindRoot(target, triggerId, instanceId, firstToken, firstFence);
            expireClaim(target, triggerId);
            assertEquals(1, claimWork(target, triggerId, "node-b", secondToken));
            long secondFence = fenceToken(target, triggerId);
            assertEquals(firstFence + 1, secondFence);
            adoptRoot(target, triggerId, instanceId, secondToken, secondFence);

            assertEquals(0, fencedProgress(target, instanceId, firstToken, firstFence, 25));
            assertEquals(1, fencedProgress(target, instanceId, secondToken, secondFence, 50));
        } finally {
            deleteInstance(target, instanceId);
            deleteTrigger(target, idempotencyKey);
        }
    }

    @Test
    void runtimeCommandShouldBeIdempotentAndTakenOverByTheNewFence() throws Exception {
        MysqlTarget target = MysqlTarget.fromEnvironment();
        flyway(target).migrate();
        String idempotencyKey = "MANUAL:TASK:1:command-it-" + UUID.randomUUID();
        String requestId = "command-it-" + UUID.randomUUID();
        long triggerId = positiveId();
        long instanceId = positiveId();
        String firstWorkToken = UUID.randomUUID().toString();
        String secondWorkToken = UUID.randomUUID().toString();
        String firstProcessingToken = UUID.randomUUID().toString();
        String secondProcessingToken = UUID.randomUUID().toString();
        insertIdempotentTrigger(target, triggerId, idempotencyKey);
        insertWorkItem(target, triggerId, 3);

        try {
            assertEquals(1, claimWork(target, triggerId, "node-a", firstWorkToken));
            long firstFence = fenceToken(target, triggerId);
            insertRootInstance(target, triggerId, instanceId, firstFence);
            bindRoot(target, triggerId, instanceId, firstWorkToken, firstFence);
            assertEquals(1, insertRuntimeCommand(target, requestId, instanceId));
            assertEquals(0, insertRuntimeCommand(target, requestId, instanceId));
            assertEquals(
                    1,
                    claimRuntimeCommand(
                            target,
                            requestId,
                            triggerId,
                            "node-a",
                            firstProcessingToken,
                            firstFence));

            expireClaim(target, triggerId);
            assertEquals(1, claimWork(target, triggerId, "node-b", secondWorkToken));
            long secondFence = fenceToken(target, triggerId);
            adoptRoot(target, triggerId, instanceId, secondWorkToken, secondFence);
            assertEquals(
                    0,
                    finishRuntimeCommand(
                            target, requestId, firstProcessingToken, firstFence, "SUCCEEDED"));
            assertEquals(
                    1,
                    claimRuntimeCommand(
                            target,
                            requestId,
                            triggerId,
                            "node-b",
                            secondProcessingToken,
                            secondFence));
            assertEquals(
                    1,
                    finishRuntimeCommand(
                            target, requestId, secondProcessingToken, secondFence, "SUCCEEDED"));
            assertEquals("SUCCEEDED", runtimeCommandStatus(target, requestId));
        } finally {
            deleteInstance(target, instanceId);
            deleteTrigger(target, idempotencyKey);
        }
    }

    @Test
    void concurrentDatabasePermitClaimsShouldNeverOverIssueASlot() throws Exception {
        MysqlTarget target = MysqlTarget.fromEnvironment();
        flyway(target).migrate();
        String firstKey = "MANUAL:TASK:1:permit-a-" + UUID.randomUUID();
        String secondKey = "MANUAL:TASK:1:permit-b-" + UUID.randomUUID();
        String scopeKey = "RESOURCE:permit-it-" + UUID.randomUUID();
        long firstTriggerId = positiveId();
        long secondTriggerId = positiveId();
        long firstInstanceId = positiveId();
        long secondInstanceId = positiveId();
        String firstWorkToken = UUID.randomUUID().toString();
        String secondWorkToken = UUID.randomUUID().toString();
        String firstPermitToken = UUID.randomUUID().toString();
        String secondPermitToken = UUID.randomUUID().toString();
        insertIdempotentTrigger(target, firstTriggerId, firstKey);
        insertIdempotentTrigger(target, secondTriggerId, secondKey);
        insertWorkItem(target, firstTriggerId, 3);
        insertWorkItem(target, secondTriggerId, 3);

        try {
            assertEquals(1, claimWork(target, firstTriggerId, "node-a", firstWorkToken));
            assertEquals(1, claimWork(target, secondTriggerId, "node-b", secondWorkToken));
            long firstFence = fenceToken(target, firstTriggerId);
            long secondFence = fenceToken(target, secondTriggerId);
            insertRootInstance(target, firstTriggerId, firstInstanceId, firstFence);
            insertRootInstance(target, secondTriggerId, secondInstanceId, secondFence);
            bindRoot(target, firstTriggerId, firstInstanceId, firstWorkToken, firstFence);
            bindRoot(target, secondTriggerId, secondInstanceId, secondWorkToken, secondFence);
            insertPermitSlot(target, scopeKey, 1);

            try (var executor = Executors.newFixedThreadPool(2)) {
                var first =
                        executor.submit(
                                () ->
                                        claimPermit(
                                                target,
                                                scopeKey,
                                                firstInstanceId,
                                                "node-a",
                                                firstPermitToken,
                                                firstWorkToken,
                                                firstFence));
                var second =
                        executor.submit(
                                () ->
                                        claimPermit(
                                                target,
                                                scopeKey,
                                                secondInstanceId,
                                                "node-b",
                                                secondPermitToken,
                                                secondWorkToken,
                                                secondFence));
                assertEquals(1, first.get() + second.get());
            }
            assertEquals(1, activePermitCount(target, scopeKey));
        } finally {
            deletePermitScope(target, scopeKey);
            deleteInstance(target, firstInstanceId);
            deleteInstance(target, secondInstanceId);
            deleteTrigger(target, firstKey);
            deleteTrigger(target, secondKey);
        }
    }

    @Test
    void coordinationFoundationShouldAllowIndependentNodeRowsWithDatabaseTime() throws Exception {
        MysqlTarget target = MysqlTarget.fromEnvironment();
        flyway(target).migrate();
        String firstRunId = UUID.randomUUID().toString();
        String secondRunId = UUID.randomUUID().toString();

        try (Connection first =
                        DriverManager.getConnection(
                                target.url(), target.username(), target.password());
                Connection second =
                        DriverManager.getConnection(
                                target.url(), target.username(), target.password())) {
            insertRun(first, firstRunId, "it-node-a");
            insertRun(second, secondRunId, "it-node-b");

            try (var statement =
                    first.prepareStatement(
                            "SELECT COUNT(*), "
                                    + "MAX(ABS(TIMESTAMPDIFF(SECOND, heartbeat_at, "
                                    + "UTC_TIMESTAMP(3)))) FROM ifj_app_run "
                                    + "WHERE run_id IN (?, ?)")) {
                statement.setString(1, firstRunId);
                statement.setString(2, secondRunId);
                try (ResultSet result = statement.executeQuery()) {
                    assertTrue(result.next());
                    assertEquals(2, result.getInt(1));
                    assertTrue(result.getLong(2) <= 5L);
                }
            }
        } finally {
            deleteRuns(target, firstRunId, secondRunId);
        }
    }

    @Test
    void concurrentDatabaseIdSegmentsShouldNeverOverlap() throws Exception {
        MysqlTarget target = MysqlTarget.fromEnvironment();
        flyway(target).migrate();
        String segmentKey = "it-" + UUID.randomUUID();

        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> reserveSegment(target, segmentKey, 100));
            var second = executor.submit(() -> reserveSegment(target, segmentKey, 100));
            LongRange firstRange = first.get();
            LongRange secondRange = second.get();

            assertEquals(100L, firstRange.endExclusive() - firstRange.startInclusive());
            assertEquals(100L, secondRange.endExclusive() - secondRange.startInclusive());
            assertTrue(
                    firstRange.endExclusive() <= secondRange.startInclusive()
                            || secondRange.endExclusive() <= firstRange.startInclusive());
        } finally {
            try (Connection connection =
                            DriverManager.getConnection(
                                    target.url(), target.username(), target.password());
                    var statement =
                            connection.prepareStatement(
                                    "DELETE FROM ifj_id_segment WHERE segment_key = ?")) {
                statement.setString(1, segmentKey);
                statement.executeUpdate();
            }
        }
    }

    private Flyway flyway(MysqlTarget target) {
        return Flyway.configure()
                .dataSource(target.url(), target.username(), target.password())
                .locations("classpath:db/release/mysql")
                .table("ifj_schema_history")
                .baselineOnMigrate(false)
                .validateOnMigrate(true)
                .cleanDisabled(true)
                .load();
    }

    private void insertIdempotentTrigger(MysqlTarget target, long triggerId, String idempotencyKey)
            throws Exception {
        try (Connection connection =
                        DriverManager.getConnection(
                                target.url(), target.username(), target.password());
                var statement =
                        connection.prepareStatement(
                                "INSERT INTO ifj_trigger (id, target_type, target_id, "
                                        + "trigger_type, idempotency_key, status, trigger_time) "
                                        + "VALUES (?, 'TASK', 1, 'MANUAL', ?, 'WAITING', "
                                        + "UTC_TIMESTAMP(3)) ON DUPLICATE KEY UPDATE id = id")) {
            statement.setLong(1, triggerId);
            statement.setString(2, idempotencyKey);
            statement.executeUpdate();
        }
    }

    private void insertDueSchedule(MysqlTarget target, long scheduleId) throws Exception {
        try (Connection connection =
                        DriverManager.getConnection(
                                target.url(), target.username(), target.password());
                var statement =
                        connection.prepareStatement(
                                "INSERT INTO ifj_schedule (id, target_type, target_id,"
                                        + " schedule_type, interval_sec, enabled, next_fire_time)"
                                        + " VALUES (?, 'TASK', 1, 'INTERVAL', 60, 1,"
                                        + " TIMESTAMPADD(SECOND, -1, UTC_TIMESTAMP(3)))")) {
            statement.setLong(1, scheduleId);
            assertEquals(1, statement.executeUpdate());
        }
    }

    private int claimSchedule(MysqlTarget target, long scheduleId, String token) throws Exception {
        try (Connection connection =
                        DriverManager.getConnection(
                                target.url(), target.username(), target.password());
                var statement =
                        connection.prepareStatement(
                                "UPDATE ifj_schedule SET fire_claim_token = ?, fire_claimed_at ="
                                    + " UTC_TIMESTAMP(3) WHERE id = ? AND enabled = 1 AND"
                                    + " next_fire_time <= UTC_TIMESTAMP(3) AND fire_claim_token IS"
                                    + " NULL")) {
            statement.setString(1, token);
            statement.setLong(2, scheduleId);
            return statement.executeUpdate();
        }
    }

    private int advanceSchedule(MysqlTarget target, long scheduleId, String token)
            throws Exception {
        try (Connection connection =
                        DriverManager.getConnection(
                                target.url(), target.username(), target.password());
                var statement =
                        connection.prepareStatement(
                                "UPDATE ifj_schedule SET last_fire_time = next_fire_time,"
                                    + " next_fire_time = TIMESTAMPADD(SECOND, interval_sec,"
                                    + " next_fire_time), fire_claim_token = NULL, fire_claimed_at ="
                                    + " NULL, update_time = UTC_TIMESTAMP(3) WHERE id = ? AND"
                                    + " enabled = 1 AND fire_claim_token = ?")) {
            statement.setLong(1, scheduleId);
            statement.setString(2, token);
            return statement.executeUpdate();
        }
    }

    private String scheduleClaimToken(MysqlTarget target, long scheduleId) throws Exception {
        try (Connection connection =
                        DriverManager.getConnection(
                                target.url(), target.username(), target.password());
                var statement =
                        connection.prepareStatement(
                                "SELECT fire_claim_token FROM ifj_schedule WHERE id = ?")) {
            statement.setLong(1, scheduleId);
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                return result.getString(1);
            }
        }
    }

    private void deleteSchedule(MysqlTarget target, long scheduleId) throws Exception {
        deleteById(target, "DELETE FROM ifj_schedule WHERE id = ?", scheduleId);
    }

    private long receiveEvent(
            MysqlTarget target, long eventId, String sourceKey, String idempotencyKey)
            throws Exception {
        try (Connection connection =
                DriverManager.getConnection(target.url(), target.username(), target.password())) {
            try (var statement =
                    connection.prepareStatement(
                            "INSERT INTO ifj_event (id, event_code, source_type, source_key, "
                                    + "idempotency_key, occurred_at, arrived_at, payload_bytes) "
                                    + "VALUES (?, 'it.event', 'HTTP', ?, ?, UTC_TIMESTAMP(3), "
                                    + "UTC_TIMESTAMP(3), 0)")) {
                statement.setLong(1, eventId);
                statement.setString(2, sourceKey);
                statement.setString(3, idempotencyKey);
                statement.executeUpdate();
                return eventId;
            } catch (java.sql.SQLIntegrityConstraintViolationException duplicate) {
                try (var select =
                        connection.prepareStatement(
                                "SELECT id FROM ifj_event WHERE source_key = ? AND idempotency_key"
                                        + " = ?")) {
                    select.setString(1, sourceKey);
                    select.setString(2, idempotencyKey);
                    try (ResultSet result = select.executeQuery()) {
                        assertTrue(result.next());
                        return result.getLong(1);
                    }
                }
            }
        }
    }

    private int eventCount(MysqlTarget target, String sourceKey, String idempotencyKey)
            throws Exception {
        try (Connection connection =
                        DriverManager.getConnection(
                                target.url(), target.username(), target.password());
                var statement =
                        connection.prepareStatement(
                                "SELECT COUNT(*) FROM ifj_event WHERE source_key = ? AND"
                                        + " idempotency_key = ?")) {
            statement.setString(1, sourceKey);
            statement.setString(2, idempotencyKey);
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                return result.getInt(1);
            }
        }
    }

    private void deleteEvent(MysqlTarget target, String sourceKey, String idempotencyKey)
            throws Exception {
        try (Connection connection =
                        DriverManager.getConnection(
                                target.url(), target.username(), target.password());
                var statement =
                        connection.prepareStatement(
                                "DELETE FROM ifj_event WHERE source_key = ? AND idempotency_key"
                                        + " = ?")) {
            statement.setString(1, sourceKey);
            statement.setString(2, idempotencyKey);
            statement.executeUpdate();
        }
    }

    private void insertNotificationOutbox(
            MysqlTarget target, long outboxId, String deduplicationKey) throws Exception {
        try (Connection connection =
                        DriverManager.getConnection(
                                target.url(), target.username(), target.password());
                var statement =
                        connection.prepareStatement(
                                "INSERT INTO ifj_notification_outbox (id, deduplication_key,"
                                    + " notify_config_id, subject, content, source_type, status,"
                                    + " available_at, attempt_count, max_attempts) VALUES (?, ?, 1,"
                                    + " 'it', 'delivery', 'SYSTEM', 'READY', UTC_TIMESTAMP(3), 0,"
                                    + " 3)")) {
            statement.setLong(1, outboxId);
            statement.setString(2, deduplicationKey);
            assertEquals(1, statement.executeUpdate());
        }
    }

    private int claimNotification(MysqlTarget target, long outboxId, String owner, String token)
            throws Exception {
        try (Connection connection =
                        DriverManager.getConnection(
                                target.url(), target.username(), target.password());
                var statement =
                        connection.prepareStatement(
                                "UPDATE ifj_notification_outbox SET status = 'CLAIMED', lease_owner"
                                    + " = ?, claim_token = ?, lease_deadline = TIMESTAMPADD(SECOND,"
                                    + " 30, UTC_TIMESTAMP(3)), attempt_count = attempt_count + 1,"
                                    + " update_time = UTC_TIMESTAMP(3) WHERE id = ? AND"
                                    + " attempt_count < max_attempts AND (((status IN ('READY',"
                                    + " 'RETRY_WAIT')) AND available_at <= UTC_TIMESTAMP(3)) OR"
                                    + " (status = 'CLAIMED' AND lease_deadline <= "
                                    + "UTC_TIMESTAMP(3)))")) {
            statement.setString(1, owner);
            statement.setString(2, token);
            statement.setLong(3, outboxId);
            return statement.executeUpdate();
        }
    }

    private int renewNotification(MysqlTarget target, long outboxId, String owner, String token)
            throws Exception {
        return updateClaimedNotification(
                target,
                "UPDATE ifj_notification_outbox SET lease_deadline = TIMESTAMPADD(SECOND, 30, "
                        + "UTC_TIMESTAMP(3)), update_time = UTC_TIMESTAMP(3) WHERE id = ? "
                        + "AND status = 'CLAIMED' AND lease_owner = ? AND claim_token = ? "
                        + "AND lease_deadline > UTC_TIMESTAMP(3)",
                outboxId,
                owner,
                token);
    }

    private int retryNotification(MysqlTarget target, long outboxId, String owner, String token)
            throws Exception {
        return updateClaimedNotification(
                target,
                "UPDATE ifj_notification_outbox SET status = 'RETRY_WAIT', available_at ="
                    + " UTC_TIMESTAMP(3), lease_owner = NULL, claim_token = NULL, lease_deadline ="
                    + " NULL, update_time = UTC_TIMESTAMP(3) WHERE id = ? AND status = 'CLAIMED'"
                    + " AND lease_owner = ? AND claim_token = ? AND lease_deadline >"
                    + " UTC_TIMESTAMP(3)",
                outboxId,
                owner,
                token);
    }

    private int completeNotification(MysqlTarget target, long outboxId, String owner, String token)
            throws Exception {
        return updateClaimedNotification(
                target,
                "UPDATE ifj_notification_outbox SET status = 'DELIVERED', "
                        + "lease_owner = NULL, claim_token = NULL, lease_deadline = NULL, "
                        + "delivered_at = UTC_TIMESTAMP(3), update_time = UTC_TIMESTAMP(3) "
                        + "WHERE id = ? AND status = 'CLAIMED' AND lease_owner = ? "
                        + "AND claim_token = ? AND lease_deadline > UTC_TIMESTAMP(3)",
                outboxId,
                owner,
                token);
    }

    private int updateClaimedNotification(
            MysqlTarget target, String sql, long outboxId, String owner, String token)
            throws Exception {
        try (Connection connection =
                        DriverManager.getConnection(
                                target.url(), target.username(), target.password());
                var statement = connection.prepareStatement(sql)) {
            statement.setLong(1, outboxId);
            statement.setString(2, owner);
            statement.setString(3, token);
            return statement.executeUpdate();
        }
    }

    private void deleteNotificationOutbox(MysqlTarget target, long outboxId) throws Exception {
        deleteById(target, "DELETE FROM ifj_notification_outbox WHERE id = ?", outboxId);
    }

    private void deleteById(MysqlTarget target, String sql, long id) throws Exception {
        try (Connection connection =
                        DriverManager.getConnection(
                                target.url(), target.username(), target.password());
                var statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            statement.executeUpdate();
        }
    }

    private void insertWorkItem(MysqlTarget target, long triggerId, int maxAttempts)
            throws Exception {
        try (Connection connection =
                        DriverManager.getConnection(
                                target.url(), target.username(), target.password());
                var statement =
                        connection.prepareStatement(
                                "INSERT INTO ifj_trigger_work_item (trigger_id, status, "
                                        + "available_at, attempt_count, max_attempts) "
                                        + "VALUES (?, 'READY', UTC_TIMESTAMP(3), 0, ?)")) {
            statement.setLong(1, triggerId);
            statement.setInt(2, maxAttempts);
            assertEquals(1, statement.executeUpdate());
        }
    }

    private int claimWork(MysqlTarget target, long triggerId, String owner, String token)
            throws Exception {
        try (Connection connection =
                        DriverManager.getConnection(
                                target.url(), target.username(), target.password());
                var statement =
                        connection.prepareStatement(
                                "UPDATE ifj_trigger_work_item SET status = 'CLAIMED', "
                                        + "lease_owner = ?, claim_token = ?, "
                                        + "lease_deadline = TIMESTAMPADD(SECOND, 30, "
                                        + "UTC_TIMESTAMP(3)), attempt_count = attempt_count + 1, "
                                        + "fence_token = fence_token + 1, "
                                        + "update_time = UTC_TIMESTAMP(3) WHERE trigger_id = ? "
                                        + "AND attempt_count < max_attempts AND "
                                        + "(((status IN ('READY', 'RETRY_WAIT')) AND "
                                        + "available_at <= UTC_TIMESTAMP(3)) OR "
                                        + "(status = 'CLAIMED' AND "
                                        + "lease_deadline <= UTC_TIMESTAMP(3)))")) {
            statement.setString(1, owner);
            statement.setString(2, token);
            statement.setLong(3, triggerId);
            return statement.executeUpdate();
        }
    }

    private int renewWork(MysqlTarget target, long triggerId, String owner, String token)
            throws Exception {
        return updateClaimedWork(
                target,
                "UPDATE ifj_trigger_work_item SET lease_deadline = "
                        + "TIMESTAMPADD(SECOND, 30, UTC_TIMESTAMP(3)), "
                        + "update_time = UTC_TIMESTAMP(3) WHERE trigger_id = ? "
                        + "AND status = 'CLAIMED' AND lease_owner = ? AND claim_token = ? "
                        + "AND lease_deadline > UTC_TIMESTAMP(3)",
                triggerId,
                owner,
                token);
    }

    private int retryWork(
            MysqlTarget target, long triggerId, String owner, String token, String error)
            throws Exception {
        try (Connection connection =
                        DriverManager.getConnection(
                                target.url(), target.username(), target.password());
                var statement =
                        connection.prepareStatement(
                                "UPDATE ifj_trigger_work_item SET status = 'RETRY_WAIT', "
                                        + "available_at = UTC_TIMESTAMP(3), lease_owner = NULL, "
                                        + "claim_token = NULL, lease_deadline = NULL, "
                                        + "last_error = ?, update_time = UTC_TIMESTAMP(3) "
                                        + "WHERE trigger_id = ? AND status = 'CLAIMED' "
                                        + "AND lease_owner = ? AND claim_token = ?")) {
            statement.setString(1, error);
            statement.setLong(2, triggerId);
            statement.setString(3, owner);
            statement.setString(4, token);
            return statement.executeUpdate();
        }
    }

    private int completeWork(MysqlTarget target, long triggerId, String owner, String token)
            throws Exception {
        return updateClaimedWork(
                target,
                "UPDATE ifj_trigger_work_item SET status = 'COMPLETED', "
                        + "lease_owner = NULL, claim_token = NULL, lease_deadline = NULL, "
                        + "update_time = UTC_TIMESTAMP(3) WHERE trigger_id = ? "
                        + "AND status = 'CLAIMED' AND lease_owner = ? AND claim_token = ?",
                triggerId,
                owner,
                token);
    }

    private int updateClaimedWork(
            MysqlTarget target, String sql, long triggerId, String owner, String token)
            throws Exception {
        try (Connection connection =
                        DriverManager.getConnection(
                                target.url(), target.username(), target.password());
                var statement = connection.prepareStatement(sql)) {
            statement.setLong(1, triggerId);
            statement.setString(2, owner);
            statement.setString(3, token);
            return statement.executeUpdate();
        }
    }

    private void deleteTrigger(MysqlTarget target, String idempotencyKey) throws Exception {
        try (Connection connection =
                        DriverManager.getConnection(
                                target.url(), target.username(), target.password());
                var statement =
                        connection.prepareStatement(
                                "DELETE FROM ifj_trigger WHERE idempotency_key = ?")) {
            statement.setString(1, idempotencyKey);
            statement.executeUpdate();
        }
    }

    private long fenceToken(MysqlTarget target, long triggerId) throws Exception {
        try (Connection connection =
                        DriverManager.getConnection(
                                target.url(), target.username(), target.password());
                var statement =
                        connection.prepareStatement(
                                "SELECT fence_token FROM ifj_trigger_work_item WHERE trigger_id"
                                        + " = ?")) {
            statement.setLong(1, triggerId);
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                return result.getLong(1);
            }
        }
    }

    private void insertRootInstance(
            MysqlTarget target, long triggerId, long instanceId, long fenceToken) throws Exception {
        try (Connection connection =
                        DriverManager.getConnection(
                                target.url(), target.username(), target.password());
                var statement =
                        connection.prepareStatement(
                                "INSERT INTO ifj_instance (id, root_instance_id, execution_fence,"
                                    + " target_type, target_id, trigger_type, trigger_id, status)"
                                    + " VALUES (?, ?, ?, 'TASK', 1, 'MANUAL', ?, 'RUNNING')")) {
            statement.setLong(1, instanceId);
            statement.setLong(2, instanceId);
            statement.setLong(3, fenceToken);
            statement.setLong(4, triggerId);
            assertEquals(1, statement.executeUpdate());
        }
    }

    private void bindRoot(
            MysqlTarget target, long triggerId, long instanceId, String claimToken, long fenceToken)
            throws Exception {
        try (Connection connection =
                        DriverManager.getConnection(
                                target.url(), target.username(), target.password());
                var statement =
                        connection.prepareStatement(
                                "UPDATE ifj_trigger_work_item SET root_instance_id = ? "
                                        + "WHERE trigger_id = ? AND status = 'CLAIMED' "
                                        + "AND claim_token = ? AND fence_token = ?")) {
            statement.setLong(1, instanceId);
            statement.setLong(2, triggerId);
            statement.setString(3, claimToken);
            statement.setLong(4, fenceToken);
            assertEquals(1, statement.executeUpdate());
        }
    }

    private void expireClaim(MysqlTarget target, long triggerId) throws Exception {
        try (Connection connection =
                        DriverManager.getConnection(
                                target.url(), target.username(), target.password());
                var statement =
                        connection.prepareStatement(
                                "UPDATE ifj_trigger_work_item SET lease_deadline ="
                                    + " TIMESTAMPADD(SECOND, -1, UTC_TIMESTAMP(3)) WHERE trigger_id"
                                    + " = ?")) {
            statement.setLong(1, triggerId);
            assertEquals(1, statement.executeUpdate());
        }
    }

    private void adoptRoot(
            MysqlTarget target, long triggerId, long instanceId, String claimToken, long fenceToken)
            throws Exception {
        try (Connection connection =
                        DriverManager.getConnection(
                                target.url(), target.username(), target.password());
                var statement =
                        connection.prepareStatement(
                                "UPDATE ifj_instance SET execution_fence = ?, status = 'WAITING'"
                                    + " WHERE id = ? AND trigger_id = ? AND EXISTS (SELECT 1 FROM"
                                    + " ifj_trigger_work_item work_item WHERE work_item.trigger_id"
                                    + " = ? AND work_item.status = 'CLAIMED' AND"
                                    + " work_item.claim_token = ? AND work_item.fence_token ="
                                    + " ?)")) {
            statement.setLong(1, fenceToken);
            statement.setLong(2, instanceId);
            statement.setLong(3, triggerId);
            statement.setLong(4, triggerId);
            statement.setString(5, claimToken);
            statement.setLong(6, fenceToken);
            assertEquals(1, statement.executeUpdate());
        }
    }

    private int fencedProgress(
            MysqlTarget target, long instanceId, String claimToken, long fenceToken, int progress)
            throws Exception {
        try (Connection connection =
                        DriverManager.getConnection(
                                target.url(), target.username(), target.password());
                var statement =
                        connection.prepareStatement(
                                "UPDATE ifj_instance SET progress = ? WHERE id = ? AND"
                                    + " execution_fence = ? AND EXISTS (SELECT 1 FROM"
                                    + " ifj_trigger_work_item work_item WHERE work_item.trigger_id"
                                    + " = ifj_instance.trigger_id AND work_item.status = 'CLAIMED'"
                                    + " AND work_item.claim_token = ? AND work_item.fence_token ="
                                    + " ?)")) {
            statement.setInt(1, progress);
            statement.setLong(2, instanceId);
            statement.setLong(3, fenceToken);
            statement.setString(4, claimToken);
            statement.setLong(5, fenceToken);
            return statement.executeUpdate();
        }
    }

    private int insertRuntimeCommand(MysqlTarget target, String requestId, long instanceId)
            throws Exception {
        try (Connection connection =
                        DriverManager.getConnection(
                                target.url(), target.username(), target.password());
                var statement =
                        connection.prepareStatement(
                                "INSERT IGNORE INTO ifj_runtime_command (request_id, instance_id,"
                                    + " root_instance_id, trigger_id, command_type, status,"
                                    + " requested_by, available_at, expires_at) SELECT ?, id,"
                                    + " root_instance_id, trigger_id, 'PAUSE', 'PENDING', 'it',"
                                    + " UTC_TIMESTAMP(3), TIMESTAMPADD(MINUTE, 5, UTC_TIMESTAMP(3))"
                                    + " FROM ifj_instance WHERE id = ?")) {
            statement.setString(1, requestId);
            statement.setLong(2, instanceId);
            return statement.executeUpdate();
        }
    }

    private int claimRuntimeCommand(
            MysqlTarget target,
            String requestId,
            long triggerId,
            String owner,
            String processingToken,
            long fenceToken)
            throws Exception {
        try (Connection connection =
                        DriverManager.getConnection(
                                target.url(), target.username(), target.password());
                var statement =
                        connection.prepareStatement(
                                "UPDATE ifj_runtime_command command_record JOIN"
                                    + " ifj_trigger_work_item work_item ON work_item.trigger_id ="
                                    + " command_record.trigger_id SET command_record.status ="
                                    + " 'PROCESSING', command_record.processing_owner = ?,"
                                    + " command_record.processing_token = ?,"
                                    + " command_record.processing_fence = ?,"
                                    + " command_record.update_time = UTC_TIMESTAMP(3) WHERE"
                                    + " command_record.request_id = ? AND command_record.trigger_id"
                                    + " = ? AND (command_record.status = 'PENDING' OR"
                                    + " (command_record.status = 'PROCESSING' AND"
                                    + " command_record.processing_fence <> ?)) AND work_item.status"
                                    + " = 'CLAIMED' AND work_item.lease_owner = ? AND"
                                    + " work_item.fence_token = ? AND work_item.lease_deadline >"
                                    + " UTC_TIMESTAMP(3)")) {
            statement.setString(1, owner);
            statement.setString(2, processingToken);
            statement.setLong(3, fenceToken);
            statement.setString(4, requestId);
            statement.setLong(5, triggerId);
            statement.setLong(6, fenceToken);
            statement.setString(7, owner);
            statement.setLong(8, fenceToken);
            return statement.executeUpdate();
        }
    }

    private int finishRuntimeCommand(
            MysqlTarget target,
            String requestId,
            String processingToken,
            long fenceToken,
            String status)
            throws Exception {
        try (Connection connection =
                        DriverManager.getConnection(
                                target.url(), target.username(), target.password());
                var statement =
                        connection.prepareStatement(
                                "UPDATE ifj_runtime_command command_record JOIN"
                                    + " ifj_trigger_work_item work_item ON work_item.trigger_id ="
                                    + " command_record.trigger_id SET command_record.status = ?,"
                                    + " command_record.result_message = 'done',"
                                    + " command_record.processing_owner = NULL,"
                                    + " command_record.processing_token = NULL,"
                                    + " command_record.processing_fence = NULL,"
                                    + " command_record.processed_at = UTC_TIMESTAMP(3) WHERE"
                                    + " command_record.request_id = ? AND command_record.status ="
                                    + " 'PROCESSING' AND command_record.processing_token = ? AND"
                                    + " command_record.processing_fence = ? AND work_item.status ="
                                    + " 'CLAIMED' AND work_item.lease_owner ="
                                    + " command_record.processing_owner AND work_item.fence_token ="
                                    + " command_record.processing_fence AND"
                                    + " work_item.lease_deadline > UTC_TIMESTAMP(3)")) {
            statement.setString(1, status);
            statement.setString(2, requestId);
            statement.setString(3, processingToken);
            statement.setLong(4, fenceToken);
            return statement.executeUpdate();
        }
    }

    private String runtimeCommandStatus(MysqlTarget target, String requestId) throws Exception {
        try (Connection connection =
                        DriverManager.getConnection(
                                target.url(), target.username(), target.password());
                var statement =
                        connection.prepareStatement(
                                "SELECT status FROM ifj_runtime_command WHERE request_id = ?")) {
            statement.setString(1, requestId);
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                return result.getString(1);
            }
        }
    }

    private void insertPermitSlot(MysqlTarget target, String scopeKey, int slotNo)
            throws Exception {
        try (Connection connection =
                        DriverManager.getConnection(
                                target.url(), target.username(), target.password());
                var statement =
                        connection.prepareStatement(
                                "INSERT INTO ifj_concurrency_permit (scope_key, slot_no) VALUES (?,"
                                        + " ?)")) {
            statement.setString(1, scopeKey);
            statement.setInt(2, slotNo);
            assertEquals(1, statement.executeUpdate());
        }
    }

    private int claimPermit(
            MysqlTarget target,
            String scopeKey,
            long instanceId,
            String owner,
            String permitToken,
            String claimToken,
            long executionFence)
            throws Exception {
        try (Connection connection =
                        DriverManager.getConnection(
                                target.url(), target.username(), target.password());
                var statement =
                        connection.prepareStatement(
                                "UPDATE ifj_concurrency_permit permit JOIN ifj_instance"
                                    + " instance_record ON instance_record.id = ? JOIN"
                                    + " ifj_trigger_work_item work_item ON work_item.trigger_id ="
                                    + " instance_record.trigger_id SET permit.owner_instance_id ="
                                    + " instance_record.id, permit.owner_root_instance_id ="
                                    + " instance_record.root_instance_id, permit.owner_trigger_id ="
                                    + " instance_record.trigger_id, permit.lease_owner = ?,"
                                    + " permit.permit_token = ?, permit.permit_fence ="
                                    + " permit.permit_fence + 1, permit.execution_fence = ?,"
                                    + " permit.lease_deadline = TIMESTAMPADD(SECOND, 30,"
                                    + " UTC_TIMESTAMP(3)) WHERE permit.scope_key = ? AND"
                                    + " permit.slot_no = 1 AND (permit.owner_instance_id IS NULL OR"
                                    + " permit.lease_deadline <= UTC_TIMESTAMP(3)) AND"
                                    + " instance_record.execution_fence = ? AND"
                                    + " instance_record.status IN ('WAITING', 'RUNNING') AND"
                                    + " work_item.status = 'CLAIMED' AND work_item.claim_token = ?"
                                    + " AND work_item.fence_token = ? AND work_item.lease_deadline"
                                    + " > UTC_TIMESTAMP(3)")) {
            statement.setLong(1, instanceId);
            statement.setString(2, owner);
            statement.setString(3, permitToken);
            statement.setLong(4, executionFence);
            statement.setString(5, scopeKey);
            statement.setLong(6, executionFence);
            statement.setString(7, claimToken);
            statement.setLong(8, executionFence);
            return statement.executeUpdate();
        }
    }

    private int activePermitCount(MysqlTarget target, String scopeKey) throws Exception {
        try (Connection connection =
                        DriverManager.getConnection(
                                target.url(), target.username(), target.password());
                var statement =
                        connection.prepareStatement(
                                "SELECT COUNT(*) FROM ifj_concurrency_permit WHERE scope_key = ?"
                                        + " AND owner_instance_id IS NOT NULL AND lease_deadline >"
                                        + " UTC_TIMESTAMP(3)")) {
            statement.setString(1, scopeKey);
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                return result.getInt(1);
            }
        }
    }

    private void deletePermitScope(MysqlTarget target, String scopeKey) throws Exception {
        try (Connection connection =
                        DriverManager.getConnection(
                                target.url(), target.username(), target.password());
                var statement =
                        connection.prepareStatement(
                                "DELETE FROM ifj_concurrency_permit WHERE scope_key = ?")) {
            statement.setString(1, scopeKey);
            statement.executeUpdate();
        }
    }

    private void deleteInstance(MysqlTarget target, long instanceId) throws Exception {
        try (Connection connection =
                        DriverManager.getConnection(
                                target.url(), target.username(), target.password());
                var statement =
                        connection.prepareStatement("DELETE FROM ifj_instance WHERE id = ?")) {
            statement.setLong(1, instanceId);
            statement.executeUpdate();
        }
    }

    private void insertMaintenanceLease(MysqlTarget target, String leaseName) throws Exception {
        try (Connection connection =
                        DriverManager.getConnection(
                                target.url(), target.username(), target.password());
                var statement =
                        connection.prepareStatement(
                                "INSERT INTO ifj_maintenance_lease (lease_name) VALUES (?)")) {
            statement.setString(1, leaseName);
            assertEquals(1, statement.executeUpdate());
        }
    }

    private int claimMaintenanceLease(
            MysqlTarget target, String leaseName, String owner, String token) throws Exception {
        try (Connection connection =
                        DriverManager.getConnection(
                                target.url(), target.username(), target.password());
                var statement =
                        connection.prepareStatement(
                                "UPDATE ifj_maintenance_lease SET lease_owner = ?, lease_token = ?,"
                                    + " lease_deadline = TIMESTAMPADD(SECOND, 30,"
                                    + " UTC_TIMESTAMP(3)), fence_token = fence_token + 1,"
                                    + " last_acquired_at = UTC_TIMESTAMP(3) WHERE lease_name = ?"
                                    + " AND (lease_owner IS NULL OR lease_deadline <="
                                    + " UTC_TIMESTAMP(3))")) {
            statement.setString(1, owner);
            statement.setString(2, token);
            statement.setString(3, leaseName);
            return statement.executeUpdate();
        }
    }

    private int renewMaintenanceLease(
            MysqlTarget target, String leaseName, String owner, String token, long fence)
            throws Exception {
        try (Connection connection =
                        DriverManager.getConnection(
                                target.url(), target.username(), target.password());
                var statement =
                        connection.prepareStatement(
                                "UPDATE ifj_maintenance_lease SET lease_deadline ="
                                    + " TIMESTAMPADD(SECOND, 30, UTC_TIMESTAMP(3)) WHERE lease_name"
                                    + " = ? AND lease_owner = ? AND lease_token = ? AND fence_token"
                                    + " = ? AND lease_deadline > UTC_TIMESTAMP(3)")) {
            statement.setString(1, leaseName);
            statement.setString(2, owner);
            statement.setString(3, token);
            statement.setLong(4, fence);
            return statement.executeUpdate();
        }
    }

    private int releaseMaintenanceLease(
            MysqlTarget target, String leaseName, String owner, String token, long fence)
            throws Exception {
        try (Connection connection =
                        DriverManager.getConnection(
                                target.url(), target.username(), target.password());
                var statement =
                        connection.prepareStatement(
                                "UPDATE ifj_maintenance_lease SET lease_owner = NULL, lease_token ="
                                    + " NULL, lease_deadline = NULL WHERE lease_name = ? AND"
                                    + " lease_owner = ? AND lease_token = ? AND fence_token = ?")) {
            statement.setString(1, leaseName);
            statement.setString(2, owner);
            statement.setString(3, token);
            statement.setLong(4, fence);
            return statement.executeUpdate();
        }
    }

    private long maintenanceFence(MysqlTarget target, String leaseName) throws Exception {
        try (Connection connection =
                        DriverManager.getConnection(
                                target.url(), target.username(), target.password());
                var statement =
                        connection.prepareStatement(
                                "SELECT fence_token FROM ifj_maintenance_lease WHERE lease_name ="
                                        + " ?")) {
            statement.setString(1, leaseName);
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                return result.getLong(1);
            }
        }
    }

    private void deleteMaintenanceLease(MysqlTarget target, String leaseName) throws Exception {
        try (Connection connection =
                        DriverManager.getConnection(
                                target.url(), target.username(), target.password());
                var statement =
                        connection.prepareStatement(
                                "DELETE FROM ifj_maintenance_lease WHERE lease_name = ?")) {
            statement.setString(1, leaseName);
            statement.executeUpdate();
        }
    }

    private void expireRootDeadlineAndClaim(MysqlTarget target, long triggerId, long instanceId)
            throws Exception {
        try (Connection connection =
                DriverManager.getConnection(target.url(), target.username(), target.password())) {
            try (var instance =
                    connection.prepareStatement(
                            "UPDATE ifj_instance SET timeout_at = TIMESTAMPADD(SECOND, -1,"
                                    + " UTC_TIMESTAMP(3)) WHERE id = ?")) {
                instance.setLong(1, instanceId);
                assertEquals(1, instance.executeUpdate());
            }
            try (var work =
                    connection.prepareStatement(
                            "UPDATE ifj_trigger_work_item SET lease_deadline ="
                                    + " TIMESTAMPADD(SECOND, -1, UTC_TIMESTAMP(3)) WHERE trigger_id"
                                    + " = ?")) {
                work.setLong(1, triggerId);
                assertEquals(1, work.executeUpdate());
            }
        }
    }

    private int claimTimedOutWork(MysqlTarget target, long triggerId, String owner, String token)
            throws Exception {
        try (Connection connection =
                        DriverManager.getConnection(
                                target.url(), target.username(), target.password());
                var statement =
                        connection.prepareStatement(
                                "UPDATE ifj_trigger_work_item work_item JOIN ifj_instance"
                                    + " root_instance ON root_instance.id ="
                                    + " work_item.root_instance_id AND"
                                    + " root_instance.parent_instance_id IS NULL SET"
                                    + " work_item.status = 'CLAIMED', work_item.lease_owner = ?,"
                                    + " work_item.claim_token = ?, work_item.lease_deadline ="
                                    + " TIMESTAMPADD(SECOND, 30, UTC_TIMESTAMP(3)),"
                                    + " work_item.fence_token = work_item.fence_token + 1 WHERE"
                                    + " work_item.trigger_id = ? AND root_instance.status IN"
                                    + " ('WAITING', 'RUNNING', 'PAUSED', 'WAITING_EVENT') AND"
                                    + " root_instance.timeout_at <= UTC_TIMESTAMP(3) AND"
                                    + " work_item.status = 'CLAIMED' AND work_item.lease_deadline"
                                    + " <= UTC_TIMESTAMP(3)")) {
            statement.setString(1, owner);
            statement.setString(2, token);
            statement.setLong(3, triggerId);
            return statement.executeUpdate();
        }
    }

    private int workAttemptCount(MysqlTarget target, long triggerId) throws Exception {
        try (Connection connection =
                        DriverManager.getConnection(
                                target.url(), target.username(), target.password());
                var statement =
                        connection.prepareStatement(
                                "SELECT attempt_count FROM ifj_trigger_work_item WHERE trigger_id ="
                                        + " ?")) {
            statement.setLong(1, triggerId);
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                return result.getInt(1);
            }
        }
    }

    private long positiveId() {
        long value = UUID.randomUUID().getMostSignificantBits() & Long.MAX_VALUE;
        return value == 0 ? 1 : value;
    }

    private void insertRun(Connection connection, String runId, String instanceName)
            throws Exception {
        try (var statement =
                connection.prepareStatement(
                        "INSERT INTO ifj_app_run (run_id, instance_name, runtime_mode, roles, "
                                + "status, application_version, host_name, process_id, started_at, "
                                + "heartbeat_at) VALUES (?, ?, 'STANDALONE', 'API,WORKER', "
                                + "'RUNNING', 'it', 'localhost', 1, UTC_TIMESTAMP(3), "
                                + "UTC_TIMESTAMP(3))")) {
            statement.setString(1, runId);
            statement.setString(2, instanceName);
            assertEquals(1, statement.executeUpdate());
        }
    }

    private void deleteRuns(MysqlTarget target, String firstRunId, String secondRunId)
            throws Exception {
        try (Connection connection =
                        DriverManager.getConnection(
                                target.url(), target.username(), target.password());
                var statement =
                        connection.prepareStatement(
                                "DELETE FROM ifj_app_run WHERE run_id IN (?, ?)")) {
            statement.setString(1, firstRunId);
            statement.setString(2, secondRunId);
            statement.executeUpdate();
        }
    }

    private LongRange reserveSegment(MysqlTarget target, String segmentKey, int segmentSize)
            throws Exception {
        try (Connection connection =
                DriverManager.getConnection(target.url(), target.username(), target.password())) {
            connection.setAutoCommit(false);
            try {
                try (var insert =
                        connection.prepareStatement(
                                "INSERT INTO ifj_id_segment "
                                        + "(segment_key, next_value, segment_size, update_time) "
                                        + "VALUES (?, 1, ?, UTC_TIMESTAMP(3)) "
                                        + "ON DUPLICATE KEY UPDATE segment_key = ?")) {
                    insert.setString(1, segmentKey);
                    insert.setInt(2, segmentSize);
                    insert.setString(3, segmentKey);
                    insert.executeUpdate();
                }

                long start;
                try (var select =
                        connection.prepareStatement(
                                "SELECT next_value FROM ifj_id_segment "
                                        + "WHERE segment_key = ? FOR UPDATE")) {
                    select.setString(1, segmentKey);
                    try (ResultSet result = select.executeQuery()) {
                        assertTrue(result.next());
                        start = result.getLong(1);
                    }
                }
                long end = Math.addExact(start, segmentSize);
                try (var update =
                        connection.prepareStatement(
                                "UPDATE ifj_id_segment SET next_value = ?, segment_size = ?, "
                                        + "update_time = UTC_TIMESTAMP(3) "
                                        + "WHERE segment_key = ? AND next_value = ?")) {
                    update.setLong(1, end);
                    update.setInt(2, segmentSize);
                    update.setString(3, segmentKey);
                    update.setLong(4, start);
                    assertEquals(1, update.executeUpdate());
                }
                connection.commit();
                return new LongRange(start, end);
            } catch (Exception exception) {
                connection.rollback();
                throw exception;
            }
        }
    }

    private record LongRange(long startInclusive, long endExclusive) {}

    private record MysqlTarget(String url, String username, String password) {
        private static MysqlTarget fromEnvironment() {
            String url = required("IJOB_MYSQL_IT_URL");
            String username = required("IJOB_MYSQL_IT_USERNAME");
            String password = System.getenv("IJOB_MYSQL_IT_PASSWORD");
            integrationDatabaseName(url);
            return new MysqlTarget(url, username, password == null ? "" : password);
        }

        private static String required(String name) {
            String value = System.getenv(name);
            if (value == null || value.isBlank()) {
                throw new IllegalStateException(
                        "Required MySQL integration environment is missing: " + name);
            }
            return value;
        }
    }

    static String integrationDatabaseName(String url) {
        if (url == null || !url.startsWith("jdbc:mysql://")) {
            throw new IllegalStateException("MySQL integration JDBC URL is invalid");
        }
        try {
            java.net.URI uri = java.net.URI.create(url.substring("jdbc:".length()));
            String path = uri.getRawPath();
            if (uri.getHost() == null
                    || uri.getUserInfo() != null
                    || uri.getRawFragment() != null
                    || path == null
                    || !path.matches("/ijob_it_[A-Za-z0-9_]+")) {
                throw new IllegalStateException("An isolated ijob_it_ database is required");
            }
            return path.substring(1);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("MySQL integration JDBC URL is invalid", exception);
        }
    }
}
