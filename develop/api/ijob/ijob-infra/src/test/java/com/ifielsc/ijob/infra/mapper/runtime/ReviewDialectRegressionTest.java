package com.ifielsc.ijob.infra.mapper.runtime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ifielsc.ijob.infra.coordination.work.ExecutionFencePermit;
import com.ifielsc.ijob.infra.entity.runtime.EtlCheckpoint;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

class ReviewDialectRegressionTest {
    @Test
    void everyDialectClearsPermitWaitDiagnosticsWhenStarting() throws Exception {
        for (String vendor : List.of("mysql", "postgresql", "sqlite", "oracle", "sqlserver")) {
            String sql =
                    sql(
                            vendor,
                            "runtime/InstanceMapper",
                            "markRunningIfWaiting",
                            Map.of(
                                    "id",
                                    1L,
                                    "startTime",
                                    LocalDateTime.of(2026, 10, 13, 0, 0),
                                    "permit",
                                    ExecutionFencePermit.unfenced()));
            for (String column :
                    List.of(
                            "message",
                            "error_code",
                            "message_key",
                            "message_args",
                            "wait_reason",
                            "permit_wait_deadline")) {
                assertTrue(sql.contains(column + " = NULL"), vendor + ": " + column);
            }
        }
    }

    @Test
    void oracleCheckpointCreationHonorsEveryExecutionPermitMode() throws Exception {
        var checkpoint = new EtlCheckpoint();
        checkpoint.setTaskId(1L);
        checkpoint.setLastInstanceId(2L);
        for (var mode : ExecutionFencePermit.Mode.values()) {
            String sql =
                    sql(
                            "oracle",
                            "runtime/EtlCheckpointMapper",
                            "insertIfAbsent",
                            Map.of(
                                    "checkpoint",
                                    checkpoint,
                                    "permit",
                                    new ExecutionFencePermit(mode, "claim", 7L)));
            if (mode == ExecutionFencePermit.Mode.CLAIMED) {
                assertTrue(sql.contains("instance_record.execution_fence = ?"), sql);
                assertTrue(sql.contains("work_item.claim_token = ?"), sql);
                assertTrue(sql.contains("work_item.lease_deadline >"), sql);
            } else if (mode == ExecutionFencePermit.Mode.SUSPENDED) {
                assertTrue(sql.contains("work_item.status = 'SUSPENDED'"), sql);
                assertTrue(
                        sql.contains("work_item.fence_token = instance_record.execution_fence"),
                        sql);
            } else assertFalse(sql.contains("work_item"), sql);
        }
    }

    @Test
    void notificationReferenceLockIsNotLostInVendorOverrides() throws Exception {
        assertTrue(
                sql(
                                "oracle",
                                "notify/NotifyConfigMapper",
                                "selectIdsForUpdate",
                                Map.of("ids", List.of(1L)))
                        .contains("FOR UPDATE"));
        String sql =
                sql(
                        "sqlserver",
                        "notify/NotifyConfigMapper",
                        "selectIdsForUpdate",
                        Map.of("ids", List.of(1L)));
        assertTrue(sql.contains("UPDLOCK"), sql);
        assertTrue(sql.contains("HOLDLOCK"), sql);
    }

    @Test
    void postgresConflictDoesNotAbortTheReceiptTransaction() throws Exception {
        String sql =
                sql(
                        "postgresql",
                        "runtime/EventRecordMapper",
                        "insert",
                        new com.ifielsc.ijob.infra.entity.runtime.EventRecord());
        assertTrue(sql.contains("ON CONFLICT (source_key, idempotency_key) DO NOTHING"), sql);
    }

    private String sql(String vendor, String mapper, String statement, Object parameters)
            throws Exception {
        var configuration = new Configuration();
        configuration.setDatabaseId(vendor);
        String resource = "mapper/" + mapper + ".xml";
        try (var input = new ClassPathResource(resource).getInputStream()) {
            new XMLMapperBuilder(input, configuration, resource, configuration.getSqlFragments())
                    .parse();
        }
        return configuration
                .getMappedStatement(
                        "com.ifielsc.ijob.infra.mapper."
                                + mapper.replace('/', '.')
                                + "."
                                + statement)
                .getBoundSql(parameters)
                .getSql()
                .replaceAll("\\s+", " ")
                .trim();
    }
}
