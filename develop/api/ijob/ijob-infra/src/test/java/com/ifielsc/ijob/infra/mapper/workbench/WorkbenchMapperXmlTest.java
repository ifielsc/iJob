// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.infra.mapper.workbench;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ifielsc.ijob.contract.workbench.WorkbenchQuery;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.ClassPathResource;

import java.time.LocalDateTime;
import java.util.Map;

class WorkbenchMapperXmlTest {
    @ParameterizedTest
    @ValueSource(strings = {"mysql", "postgresql", "oracle", "sqlserver", "sqlite"})
    void aggregationUsesBoundedRootWindowAndOperatorPredicate(String vendor) throws Exception {
        Configuration configuration = new Configuration();
        configuration.setDatabaseId(vendor);
        String resource = "mapper/workbench/WorkbenchMapper.xml";
        try (var input = new ClassPathResource(resource).getInputStream()) {
            new XMLMapperBuilder(input, configuration, resource, configuration.getSqlFragments())
                    .parse();
        }
        WorkbenchQuery query = new WorkbenchQuery();
        query.setFrom(LocalDateTime.of(2026, 9, 27, 0, 0));
        query.setTo(query.getFrom().plusDays(1));
        String prefix = WorkbenchMapper.class.getName() + ".";
        String runtimeSql =
                configuration
                        .getMappedStatement(prefix + "selectRuntimeNodes")
                        .getBoundSql(Map.of("history", false))
                        .getSql();
        assertTrue(runtimeSql.contains("NOT EXISTS"));
        assertTrue(runtimeSql.contains("state_key"));
        assertFalse(runtimeSql.contains("host_name"));
        for (String name :
                new String[] {
                    "countQueuedWork",
                    "countClaimedWork",
                    "countExpiredWork",
                    "selectOldestQueuedAt"
                }) {
            var statement = configuration.getMappedStatement(prefix + name);
            assertEquals(5, statement.getTimeout());
            assertTrue(statement.getBoundSql(Map.of("now", query.getTo())).getSql().contains("?"));
        }
        for (String statement : new String[] {"selectWindowStatuses", "selectRecentFailures"}) {
            var mapped = configuration.getMappedStatement(prefix + statement);
            String sql = mapped.getBoundSql(query).getSql().replaceAll("\\s+", " ");
            assertTrue(sql.contains("parent_instance_id IS NULL"));
            assertTrue(sql.contains("create_time >= ?"));
            assertTrue(sql.contains("create_time <= ?"));
            assertEquals(5, mapped.getTimeout());
            assertFalse(sql.contains("input_variables"));
            assertFalse(sql.contains("message"));
        }
        String sql =
                configuration
                        .getMappedStatement(prefix + "countPendingApprovals")
                        .getBoundSql(Map.of("operatorId", "reviewer"))
                        .getSql()
                        .replaceAll("\\s+", " ");
        assertTrue(sql.contains("operator_id = ?"));
        assertTrue(sql.contains("wait_type = 'APPROVAL'"));
        assertTrue(sql.contains("status = 'WAITING'"));
        assertTrue(sql.contains(vendor.equals("sqlserver") ? "COUNT_BIG(*)" : "COUNT(*)"));
    }
}
