package com.ifielsc.ijob.infra.mapper.diagnostics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ifielsc.ijob.contract.diagnostics.DiagnosticCategory;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.ClassPathResource;

import java.time.LocalDateTime;
import java.util.Map;

class DiagnosticsMapperXmlTest {
    @ParameterizedTest
    @ValueSource(strings = {"mysql", "postgresql", "oracle", "sqlserver", "sqlite"})
    void allFourCategoriesAreSafeReadOnlyBoundedStatements(String vendor) throws Exception {
        Configuration configuration = new Configuration();
        configuration.setDatabaseId(vendor);
        String resource = "mapper/diagnostics/DiagnosticsMapper.xml";
        try (var input = new ClassPathResource(resource).getInputStream()) {
            new XMLMapperBuilder(input, configuration, resource, configuration.getSqlFragments())
                    .parse();
        }
        var statement =
                configuration.getMappedStatement(
                        DiagnosticsMapper.class.getName() + ".selectItems");
        assertEquals(5, statement.getTimeout());
        for (var category : DiagnosticCategory.values()) {
            String sql =
                    statement
                            .getBoundSql(
                                    Map.of(
                                            "category",
                                            category,
                                            "now",
                                            LocalDateTime.now(),
                                            "queueCutoff",
                                            LocalDateTime.now()))
                            .getSql()
                            .replaceAll("\\s+", " ");
            assertTrue(sql.trim().startsWith("SELECT"));
            assertTrue(sql.contains("ORDER BY"));
            assertTrue(sql.contains("<= ?"));
            for (String forbidden :
                    new String[] {
                        "claim_token",
                        "lease_owner",
                        "processing_token",
                        "processing_owner",
                        "message",
                        "variables",
                        "last_error",
                        "FOR UPDATE",
                        "COUNT("
                    }) {
                assertFalse(sql.contains(forbidden), sql);
            }
            if (category == DiagnosticCategory.ROOT_DEADLINE_EXCEEDED)
                assertTrue(sql.contains("parent_instance_id IS NULL"));
            if (category == DiagnosticCategory.QUEUE_DELAYED) {
                assertTrue(sql.contains("attempt_count < work_item.max_attempts"));
                assertTrue(sql.contains("expires_at > ?"));
            }
        }
    }
}
