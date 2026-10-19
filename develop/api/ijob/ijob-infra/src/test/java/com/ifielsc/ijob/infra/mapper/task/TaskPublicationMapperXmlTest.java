// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.infra.mapper.task;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ifielsc.ijob.contract.enums.PluginType;
import com.ifielsc.ijob.infra.mybatis.AutoId;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.type.EnumTypeHandler;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

class TaskPublicationMapperXmlTest {
    private static final String MAPPER_RESOURCE = "mapper/task/TaskPublicationMapper.xml";
    private static final Pattern TABLE_REFERENCE = Pattern.compile("\\bifj_[a-z0-9_]+\\b");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 15, 1, 0);

    @Test
    void dedicatedTypesShouldMatchFrozenFieldsAndHidePluginConfig() throws NoSuchFieldException {
        assertFields(
                TaskPublicationIdentityRow.class,
                List.of(
                        "id",
                        "currentTaskDefinitionId",
                        "currentRevision",
                        "retired",
                        "retiredTime",
                        "createTime",
                        "updateTime"));
        assertFields(
                TaskPublicationIdentityInsert.class,
                List.of(
                        "id",
                        "name",
                        "pluginType",
                        "pluginConfig",
                        "retryTimes",
                        "retryInterval",
                        "timeout",
                        "enabled",
                        "description",
                        "createTime",
                        "updateTime"));
        assertFields(
                TaskCurrentDefinitionInitialization.class,
                List.of("taskId", "definitionId", "updateTime"));
        assertFields(
                TaskCurrentDefinitionAdvance.class,
                List.of(
                        "taskId",
                        "expectedDefinitionId",
                        "expectedRevision",
                        "definitionId",
                        "nextRevision",
                        "name",
                        "pluginType",
                        "pluginConfig",
                        "retryTimes",
                        "retryInterval",
                        "timeout",
                        "enabled",
                        "description",
                        "updateTime"));
        assertFields(
                TaskIdentityRetirement.class,
                List.of("taskId", "expectedDefinitionId", "expectedRevision", "retiredTime"));
        assertEquals(
                PluginType.class,
                TaskPublicationIdentityInsert.class.getDeclaredField("pluginType").getType());
        assertEquals(
                PluginType.class,
                TaskCurrentDefinitionAdvance.class.getDeclaredField("pluginType").getType());
        assertTrue(
                TaskPublicationIdentityInsert.class
                        .getDeclaredField("id")
                        .isAnnotationPresent(AutoId.class));

        TaskPublicationIdentityInsert insert = identityInsert();
        TaskCurrentDefinitionAdvance advance = advance();
        insert.setPluginConfig("insert-secret");
        advance.setPluginConfig("advance-secret");
        assertFalse(insert.toString().contains("insert-secret"));
        assertFalse(advance.toString().contains("advance-secret"));
    }

    @Test
    void mapperShouldExposeOnlyFiveFrozenStatements() throws IOException {
        assertEquals(
                Set.of(
                        "selectIdentityForUpdate",
                        "insertIdentity",
                        "initializeCurrentDefinition",
                        "advanceCurrentDefinition",
                        "retireIdentity"),
                Arrays.stream(TaskPublicationMapper.class.getDeclaredMethods())
                        .map(java.lang.reflect.Method::getName)
                        .collect(java.util.stream.Collectors.toSet()));

        Configuration configuration = parseMapper();
        assertStatements(
                configuration,
                Map.of(
                        "selectIdentityForUpdate", SqlCommandType.SELECT,
                        "insertIdentity", SqlCommandType.INSERT,
                        "initializeCurrentDefinition", SqlCommandType.UPDATE,
                        "advanceCurrentDefinition", SqlCommandType.UPDATE,
                        "retireIdentity", SqlCommandType.UPDATE));
        assertEquals(
                TaskPublicationIdentityRow.class,
                configuration
                        .getMappedStatement(namespace("selectIdentityForUpdate"))
                        .getResultMaps()
                        .getFirst()
                        .getType());
        assertParameterType(configuration, "insertIdentity", TaskPublicationIdentityInsert.class);
        assertParameterType(
                configuration,
                "initializeCurrentDefinition",
                TaskCurrentDefinitionInitialization.class);
        assertParameterType(
                configuration, "advanceCurrentDefinition", TaskCurrentDefinitionAdvance.class);
        assertParameterType(configuration, "retireIdentity", TaskIdentityRetirement.class);
    }

    @Test
    void identityLockShouldReadFixedIntegrityColumnsForUpdate() throws IOException {
        String sql = boundSql(parseMapper(), "selectIdentityForUpdate", 10L);

        assertEquals(
                "SELECT id, current_task_definition_id, current_revision, "
                        + "retired, retired_time, create_time, update_time "
                        + "FROM ifj_task WHERE id = ? FOR UPDATE",
                sql);
        assertEquals(1, parameterCount(sql));
    }

    @Test
    void identityInsertShouldWriteFixedMirrorsAndInitialState() throws IOException {
        Configuration configuration = parseMapper();
        TaskPublicationIdentityInsert insert = identityInsert();
        String sql = boundSql(configuration, "insertIdentity", insert);

        assertEquals(
                "INSERT INTO ifj_task ( id, name, plugin_type, "
                        + "plugin_config, retry_times, retry_interval, "
                        + "timeout, enabled, description, "
                        + "current_task_definition_id, current_revision, "
                        + "retired, retired_time, create_time, update_time ) "
                        + "VALUES ( ?, ?, ?, ?, ?, ?, ?, ?, ?, "
                        + "NULL, NULL, FALSE, NULL, ?, ? )",
                sql);
        assertEquals(11, parameterCount(sql));
        assertEnumHandlers(configuration, "insertIdentity", insert, List.of("pluginType"));
    }

    @Test
    void initializationShouldRequireDoubleNullAndActiveIdentity() throws IOException {
        String sql = boundSql(parseMapper(), "initializeCurrentDefinition", initialization());

        assertEquals(
                "UPDATE ifj_task SET current_task_definition_id = ?, "
                        + "current_revision = 1, update_time = ? "
                        + "WHERE id = ? "
                        + "AND current_task_definition_id IS NULL "
                        + "AND current_revision IS NULL "
                        + "AND retired = FALSE AND retired_time IS NULL",
                sql);
        assertEquals(3, parameterCount(sql));
    }

    @Test
    void advanceShouldSwitchPointerAndExecutionMirrorsWithCas() throws IOException {
        Configuration configuration = parseMapper();
        TaskCurrentDefinitionAdvance advance = advance();
        String sql = boundSql(configuration, "advanceCurrentDefinition", advance);

        assertEquals(
                "UPDATE ifj_task SET current_task_definition_id = ?, "
                        + "current_revision = ?, name = ?, plugin_type = ?, "
                        + "plugin_config = ?, retry_times = ?, "
                        + "retry_interval = ?, timeout = ?, enabled = ?, "
                        + "description = ?, "
                        + "update_time = ? WHERE id = ? "
                        + "AND current_task_definition_id = ? "
                        + "AND current_revision = ? "
                        + "AND retired = FALSE AND retired_time IS NULL",
                sql);
        assertEquals(14, parameterCount(sql));
        assertEnumHandlers(
                configuration, "advanceCurrentDefinition", advance, List.of("pluginType"));
    }

    @Test
    void retirementShouldBeOneWayAndUseNullSafePointerCas() throws IOException {
        String sql = boundSql(parseMapper(), "retireIdentity", retirement());

        assertEquals(
                "UPDATE ifj_task SET retired = TRUE, retired_time = ?, "
                        + "update_time = ? WHERE id = ? "
                        + "AND retired = FALSE AND retired_time IS NULL "
                        + "AND current_task_definition_id <=> ? "
                        + "AND current_revision <=> ?",
                sql);
        assertEquals(5, parameterCount(sql));
    }

    @Test
    void xmlShouldBeSingleTableStaticAndWithoutLegacyMutation() throws IOException {
        String xml = readMapper();
        String lowerCase = xml.toLowerCase(java.util.Locale.ROOT);
        Set<String> tables = new LinkedHashSet<>();
        Matcher matcher = TABLE_REFERENCE.matcher(lowerCase);
        while (matcher.find()) {
            tables.add(matcher.group());
        }

        assertEquals(Set.of("ifj_task"), tables);
        assertEquals(4, count(lowerCase, "<select "));
        assertEquals(2, count(lowerCase, "<insert "));
        assertEquals(9, count(lowerCase, "<update "));
        assertEquals(0, count(xml, "org.apache.ibatis.type.EnumTypeHandler"));
        assertFalse(lowerCase.contains("<delete "));
        assertFalse(lowerCase.contains(" join "));
        assertFalse(lowerCase.contains("select *"));
        assertFalse(lowerCase.contains("${"));
        assertFalse(lowerCase.contains("<if "));
        assertFalse(lowerCase.contains("<foreach "));
        assertFalse(lowerCase.contains("<choose "));
        assertFalse(lowerCase.contains("<set "));
        assertFalse(lowerCase.contains("set retired = FALSE"));
        assertFalse(lowerCase.contains("ifj_task_definition"));
        assertFalse(xml.contains("com.ifielsc.ijob.infra.entity.task.Task"));
    }

    private Configuration parseMapper() throws IOException {
        Configuration configuration = new Configuration();
        configuration.setMapUnderscoreToCamelCase(true);
        ClassPathResource resource = new ClassPathResource(MAPPER_RESOURCE);
        try (var input = resource.getInputStream()) {
            XMLMapperBuilder builder =
                    new XMLMapperBuilder(
                            input, configuration, MAPPER_RESOURCE, configuration.getSqlFragments());
            builder.parse();
        }
        return configuration;
    }

    private void assertStatements(
            Configuration configuration, Map<String, SqlCommandType> expected) {
        expected.forEach(
                (id, commandType) -> {
                    String statement = namespace(id);
                    assertTrue(configuration.hasStatement(statement, false));
                    assertEquals(
                            commandType,
                            configuration.getMappedStatement(statement).getSqlCommandType());
                });
    }

    private void assertParameterType(
            Configuration configuration, String statementId, Class<?> expectedType) {
        assertEquals(
                expectedType,
                configuration
                        .getMappedStatement(namespace(statementId))
                        .getParameterMap()
                        .getType());
    }

    private void assertEnumHandlers(
            Configuration configuration,
            String statementId,
            Object parameter,
            List<String> properties) {
        var mappings =
                configuration
                        .getMappedStatement(namespace(statementId))
                        .getBoundSql(parameter)
                        .getParameterMappings();
        for (String property : properties) {
            var mapping =
                    mappings.stream()
                            .filter(candidate -> property.equals(candidate.getProperty()))
                            .findFirst()
                            .orElseThrow();
            assertEquals(EnumTypeHandler.class, mapping.getTypeHandler().getClass());
        }
    }

    private void assertFields(Class<?> type, List<String> expected) {
        List<String> actual =
                Arrays.stream(type.getDeclaredFields())
                        .filter(field -> !field.isSynthetic())
                        .map(Field::getName)
                        .toList();
        assertEquals(expected, actual, type::getSimpleName);
        assertEquals(Object.class, type.getSuperclass());
    }

    private String boundSql(Configuration configuration, String statementId, Object parameter) {
        return normalizedSql(
                configuration
                        .getMappedStatement(namespace(statementId))
                        .getBoundSql(parameter)
                        .getSql());
    }

    private String readMapper() throws IOException {
        ClassPathResource resource = new ClassPathResource(MAPPER_RESOURCE);
        try (var input = resource.getInputStream()) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private TaskPublicationIdentityInsert identityInsert() {
        TaskPublicationIdentityInsert insert = new TaskPublicationIdentityInsert();
        insert.setId(10L);
        insert.setName("task");
        insert.setPluginType(PluginType.SCRIPT);
        insert.setPluginConfig("{}");
        insert.setRetryTimes(2);
        insert.setRetryInterval(10);
        insert.setTimeout(60);
        insert.setEnabled(true);
        insert.setDescription("description");
        insert.setCreateTime(NOW);
        insert.setUpdateTime(NOW);
        return insert;
    }

    private TaskCurrentDefinitionInitialization initialization() {
        TaskCurrentDefinitionInitialization initialization =
                new TaskCurrentDefinitionInitialization();
        initialization.setTaskId(10L);
        initialization.setDefinitionId(100L);
        initialization.setUpdateTime(NOW);
        return initialization;
    }

    private TaskCurrentDefinitionAdvance advance() {
        TaskCurrentDefinitionAdvance advance = new TaskCurrentDefinitionAdvance();
        advance.setTaskId(10L);
        advance.setExpectedDefinitionId(100L);
        advance.setExpectedRevision(1L);
        advance.setDefinitionId(101L);
        advance.setNextRevision(2L);
        advance.setName("task");
        advance.setPluginType(PluginType.SCRIPT);
        advance.setPluginConfig("{}");
        advance.setRetryTimes(2);
        advance.setRetryInterval(10);
        advance.setTimeout(60);
        advance.setEnabled(true);
        advance.setDescription("description");
        advance.setUpdateTime(NOW);
        return advance;
    }

    private TaskIdentityRetirement retirement() {
        TaskIdentityRetirement retirement = new TaskIdentityRetirement();
        retirement.setTaskId(10L);
        retirement.setExpectedDefinitionId(100L);
        retirement.setExpectedRevision(1L);
        retirement.setRetiredTime(NOW);
        return retirement;
    }

    private int parameterCount(String sql) {
        return (int) sql.chars().filter(value -> value == '?').count();
    }

    private int count(String value, String token) {
        return value.split(Pattern.quote(token), -1).length - 1;
    }

    private String namespace(String id) {
        return TaskPublicationMapper.class.getName() + "." + id;
    }

    private String normalizedSql(String sql) {
        return sql.replaceAll("\\s+", " ").replace("FALSE ,", "FALSE,").trim();
    }
}
