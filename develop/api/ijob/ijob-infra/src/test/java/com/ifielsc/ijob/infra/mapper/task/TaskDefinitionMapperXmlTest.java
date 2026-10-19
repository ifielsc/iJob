package com.ifielsc.ijob.infra.mapper.task;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ifielsc.ijob.contract.enums.PluginType;
import com.ifielsc.ijob.infra.entity.task.TaskDefinition;
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

class TaskDefinitionMapperXmlTest {
    private static final String MAPPER_RESOURCE = "mapper/task/TaskDefinitionMapper.xml";
    private static final List<String> ENTITY_FIELDS =
            List.of(
                    "id",
                    "taskId",
                    "revision",
                    "name",
                    "pluginType",
                    "pluginConfig",
                    "retryTimes",
                    "retryInterval",
                    "timeout",
                    "enabled",
                    "description",
                    "createTime");
    private static final Pattern TABLE_REFERENCE = Pattern.compile("\\bifj_[a-z0-9_]+\\b");

    @Test
    void entityShouldMatchImmutableV4ColumnsWithoutBaseEntity() throws NoSuchFieldException {
        List<String> actualFields =
                Arrays.stream(TaskDefinition.class.getDeclaredFields())
                        .filter(field -> !field.isSynthetic())
                        .map(Field::getName)
                        .toList();

        assertEquals(ENTITY_FIELDS, actualFields);
        assertEquals(Object.class, TaskDefinition.class.getSuperclass());
        assertFalse(actualFields.contains("updateTime"));
        assertTrue(TaskDefinition.class.getDeclaredField("id").isAnnotationPresent(AutoId.class));
        assertEquals(
                PluginType.class, TaskDefinition.class.getDeclaredField("pluginType").getType());
    }

    @Test
    void entityShouldNotGenerateFieldBasedToString() {
        TaskDefinition definition = definition();
        definition.setPluginConfig("plugin-secret-value");

        assertFalse(definition.toString().contains("plugin-secret-value"));
        assertFalse(definition.toString().contains("revision=1"));
    }

    @Test
    void mapperInterfaceShouldExposeOnlyFrozenReadAndInsertMethods() {
        assertEquals(
                Set.of("selectById", "selectListByTaskId", "insert"),
                Arrays.stream(TaskDefinitionMapper.class.getDeclaredMethods())
                        .map(java.lang.reflect.Method::getName)
                        .collect(java.util.stream.Collectors.toSet()));
    }

    @Test
    void mapperShouldExposeExactReadAndInsertBoundSql() throws IOException {
        Configuration configuration = parseMapper();

        assertStatements(
                configuration,
                Map.of(
                        "selectById", SqlCommandType.SELECT,
                        "selectListByTaskId", SqlCommandType.SELECT,
                        "insert", SqlCommandType.INSERT));
        assertEquals(
                "SELECT id, task_id, revision, name, plugin_type, "
                        + "plugin_config, retry_times, retry_interval, "
                        + "timeout, enabled, description, "
                        + "create_time FROM ifj_task_definition "
                        + "WHERE id = ?",
                boundSql(configuration, "selectById", 1L));
        assertEquals(
                "SELECT id, task_id, revision, name, plugin_type, "
                        + "plugin_config, retry_times, retry_interval, "
                        + "timeout, enabled, description, "
                        + "create_time FROM ifj_task_definition "
                        + "WHERE task_id = ? "
                        + "ORDER BY revision ASC, id ASC",
                boundSql(configuration, "selectListByTaskId", 10L));

        String insertSql = boundSql(configuration, "insert", definition());
        assertTrue(insertSql.startsWith("INSERT INTO ifj_task_definition"));
        assertEquals(12, insertSql.chars().filter(value -> value == '?').count());
    }

    @Test
    void mapperShouldBindPluginEnumByJavaType() throws IOException {
        Configuration configuration = parseMapper();
        var parameterMappings =
                configuration
                        .getMappedStatement(namespace("insert"))
                        .getBoundSql(definition())
                        .getParameterMappings();

        for (String property : List.of("pluginType")) {
            var mapping =
                    parameterMappings.stream()
                            .filter(candidate -> property.equals(candidate.getProperty()))
                            .findFirst()
                            .orElseThrow();
            assertEquals(EnumTypeHandler.class, mapping.getTypeHandler().getClass());
        }

        String xml = readMapper();
        assertTrue(xml.contains(PluginType.class.getName()));
        assertEquals(0, count(xml, "org.apache.ibatis.type.EnumTypeHandler"));
    }

    @Test
    void mapperShouldBeSingleTableAndWithoutMutableStatements() throws IOException {
        String xml = readMapper();
        String lowerCase = xml.toLowerCase(java.util.Locale.ROOT);
        Set<String> tables = new LinkedHashSet<>();
        Matcher matcher = TABLE_REFERENCE.matcher(lowerCase);
        while (matcher.find()) {
            tables.add(matcher.group());
        }

        assertEquals(Set.of("ifj_task_definition"), tables);
        assertEquals(2, count(lowerCase, "<select "));
        assertEquals(1, count(lowerCase, "<insert "));
        assertFalse(lowerCase.contains("<update "));
        assertFalse(lowerCase.contains("<delete "));
        assertFalse(lowerCase.contains(" join "));
        assertFalse(lowerCase.contains("select *"));
        assertFalse(lowerCase.contains("${"));
        assertFalse(lowerCase.contains("update_time"));
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

    private TaskDefinition definition() {
        TaskDefinition definition = new TaskDefinition();
        definition.setId(1L);
        definition.setTaskId(10L);
        definition.setRevision(1L);
        definition.setName("task");
        definition.setPluginType(PluginType.SCRIPT);
        definition.setPluginConfig("{}");
        definition.setRetryTimes(2);
        definition.setRetryInterval(10);
        definition.setTimeout(60);
        definition.setEnabled(true);
        definition.setDescription("description");
        definition.setCreateTime(LocalDateTime.now());
        return definition;
    }

    private String namespace(String id) {
        return TaskDefinitionMapper.class.getName() + "." + id;
    }

    private int count(String value, String token) {
        return value.split(Pattern.quote(token), -1).length - 1;
    }

    private String normalizedSql(String sql) {
        return sql.replaceAll("\\s+", " ").trim();
    }
}
