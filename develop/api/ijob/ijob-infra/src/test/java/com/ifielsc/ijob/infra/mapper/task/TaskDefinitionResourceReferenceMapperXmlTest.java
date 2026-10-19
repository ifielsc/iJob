// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.infra.mapper.task;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

class TaskDefinitionResourceReferenceMapperXmlTest {
    private static final String MAPPER = "mapper/task/TaskDefinitionResourceReferenceMapper.xml";

    @Test
    void mapperShouldReadOnlyImmutablePluginSnapshots() throws IOException {
        Configuration configuration = new Configuration();
        ClassPathResource resource = new ClassPathResource(MAPPER);
        try (var input = resource.getInputStream()) {
            new XMLMapperBuilder(input, configuration, MAPPER, configuration.getSqlFragments())
                    .parse();
        }

        String statement =
                TaskDefinitionResourceReferenceMapper.class.getName() + ".selectDefinitionBatch";
        assertTrue(configuration.hasStatement(statement, false));
        String sql =
                configuration
                        .getMappedStatement(statement)
                        .getBoundSql(java.util.Map.of("afterId", 0L, "upperId", 1000L))
                        .getSql()
                        .replaceAll("\\s+", " ")
                        .trim();
        assertTrue(sql.startsWith("SELECT id, task_id, plugin_type, plugin_config"));
        assertTrue(sql.contains("FROM ifj_task_definition"));
        assertTrue(sql.contains("WHERE id > ? AND id <= ?"));
        assertTrue(
                configuration.hasStatement(
                        TaskDefinitionResourceReferenceMapper.class.getName()
                                + ".selectMaxDefinitionId",
                        false));
        assertTrue(sql.endsWith("ORDER BY id"));

        try (var input = resource.getInputStream()) {
            String xml = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            assertFalse(xml.contains("${"));
            assertFalse(xml.contains("UPDATE "));
            assertFalse(xml.contains("DELETE "));
        }
    }
}
