// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.infra.mapper.task;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ifielsc.ijob.contract.enums.PluginType;
import com.ifielsc.ijob.contract.task.TaskPageQuery;
import com.ifielsc.ijob.infra.entity.task.Task;
import com.ifielsc.ijob.infra.mybatis.AutoIdInterceptor;

import org.apache.ibatis.builder.StaticSqlSource;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

class TaskMapperXmlTest {
    private static final String TASK_MAPPER = "mapper/task/TaskMapper.xml";
    private static final String SCHEDULE_MAPPER = "mapper/schedule/ScheduleMapper.xml";

    @Test
    void groupCandidatesShouldUseCurrentNonRetiredDefinitionsForEveryDialect() throws IOException {
        for (String dialect : List.of("mysql", "postgresql", "sqlite", "oracle", "sqlserver")) {
            Configuration configuration = new Configuration();
            configuration.setDatabaseId(dialect);
            parseMapper(configuration, TASK_MAPPER);
            String sql =
                    normalizedSql(
                            configuration
                                    .getMappedStatement(
                                            "com.ifielsc.ijob.infra.mapper.task.TaskMapper.selectGroupCodes")
                                    .getBoundSql(java.util.Map.of("pattern", "finance!_%"))
                                    .getSql());
            assertTrue(sql.contains("SELECT DISTINCT dg.group_code"));
            assertTrue(sql.contains("dg.definition_id = t.current_task_definition_id"));
            assertTrue(
                    sql.contains(
                            "t.retired = "
                                    + (List.of("oracle", "sqlserver").contains(dialect)
                                            ? "0"
                                            : "FALSE")));
            assertTrue(sql.contains("dg.group_code LIKE ? ESCAPE '!'"));
            assertTrue(sql.endsWith("ORDER BY dg.group_code"));
        }
    }

    @Test
    void mapperXmlShouldParseAndExposeRequiredStatements() throws IOException {
        Configuration configuration = parseMappers();

        assertEquals(
                List.of("selectById", "selectDtoList", "selectGroupCodes"),
                Arrays.stream(TaskMapper.class.getDeclaredMethods())
                        .map(java.lang.reflect.Method::getName)
                        .sorted()
                        .toList());

        List<String> statements =
                List.of(
                        "com.ifielsc.ijob.infra.mapper.task.TaskMapper.selectDtoList",
                        "com.ifielsc.ijob.infra.mapper.task.TaskMapper.selectById",
                        "com.ifielsc.ijob.infra.mapper.schedule.ScheduleMapper.selectListByTargetId",
                        "com.ifielsc.ijob.infra.mapper.schedule.ScheduleMapper.selectIdsForTrigger",
                        "com.ifielsc.ijob.infra.mapper.schedule.ScheduleMapper.insert",
                        "com.ifielsc.ijob.infra.mapper.schedule.ScheduleMapper.updateFireTimeIfClaimed",
                        "com.ifielsc.ijob.infra.mapper.schedule.ScheduleMapper.disableByTargetId");

        statements.forEach(
                statement ->
                        assertTrue(
                                configuration.hasStatement(statement, false),
                                () -> "Missing mapped statement: " + statement));
    }

    @Test
    void taskPageSqlShouldUseBoundFiltersAndFixedOrdering() throws IOException {
        Configuration configuration = parseMappers();
        TaskPageQuery query = new TaskPageQuery();
        query.setName("demo");
        query.setPluginType(PluginType.EVENT);
        query.setEnabled(true);
        query.setGroup("gpu");

        String sql =
                normalizedSql(
                        configuration
                                .getMappedStatement(
                                        "com.ifielsc.ijob.infra.mapper.task.TaskMapper.selectDtoList")
                                .getBoundSql(query)
                                .getSql());

        assertTrue(sql.contains("FROM ifj_task"));
        assertTrue(sql.contains("name LIKE CONCAT('%', ?, '%')"));
        assertTrue(sql.contains("plugin_type = ?"));
        assertTrue(sql.contains("enabled = ?"));
        assertTrue(sql.contains("FROM ifj_task_definition_group definition_group"));
        assertTrue(sql.contains("definition_group.definition_id = current_task_definition_id"));
        assertTrue(sql.contains("definition_group.group_code = ?"));
        assertTrue(sql.contains("retired = FALSE"));
        assertTrue(sql.contains("current_task_definition_id IS NOT NULL"));
        assertTrue(sql.contains("current_revision IS NOT NULL"));
        assertTrue(sql.endsWith("ORDER BY update_time DESC, id DESC"));
        String xml = readResource(TASK_MAPPER);
        assertFalse(xml.contains("${"));
        assertFalse(xml.contains("<insert "));
        assertFalse(xml.contains("<update "));
        assertFalse(xml.contains("<delete "));
    }

    @Test
    void activeReadShouldFilterRetiredIdentity() throws IOException {
        Configuration configuration = parseMappers();
        String activeSql =
                normalizedSql(
                        configuration
                                .getMappedStatement(
                                        "com.ifielsc.ijob.infra.mapper.task.TaskMapper.selectById")
                                .getBoundSql(7L)
                                .getSql());
        assertTrue(activeSql.contains("retired = FALSE"));
        assertTrue(activeSql.contains("current_task_definition_id IS NOT NULL"));
        assertFalse(readResource(SCHEDULE_MAPPER).contains("${"));
    }

    @Test
    void autoIdInterceptorShouldBeRegisteredAndFillInsertId() throws Exception {
        assertNotNull(AutoIdInterceptor.class.getAnnotation(Component.class));

        Configuration configuration = new Configuration();
        MappedStatement statement =
                new MappedStatement.Builder(
                                configuration,
                                "test.insert",
                                new StaticSqlSource(
                                        configuration, "INSERT INTO ifj_task (id) VALUES (?)"),
                                SqlCommandType.INSERT)
                        .build();
        Executor target =
                (Executor)
                        Proxy.newProxyInstance(
                                Executor.class.getClassLoader(),
                                new Class<?>[] {Executor.class},
                                (proxy, method, args) ->
                                        method.getName().equals("update") ? 1 : null);
        Executor executor =
                (Executor)
                        new AutoIdInterceptor(
                                        namespace -> {
                                            assertEquals(
                                                    com.ifielsc.ijob.infra.coordination.id
                                                            .IdNamespace.TASK,
                                                    namespace);
                                            return 42L;
                                        })
                                .plugin(target);
        Task task = new Task();

        assertEquals(1, executor.update(statement, task));
        assertEquals(42L, task.getId());
    }

    private Configuration parseMappers() throws IOException {
        Configuration configuration = new Configuration();
        configuration.setMapUnderscoreToCamelCase(true);
        parseMapper(configuration, TASK_MAPPER);
        parseMapper(configuration, SCHEDULE_MAPPER);
        return configuration;
    }

    private void parseMapper(Configuration configuration, String resourceName) throws IOException {
        ClassPathResource resource = new ClassPathResource(resourceName);
        try (var input = resource.getInputStream()) {
            XMLMapperBuilder builder =
                    new XMLMapperBuilder(
                            input, configuration, resourceName, configuration.getSqlFragments());
            builder.parse();
        }
    }

    private String readResource(String resourceName) throws IOException {
        ClassPathResource resource = new ClassPathResource(resourceName);
        try (var input = resource.getInputStream()) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private String normalizedSql(String sql) {
        return sql.replaceAll("\\s+", " ").trim();
    }
}
