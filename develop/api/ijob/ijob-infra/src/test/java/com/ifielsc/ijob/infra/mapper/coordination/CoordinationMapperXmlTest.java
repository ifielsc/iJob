package com.ifielsc.ijob.infra.mapper.coordination;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.util.List;
import java.util.Map;

class CoordinationMapperXmlTest {
    @Test
    void oracleReservationMustLockExistingSegmentBeforeReadingItsWatermark() throws IOException {
        Configuration configuration = new Configuration();
        configuration.setDatabaseId("oracle");
        parse(configuration, "mapper/coordination/IdSegmentMapper.xml");
        assertTrue(
                sql(
                                configuration,
                                "com.ifielsc.ijob.infra.mapper.coordination.IdSegmentMapper.selectForUpdate",
                                Map.of("segmentKey", "platform-primary-id"))
                        .endsWith("FOR UPDATE"));
    }

    @Test
    void idSegmentReservationShouldNotScanPlatformTablesAfterV19() throws IOException {
        String xml = read("mapper/coordination/IdSegmentMapper.xml");

        assertFalse(xml.contains("MAX(id)"));
        assertFalse(xml.contains("selectPlatformMaximumId"));
    }

    @Test
    void shouldParseDatabaseClockAndIdSegmentStatements() throws IOException {
        Configuration configuration = new Configuration();
        configuration.setMapUnderscoreToCamelCase(true);
        parse(configuration, "mapper/coordination/DatabaseClockMapper.xml");
        parse(configuration, "mapper/coordination/IdSegmentMapper.xml");
        parse(configuration, "mapper/coordination/TriggerWorkItemMapper.xml");

        assertTrue(
                configuration.hasStatement(
                        "com.ifielsc.ijob.infra.mapper.coordination.DatabaseClockMapper."
                                + "currentTimestamp",
                        false));
        for (String statement : new String[] {"lockExisting", "selectForUpdate", "advance"}) {
            assertTrue(
                    configuration.hasStatement(
                            "com.ifielsc.ijob.infra.mapper.coordination.IdSegmentMapper."
                                    + statement,
                            false));
        }
        for (String statement :
                new String[] {
                    "insertIfAbsent",
                    "selectByTriggerId",
                    "selectDispatchableTriggerIds",
                    "selectTimedOutTriggerIds",
                    "selectDeadOrphanTriggerIds",
                    "claimByTriggerId",
                    "claimTimedOut",
                    "claimDeadOrphan",
                    "selectByClaimToken",
                    "renew",
                    "complete",
                    "retryOrDead",
                    "markExpiredExhaustedDead"
                }) {
            assertTrue(
                    configuration.hasStatement(
                            "com.ifielsc.ijob.infra.mapper.coordination.TriggerWorkItemMapper."
                                    + statement,
                            false));
        }

        String clockSql =
                sql(
                        configuration,
                        "com.ifielsc.ijob.infra.mapper.coordination.DatabaseClockMapper."
                                + "currentTimestamp",
                        null);
        String lockSql =
                sql(
                        configuration,
                        "com.ifielsc.ijob.infra.mapper.coordination.IdSegmentMapper.selectForUpdate",
                        Map.of("segmentKey", "platform-primary-id"));
        assertTrue(clockSql.contains("CURRENT_TIMESTAMP(3)"));
        assertTrue(lockSql.endsWith("FOR UPDATE"));
        String claimSql =
                sql(
                        configuration,
                        "com.ifielsc.ijob.infra.mapper.coordination.TriggerWorkItemMapper."
                                + "claimByTriggerId",
                        Map.of(
                                "triggerId",
                                1L,
                                "leaseOwner",
                                "node-a",
                                "claimToken",
                                "token",
                                "leaseMicros",
                                30_000_000L,
                                "instanceGroups",
                                List.of("gpu"),
                                "allowedTaskIds",
                                List.of(11L),
                                "allowedJobIds",
                                List.of(21L)));
        assertTrue(claimSql.contains("CURRENT_TIMESTAMP(3)"));
        assertTrue(claimSql.contains("attempt_count = work_item.attempt_count + 1"));
        assertTrue(claimSql.contains("work_item.lease_deadline <= CURRENT_TIMESTAMP(3)"));
        assertTrue(claimSql.contains("ifj_task_definition_group"));
        assertTrue(claimSql.contains("ifj_job_definition_group"));
        assertTrue(claimSql.contains("trigger_record.target_type = 'TASK'"));
        assertTrue(claimSql.contains("trigger_record.target_type = 'JOB'"));
        assertTrue(claimSql.contains("trigger_record.target_id IN"));
        String unrestrictedClaimSql =
                sql(
                        configuration,
                        "com.ifielsc.ijob.infra.mapper.coordination.TriggerWorkItemMapper."
                                + "claimByTriggerId",
                        Map.of(
                                "triggerId",
                                1L,
                                "leaseOwner",
                                "node-a",
                                "claimToken",
                                "token",
                                "leaseMicros",
                                30_000_000L,
                                "instanceGroups",
                                List.of(),
                                "allowedTaskIds",
                                List.of(),
                                "allowedJobIds",
                                List.of()));
        String maintenanceClaimSql =
                sql(
                        configuration,
                        "com.ifielsc.ijob.infra.mapper.coordination.TriggerWorkItemMapper.claimTimedOut",
                        Map.of(
                                "triggerId",
                                1L,
                                "leaseOwner",
                                "maintenance",
                                "claimToken",
                                "token",
                                "leaseMicros",
                                30_000_000L));
        assertFalse(unrestrictedClaimSql.contains("trigger_record.target_type"));
        assertFalse(maintenanceClaimSql.contains("trigger_record.target_type"));
        assertFalse(clockSql.contains("${"));
        assertFalse(lockSql.contains("${"));
        assertFalse(claimSql.contains("${"));
    }

    @Test
    void shouldParseRuntimeCommandAndDistributedPermitStatements() throws IOException {
        Configuration configuration = new Configuration();
        configuration.setMapUnderscoreToCamelCase(true);
        parse(configuration, "mapper/coordination/RuntimeCommandMapper.xml");
        parse(configuration, "mapper/coordination/ConcurrencyPermitMapper.xml");

        for (String statement :
                new String[] {
                    "insertFromInstance",
                    "selectByRequestId",
                    "selectDtoByRequestId",
                    "rejectInitiallyInvalid",
                    "succeedInitiallySatisfied",
                    "requeueWorkForCommand",
                    "activateTriggerForRetry",
                    "selectDispatchableRequestIds",
                    "claim",
                    "finish",
                    "defer",
                    "expirePending"
                }) {
            assertTrue(
                    configuration.hasStatement(
                            "com.ifielsc.ijob.infra.mapper.coordination.RuntimeCommandMapper."
                                    + statement,
                            false));
        }
        for (String statement :
                new String[] {
                    "insertSlotIfAbsent",
                    "claimSlot",
                    "selectByToken",
                    "renew",
                    "release",
                    "isInstanceAdmissible"
                }) {
            assertTrue(
                    configuration.hasStatement(
                            "com.ifielsc.ijob.infra.mapper.coordination.ConcurrencyPermitMapper."
                                    + statement,
                            false));
        }

        String commandXml = read("mapper/coordination/RuntimeCommandMapper.xml");
        String permitXml = read("mapper/coordination/ConcurrencyPermitMapper.xml");
        assertTrue(commandXml.contains("processing_fence"));
        assertTrue(commandXml.contains("work_item.lease_deadline &gt; CURRENT_TIMESTAMP(3)"));
        assertTrue(permitXml.contains("permit_fence = permit.permit_fence + 1"));
        assertTrue(permitXml.contains("work_item.claim_token = #{claimToken}"));
        assertFalse(commandXml.contains("${"));
        assertFalse(permitXml.contains("${"));
    }

    @Test
    void shouldParseNotificationOutboxClaimAndTerminalWrites() throws IOException {
        Configuration configuration = new Configuration();
        configuration.setMapUnderscoreToCamelCase(true);
        parse(configuration, "mapper/coordination/NotificationOutboxMapper.xml");
        String namespace = "com.ifielsc.ijob.infra.mapper.coordination.NotificationOutboxMapper.";
        for (String statement :
                new String[] {
                    "insertIfAbsent",
                    "selectByDeduplicationKey",
                    "selectByClaimToken",
                    "selectDispatchableIds",
                    "claim",
                    "renew",
                    "complete",
                    "retryOrDead",
                    "markExpiredExhaustedDead"
                }) {
            assertTrue(configuration.hasStatement(namespace + statement, false));
        }
        String claim =
                sql(
                        configuration,
                        namespace + "claim",
                        Map.of(
                                "id",
                                1L,
                                "leaseOwner",
                                "node-a",
                                "claimToken",
                                "token",
                                "leaseMicros",
                                30_000_000L));
        String xml = read("mapper/coordination/NotificationOutboxMapper.xml");
        assertTrue(claim.contains("CURRENT_TIMESTAMP(3)"));
        assertTrue(claim.contains("attempt_count = attempt_count + 1"));
        assertTrue(xml.contains("ON DUPLICATE KEY UPDATE id = id"));
        assertTrue(xml.contains("config_snapshot"));
        assertTrue(xml.contains("#{configSnapshot}"));
        assertFalse(xml.contains("${"));
    }

    @Test
    void shouldParseMaintenanceLeaseAndCoordinationSnapshotStatements() throws IOException {
        Configuration configuration = new Configuration();
        configuration.setMapUnderscoreToCamelCase(true);
        parse(configuration, "mapper/coordination/MaintenanceLeaseMapper.xml");
        parse(configuration, "mapper/coordination/CoordinationSnapshotMapper.xml");

        String leaseNamespace =
                "com.ifielsc.ijob.infra.mapper.coordination.MaintenanceLeaseMapper.";
        for (String statement :
                new String[] {
                    "insertIfAbsent",
                    "claim",
                    "selectByToken",
                    "renew",
                    "lockIfHeld",
                    "markCompleted",
                    "release"
                }) {
            assertTrue(configuration.hasStatement(leaseNamespace + statement, false));
        }
        String claim =
                sql(
                        configuration,
                        leaseNamespace + "claim",
                        Map.of(
                                "leaseName",
                                "runtime-maintenance",
                                "leaseOwner",
                                "node-a",
                                "leaseToken",
                                "token",
                                "leaseMicros",
                                30_000_000L));
        String lock =
                sql(
                        configuration,
                        leaseNamespace + "lockIfHeld",
                        Map.of(
                                "leaseName",
                                "runtime-maintenance",
                                "leaseOwner",
                                "node-a",
                                "leaseToken",
                                "token",
                                "fenceToken",
                                2L));
        String snapshot =
                sql(
                        configuration,
                        "com.ifielsc.ijob.infra.mapper.coordination.CoordinationSnapshotMapper."
                                + "selectSnapshot",
                        Map.of(
                                "maintenanceLeaseName",
                                "runtime-maintenance",
                                "staleRunMicros",
                                30_000_000L));
        assertTrue(claim.contains("fence_token = fence_token + 1"));
        assertTrue(claim.contains("CURRENT_TIMESTAMP(3)"));
        assertTrue(lock.endsWith("FOR UPDATE"));
        assertTrue(snapshot.contains("command_oldest_delay_millis"));
        assertTrue(snapshot.contains("ifj_notification_outbox"));
        assertFalse(claim.contains("${"));
        assertFalse(lock.contains("${"));
        assertFalse(snapshot.contains("${"));
    }

    private String sql(Configuration configuration, String statement, Object parameter) {
        return configuration
                .getMappedStatement(statement)
                .getBoundSql(parameter)
                .getSql()
                .replaceAll("\\s+", " ")
                .trim();
    }

    private void parse(Configuration configuration, String resourceName) throws IOException {
        ClassPathResource resource = new ClassPathResource(resourceName);
        try (var input = resource.getInputStream()) {
            new XMLMapperBuilder(
                            input, configuration, resourceName, configuration.getSqlFragments())
                    .parse();
        }
    }

    private String read(String resourceName) throws IOException {
        try (var input = new ClassPathResource(resourceName).getInputStream()) {
            return new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
    }
}
