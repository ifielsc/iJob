// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.infra.mapper.schedule;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ifielsc.ijob.contract.enums.ScheduleType;
import com.ifielsc.ijob.contract.enums.TargetType;
import com.ifielsc.ijob.contract.schedule.SchedulePageQuery;
import com.ifielsc.ijob.infra.entity.schedule.Schedule;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

class ScheduleMapperXmlTest {
    private static final String MAPPER = "mapper/schedule/ScheduleMapper.xml";

    @Test
    void mapperShouldParseAndExposeManagementStatements() throws IOException {
        Configuration configuration = parseMapper();
        List.of(
                        "selectList",
                        "selectById",
                        "selectByFireClaimToken",
                        "insert",
                        "updateById",
                        "updateEnabledById",
                        "selectIdsForTrigger",
                        "selectDueForTriggerForUpdate",
                        "claimDueForTrigger",
                        "updateFireTimeIfClaimed",
                        "disableByTargetId",
                        "deleteById",
                        "selectIdsForUpdate",
                        "countByCalendarIds",
                        "deleteByIds")
                .forEach(id -> assertTrue(configuration.hasStatement(namespace(id), false)));
    }

    @Test
    void pageQueryShouldBindFiltersAndUseFixedOrdering() throws IOException {
        SchedulePageQuery query = new SchedulePageQuery();
        query.setTargetType(TargetType.TASK);
        query.setTargetId(7L);
        query.setScheduleType(ScheduleType.CRON);
        query.setEnabled(true);

        String sql = boundSql(parseMapper(), "selectList", query);

        assertTrue(sql.contains("FROM ifj_schedule"));
        assertTrue(sql.contains("target_type = ?"));
        assertTrue(sql.contains("target_id = ?"));
        assertTrue(sql.contains("schedule_type = ?"));
        assertTrue(sql.contains("enabled = ?"));
        assertTrue(sql.endsWith("ORDER BY update_time DESC, id DESC"));
        assertFalse(sql.contains("${"));
    }

    @Test
    void writesShouldKeepFireStateBoundariesExplicit() throws IOException {
        Configuration configuration = parseMapper();
        Schedule schedule = schedule();

        String insert = boundSql(configuration, "insert", schedule);
        String update = boundSql(configuration, "updateById", schedule);
        String enabled = boundSql(configuration, "updateEnabledById", schedule);
        String fire =
                boundSql(
                        configuration,
                        "updateFireTimeIfClaimed",
                        Map.of(
                                "schedule",
                                schedule,
                                "expectedNextFireTime",
                                LocalDateTime.now(),
                                "fireClaimToken",
                                "claim-token"));

        assertTrue(insert.contains("last_fire_time, next_fire_time"));
        assertTrue(insert.contains("variables"));
        assertTrue(update.contains("next_fire_time = ?"));
        assertTrue(update.contains("variables = ?"));
        assertFalse(update.contains("last_fire_time ="));
        assertTrue(enabled.contains("enabled = ?"));
        assertTrue(enabled.contains("next_fire_time = ?"));
        assertFalse(enabled.contains("last_fire_time ="));
        assertTrue(fire.contains("last_fire_time = ?"));
        assertTrue(fire.contains("next_fire_time = ?"));
        assertTrue(fire.contains("enabled = TRUE"));
        assertTrue(fire.contains("next_fire_time <=> ?"));
        assertTrue(fire.contains("fire_claim_token = ?"));
    }

    @Test
    void batchDeleteAndCalendarReferenceSqlShouldBeBoundAndEmptySafe() throws IOException {
        Configuration configuration = parseMapper();
        String lock = boundSql(configuration, "selectIdsForUpdate", Map.of("ids", List.of(2L, 1L)));
        String count =
                boundSql(
                        configuration,
                        "countByCalendarIds",
                        Map.of("calendarIds", List.of(3L, 4L)));
        String emptyDelete = boundSql(configuration, "deleteByIds", Map.of("ids", List.of()));

        assertTrue(lock.contains("id IN ( ? , ? )"));
        assertTrue(lock.endsWith("ORDER BY id FOR UPDATE"));
        assertTrue(count.contains("calendar_id IN ( ? , ? )"));
        assertTrue(emptyDelete.contains("WHERE 1 = 0"));
    }

    @Test
    void scannerQueryShouldBeIdOnlyAndBounded() throws IOException {
        String sql = boundSql(parseMapper(), "selectIdsForTrigger", Map.of("limit", 100));

        assertTrue(sql.startsWith("SELECT id FROM ifj_schedule"));
        assertTrue(sql.contains("next_fire_time <= CURRENT_TIMESTAMP(3)"));
        assertTrue(sql.endsWith("LIMIT ?"));
    }

    @Test
    void scannerClaimShouldUseDatabaseTimeAndTokenGuard() throws IOException {
        Configuration configuration = parseMapper();
        String claim =
                boundSql(
                        configuration,
                        "claimDueForTrigger",
                        Map.of("id", 7L, "fireClaimToken", "claim-token"));
        String lock = boundSql(configuration, "selectDueForTriggerForUpdate", Map.of("id", 7L));
        String complete =
                boundSql(
                        configuration,
                        "updateFireTimeIfClaimed",
                        Map.of(
                                "schedule", schedule(),
                                "expectedNextFireTime", LocalDateTime.now(),
                                "fireClaimToken", "claim-token"));

        assertTrue(claim.contains("fire_claim_token = ?"));
        assertTrue(claim.contains("CURRENT_TIMESTAMP(3)"));
        assertTrue(claim.contains("fire_claim_token IS NULL"));
        assertTrue(lock.endsWith("FOR UPDATE SKIP LOCKED"));
        assertTrue(complete.contains("fire_claim_token = NULL"));
        assertTrue(complete.contains("fire_claim_token = ?"));
    }

    @Test
    void mutationsAndScannerRemainScheduleOnlyWithBoundParameters() throws IOException {
        String xml = readMapper();

        assertTrue(xml.contains("ifj_schedule"));
        assertFalse(xml.contains("${"));
        for (String statement : List.of("insert", "updateById", "selectIdsForTrigger")) {
            Object parameters =
                    statement.equals("selectIdsForTrigger") ? Map.of("limit", 100) : schedule();
            String sql = boundSql(parseMapper(), statement, parameters);
            assertFalse(sql.contains("ifj_task"));
            assertFalse(sql.contains("ifj_job"));
        }
    }

    @Test
    void targetNamesUseCurrentIdentitiesAndLiteralContainsFiltersAcrossDialects()
            throws IOException {
        SchedulePageQuery query = new SchedulePageQuery();
        query.setTargetName("  50%_[!  ");
        assertEquals("%50!%!_![!!%", query.getTargetNamePattern());
        for (String vendor : List.of("mysql", "postgresql", "sqlite", "oracle", "sqlserver")) {
            Configuration configuration = new Configuration();
            configuration.setDatabaseId(vendor);
            try (var input = new ClassPathResource(MAPPER).getInputStream()) {
                new XMLMapperBuilder(input, configuration, MAPPER, configuration.getSqlFragments())
                        .parse();
            }
            String sql = boundSql(configuration, "selectList", query);
            assertTrue(sql.contains("t.id = ifj_schedule.target_id"));
            assertTrue(sql.contains("j.id = ifj_schedule.target_id"));
            assertTrue(sql.contains("AS target_name"));
            assertTrue(sql.contains("LIKE ? ESCAPE '!'"));
            assertFalse(sql.contains("50%"));
            assertTrue(
                    boundSql(configuration, "selectById", Map.of("id", 1L))
                            .contains("AS target_name"));
        }
    }

    private Configuration parseMapper() throws IOException {
        Configuration configuration = new Configuration();
        ClassPathResource resource = new ClassPathResource(MAPPER);
        try (var input = resource.getInputStream()) {
            new XMLMapperBuilder(input, configuration, MAPPER, configuration.getSqlFragments())
                    .parse();
        }
        return configuration;
    }

    private String boundSql(Configuration configuration, String id, Object parameter) {
        return configuration
                .getMappedStatement(namespace(id))
                .getBoundSql(parameter)
                .getSql()
                .replaceAll("\\s+", " ")
                .trim();
    }

    private Schedule schedule() {
        Schedule schedule = new Schedule();
        schedule.setId(1L);
        schedule.setTargetType(TargetType.TASK);
        schedule.setTargetId(2L);
        schedule.setScheduleType(ScheduleType.INTERVAL);
        schedule.setIntervalSec(60);
        schedule.setEnabled(true);
        schedule.setCreateTime(LocalDateTime.now());
        schedule.setUpdateTime(LocalDateTime.now());
        return schedule;
    }

    private String readMapper() throws IOException {
        ClassPathResource resource = new ClassPathResource(MAPPER);
        try (var input = resource.getInputStream()) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private String namespace(String id) {
        return "com.ifielsc.ijob.infra.mapper.schedule.ScheduleMapper." + id;
    }
}
