package com.ifelio.ijob.infra.mapper.task;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

class TaskReferenceMapperXmlTest {
    private static final String MAPPER_RESOURCE = "mapper/task/TaskReferenceMapper.xml";

    @Test
    void mapperXmlShouldParseAndExposeReadStatement() throws IOException {
        Configuration configuration = parseMapper();

        assertTrue(configuration.hasStatement(namespace("selectExistingIds"), false));
    }

    @Test
    void queryShouldBindEveryIdAndUseFixedOrdering() throws IOException {
        String sql =
                parseMapper()
                        .getMappedStatement(namespace("selectExistingIds"))
                        .getBoundSql(Map.of("ids", List.of(20L, 10L)))
                        .getSql()
                        .replaceAll("\\s+", " ")
                        .trim();

        assertEquals(
                "SELECT id FROM ifj_task WHERE id IN ( ? , ? ) "
                        + "AND retired = FALSE "
                        + "AND current_task_definition_id IS NOT NULL "
                        + "AND current_revision IS NOT NULL "
                        + "ORDER BY id ASC",
                sql);
    }

    @Test
    void mapperShouldBeSingleTableReadOnlyWithoutTextSubstitution() throws IOException {
        String xml = readMapper();

        assertEquals(2, count(xml, "<select "));
        assertFalse(xml.contains("<insert "));
        assertFalse(xml.contains("<update "));
        assertFalse(xml.contains("<delete "));
        assertFalse(xml.contains(" JOIN "));
        assertFalse(xml.contains("${"));
        assertTrue(xml.contains("ifj_task"));
        assertFalse(xml.contains("ifj_job"));
    }

    private Configuration parseMapper() throws IOException {
        Configuration configuration = new Configuration();
        ClassPathResource resource = new ClassPathResource(MAPPER_RESOURCE);
        try (var input = resource.getInputStream()) {
            XMLMapperBuilder builder =
                    new XMLMapperBuilder(
                            input, configuration, MAPPER_RESOURCE, configuration.getSqlFragments());
            builder.parse();
        }
        return configuration;
    }

    private String readMapper() throws IOException {
        ClassPathResource resource = new ClassPathResource(MAPPER_RESOURCE);
        try (var input = resource.getInputStream()) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private int count(String value, String token) {
        return value.split(java.util.regex.Pattern.quote(token), -1).length - 1;
    }

    private String namespace(String id) {
        return "com.ifelio.ijob.infra.mapper.task.TaskReferenceMapper." + id;
    }
}
