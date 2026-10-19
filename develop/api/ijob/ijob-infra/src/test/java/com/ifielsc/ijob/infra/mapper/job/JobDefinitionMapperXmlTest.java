// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.infra.mapper.job;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ifielsc.ijob.contract.enums.ExecuteCondition;
import com.ifielsc.ijob.contract.enums.FailStrategy;
import com.ifielsc.ijob.contract.enums.GatewayMode;
import com.ifielsc.ijob.contract.enums.JobNodeKind;
import com.ifielsc.ijob.contract.enums.TargetType;
import com.ifielsc.ijob.infra.entity.job.JobDefinition;
import com.ifielsc.ijob.infra.entity.job.JobDefinitionEdge;
import com.ifielsc.ijob.infra.entity.job.JobDefinitionNode;
import com.ifielsc.ijob.infra.entity.job.JobDefinitionNodePolicy;
import com.ifielsc.ijob.infra.mybatis.AutoId;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

class JobDefinitionMapperXmlTest {
    private static final String DEFINITION_MAPPER = "mapper/job/JobDefinitionMapper.xml";
    private static final String NODE_MAPPER = "mapper/job/JobDefinitionNodeMapper.xml";
    private static final String POLICY_MAPPER = "mapper/job/JobDefinitionNodePolicyMapper.xml";
    private static final String EDGE_MAPPER = "mapper/job/JobDefinitionEdgeMapper.xml";
    private static final Map<Class<?>, Set<String>> ENTITY_FIELDS =
            Map.of(
                    JobDefinition.class,
                    Set.of(
                            "id",
                            "jobId",
                            "revision",
                            "name",
                            "timeout",
                            "enabled",
                            "description",
                            "layout",
                            "createTime"),
                    JobDefinitionNode.class,
                    Set.of(
                            "id",
                            "definitionId",
                            "nodeKey",
                            "name",
                            "nodeKind",
                            "gatewayMode",
                            "nodeType",
                            "refId",
                            "refDefinitionId",
                            "refTaskDefinitionId",
                            "createTime"),
                    JobDefinitionNodePolicy.class,
                    Set.of(
                            "id",
                            "definitionId",
                            "nodeId",
                            "failStrategy",
                            "executeCondition",
                            "createTime"),
                    JobDefinitionEdge.class,
                    Set.of(
                            "id",
                            "definitionId",
                            "fromNodeId",
                            "toNodeId",
                            "routeKey",
                            "routePriority",
                            "defaultRoute",
                            "conditionVersion",
                            "conditionPayload",
                            "createTime"));
    private static final Map<Class<?>, Set<String>> MAPPER_METHODS =
            Map.of(
                    JobDefinitionMapper.class,
                    Set.of("selectById", "selectListByJobId", "insert"),
                    JobDefinitionNodeMapper.class,
                    Set.of("selectListByDefinitionId", "insert"),
                    JobDefinitionNodePolicyMapper.class,
                    Set.of("selectListByDefinitionId", "insert"),
                    JobDefinitionEdgeMapper.class,
                    Set.of("selectListByDefinitionId", "insert"));
    private static final Map<String, String> MAPPER_TABLES =
            Map.of(
                    DEFINITION_MAPPER, "ifj_job_definition",
                    NODE_MAPPER, "ifj_job_definition_node",
                    POLICY_MAPPER, "ifj_job_definition_node_policy",
                    EDGE_MAPPER, "ifj_job_definition_edge");
    private static final Pattern TABLE_REFERENCE = Pattern.compile("\\bifj_[a-z0-9_]+\\b");

    @Test
    void entitiesShouldMatchImmutableV3ColumnsWithoutBaseEntity() throws NoSuchFieldException {
        ENTITY_FIELDS.forEach(
                (type, fields) -> {
                    Set<String> actualFields = new LinkedHashSet<>();
                    Arrays.stream(type.getDeclaredFields())
                            .filter(field -> !field.isSynthetic())
                            .map(Field::getName)
                            .forEach(actualFields::add);
                    assertEquals(fields, actualFields, type::getSimpleName);
                    assertEquals(Object.class, type.getSuperclass());
                    assertFalse(actualFields.contains("updateTime"));
                });
        for (Class<?> type : ENTITY_FIELDS.keySet()) {
            assertTrue(type.getDeclaredField("id").isAnnotationPresent(AutoId.class));
        }
    }

    @Test
    void mapperInterfacesShouldExposeOnlyFrozenReadAndInsertMethods() {
        MAPPER_METHODS.forEach(
                (type, methods) ->
                        assertEquals(
                                methods,
                                Arrays.stream(type.getDeclaredMethods())
                                        .map(java.lang.reflect.Method::getName)
                                        .collect(java.util.stream.Collectors.toSet()),
                                type::getSimpleName));
    }

    @Test
    void definitionMapperShouldExposeReadAndInsertOnly() throws IOException {
        Configuration configuration = parseMapper(DEFINITION_MAPPER);

        assertStatements(
                configuration,
                JobDefinitionMapper.class,
                Map.of(
                        "selectById", SqlCommandType.SELECT,
                        "selectListByJobId", SqlCommandType.SELECT,
                        "insert", SqlCommandType.INSERT));
        assertEquals(
                "SELECT id, job_id, revision, name, timeout, enabled, "
                        + "description, layout_json, create_time "
                        + "FROM ifj_job_definition WHERE id = ?",
                boundSql(configuration, JobDefinitionMapper.class, "selectById", 1L));
        assertTrue(
                boundSql(configuration, JobDefinitionMapper.class, "selectListByJobId", 10L)
                        .endsWith("ORDER BY revision ASC, id ASC"));
        assertInsert(
                boundSql(configuration, JobDefinitionMapper.class, "insert", definition()),
                "ifj_job_definition",
                9);
    }

    @Test
    void nodeMapperShouldBindTargetTypeAndUseFixedOrdering() throws IOException {
        Configuration configuration = parseMapper(NODE_MAPPER);
        String xml = readMapper(NODE_MAPPER);

        assertStatements(
                configuration,
                JobDefinitionNodeMapper.class,
                Map.of(
                        "selectListByDefinitionId", SqlCommandType.SELECT,
                        "insert", SqlCommandType.INSERT));
        String selectSql =
                boundSql(
                        configuration,
                        JobDefinitionNodeMapper.class,
                        "selectListByDefinitionId",
                        1L);
        assertTrue(selectSql.contains("FROM ifj_job_definition_node WHERE definition_id = ?"));
        assertTrue(selectSql.endsWith("ORDER BY id ASC"));
        assertInsert(
                boundSql(configuration, JobDefinitionNodeMapper.class, "insert", node()),
                "ifj_job_definition_node",
                11);
        assertTrue(xml.contains(TargetType.class.getName()));
        assertTrue(xml.contains(JobNodeKind.class.getName()));
        assertTrue(xml.contains(GatewayMode.class.getName()));
        assertEquals(0, count(xml, "org.apache.ibatis.type.EnumTypeHandler"));
    }

    @Test
    void policyMapperShouldBindEnumsAndUseFixedOrdering() throws IOException {
        Configuration configuration = parseMapper(POLICY_MAPPER);
        String xml = readMapper(POLICY_MAPPER);

        assertStatements(
                configuration,
                JobDefinitionNodePolicyMapper.class,
                Map.of(
                        "selectListByDefinitionId", SqlCommandType.SELECT,
                        "insert", SqlCommandType.INSERT));
        String selectSql =
                boundSql(
                        configuration,
                        JobDefinitionNodePolicyMapper.class,
                        "selectListByDefinitionId",
                        1L);
        assertTrue(
                selectSql.contains(
                        "FROM ifj_job_definition_node_policy " + "WHERE definition_id = ?"));
        assertTrue(selectSql.endsWith("ORDER BY node_id ASC, id ASC"));
        assertInsert(
                boundSql(configuration, JobDefinitionNodePolicyMapper.class, "insert", policy()),
                "ifj_job_definition_node_policy",
                6);
        assertTrue(xml.contains(FailStrategy.class.getName()));
        assertTrue(xml.contains(ExecuteCondition.class.getName()));
        assertEquals(0, count(xml, "org.apache.ibatis.type.EnumTypeHandler"));
    }

    @Test
    void edgeMapperShouldBindEndpointsAndUseFixedOrdering() throws IOException {
        Configuration configuration = parseMapper(EDGE_MAPPER);

        assertStatements(
                configuration,
                JobDefinitionEdgeMapper.class,
                Map.of(
                        "selectListByDefinitionId", SqlCommandType.SELECT,
                        "insert", SqlCommandType.INSERT));
        String selectSql =
                boundSql(
                        configuration,
                        JobDefinitionEdgeMapper.class,
                        "selectListByDefinitionId",
                        1L);
        assertTrue(selectSql.contains("FROM ifj_job_definition_edge WHERE definition_id = ?"));
        assertTrue(selectSql.endsWith("ORDER BY id ASC"));
        assertInsert(
                boundSql(configuration, JobDefinitionEdgeMapper.class, "insert", edge()),
                "ifj_job_definition_edge",
                10);
    }

    @Test
    void mappersShouldBeSingleTableAndWithoutMutableStatements() throws IOException {
        for (Map.Entry<String, String> mapper : MAPPER_TABLES.entrySet()) {
            String resource = mapper.getKey();
            String xml = readMapper(resource);
            String lowerCase = xml.toLowerCase(java.util.Locale.ROOT);
            Set<String> tables = new LinkedHashSet<>();
            Matcher tableMatcher = TABLE_REFERENCE.matcher(lowerCase);
            while (tableMatcher.find()) {
                tables.add(tableMatcher.group());
            }

            assertEquals(Set.of(mapper.getValue()), tables);
            assertEquals(DEFINITION_MAPPER.equals(resource) ? 2 : 1, count(lowerCase, "<select "));
            assertEquals(1, count(lowerCase, "<insert "));
            assertFalse(lowerCase.contains("<update "));
            assertFalse(lowerCase.contains("<delete "));
            assertFalse(lowerCase.contains(" join "));
            assertFalse(lowerCase.contains("${"));
            assertFalse(lowerCase.contains("update_time"));
        }
    }

    private Configuration parseMapper(String resourceName) throws IOException {
        Configuration configuration = new Configuration();
        configuration.setMapUnderscoreToCamelCase(true);
        ClassPathResource resource = new ClassPathResource(resourceName);
        try (var input = resource.getInputStream()) {
            XMLMapperBuilder builder =
                    new XMLMapperBuilder(
                            input, configuration, resourceName, configuration.getSqlFragments());
            builder.parse();
        }
        return configuration;
    }

    private void assertStatements(
            Configuration configuration,
            Class<?> mapperType,
            Map<String, SqlCommandType> expected) {
        expected.forEach(
                (id, commandType) -> {
                    String statement = namespace(mapperType, id);
                    assertTrue(configuration.hasStatement(statement, false));
                    assertEquals(
                            commandType,
                            configuration.getMappedStatement(statement).getSqlCommandType());
                });
    }

    private void assertInsert(String sql, String table, int parameters) {
        assertTrue(sql.startsWith("INSERT INTO " + table));
        assertEquals(parameters, sql.chars().filter(value -> value == '?').count());
    }

    private String boundSql(
            Configuration configuration,
            Class<?> mapperType,
            String statementId,
            Object parameter) {
        return normalizedSql(
                configuration
                        .getMappedStatement(namespace(mapperType, statementId))
                        .getBoundSql(parameter)
                        .getSql());
    }

    private String readMapper(String resourceName) throws IOException {
        ClassPathResource resource = new ClassPathResource(resourceName);
        try (var input = resource.getInputStream()) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private JobDefinition definition() {
        JobDefinition definition = new JobDefinition();
        definition.setId(1L);
        definition.setJobId(10L);
        definition.setRevision(1L);
        definition.setName("job");
        definition.setTimeout(60);
        definition.setEnabled(true);
        definition.setDescription("description");
        definition.setCreateTime(LocalDateTime.now());
        return definition;
    }

    private JobDefinitionNode node() {
        JobDefinitionNode node = new JobDefinitionNode();
        node.setId(2L);
        node.setDefinitionId(1L);
        node.setNodeKey("node-a");
        node.setName("node");
        node.setNodeKind(JobNodeKind.EXECUTION);
        node.setNodeType(TargetType.JOB);
        node.setRefId(20L);
        node.setCreateTime(LocalDateTime.now());
        return node;
    }

    private JobDefinitionNodePolicy policy() {
        JobDefinitionNodePolicy policy = new JobDefinitionNodePolicy();
        policy.setId(3L);
        policy.setDefinitionId(1L);
        policy.setNodeId(2L);
        policy.setFailStrategy(FailStrategy.STOP);
        policy.setExecuteCondition(ExecuteCondition.ALL_SUCCESS);
        policy.setCreateTime(LocalDateTime.now());
        return policy;
    }

    private JobDefinitionEdge edge() {
        JobDefinitionEdge edge = new JobDefinitionEdge();
        edge.setId(4L);
        edge.setDefinitionId(1L);
        edge.setFromNodeId(2L);
        edge.setToNodeId(3L);
        edge.setCreateTime(LocalDateTime.now());
        return edge;
    }

    private int count(String value, String token) {
        return value.split(java.util.regex.Pattern.quote(token), -1).length - 1;
    }

    private String namespace(Class<?> mapperType, String id) {
        return mapperType.getName() + "." + id;
    }

    private String normalizedSql(String sql) {
        return sql.replaceAll("\\s+", " ").trim();
    }
}
