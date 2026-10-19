// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.infra.mapper.job;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ifielsc.ijob.infra.entity.job.Job;
import com.ifielsc.ijob.infra.mapper.task.TaskReferenceLockMapper;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

class JobPublicationMapperXmlTest {
    private static final String PUBLICATION_MAPPER = "mapper/job/JobPublicationMapper.xml";
    private static final String SNAPSHOT_MAPPER =
            "mapper/job/JobDefinitionReferenceSnapshotMapper.xml";
    private static final String TASK_LOCK_MAPPER = "mapper/task/TaskReferenceLockMapper.xml";
    private static final Pattern TABLE_REFERENCE = Pattern.compile("\\bifj_[a-z0-9_]+\\b");

    @Test
    void dedicatedTypesAndInterfacesShouldMatchFrozenSurface() {
        assertFields(
                JobPublicationIdentityRow.class,
                Set.of(
                        "id",
                        "name",
                        "currentDefinitionId",
                        "currentRevision",
                        "retired",
                        "retiredTime",
                        "createTime",
                        "updateTime"));
        assertFields(
                JobCurrentDefinitionAdvance.class,
                Set.of(
                        "jobId",
                        "expectedDefinitionId",
                        "expectedRevision",
                        "definitionId",
                        "nextRevision",
                        "name",
                        "timeout",
                        "enabled",
                        "description",
                        "updateTime"));
        assertFields(
                JobIdentityRetirement.class,
                Set.of(
                        "jobId", "expectedDefinitionId",
                        "expectedRevision", "retiredTime"));
        assertMethods(
                JobPublicationMapper.class,
                Set.of(
                        "lockAllJobIdsForPublication",
                        "selectIdentityForUpdate",
                        "insertIdentity",
                        "initializeCurrentDefinition",
                        "advanceCurrentDefinition",
                        "retireIdentity"));
        assertMethods(
                JobDefinitionReferenceSnapshotMapper.class,
                Set.of(
                        "selectAllCurrentJobIds",
                        "selectCurrentJobReferences",
                        "selectCurrentJobDefinitionId"));
        assertMethods(
                TaskReferenceLockMapper.class,
                Set.of("lockExistingIds", "selectCurrentTaskDefinitionId"));
    }

    @Test
    void publicationMapperShouldExposeOnlyFrozenStatements() throws IOException {
        Configuration configuration = parseMapper(PUBLICATION_MAPPER);

        assertStatements(
                configuration,
                JobPublicationMapper.class,
                Map.of(
                        "lockAllJobIdsForPublication", SqlCommandType.SELECT,
                        "selectIdentityForUpdate", SqlCommandType.SELECT,
                        "insertIdentity", SqlCommandType.INSERT,
                        "initializeCurrentDefinition", SqlCommandType.UPDATE,
                        "advanceCurrentDefinition", SqlCommandType.UPDATE,
                        "retireIdentity", SqlCommandType.UPDATE));
    }

    @Test
    void publicationLocksShouldUseFixedJobOrderingAndColumns() throws IOException {
        Configuration configuration = parseMapper(PUBLICATION_MAPPER);

        assertEquals(
                "SELECT id FROM ifj_job ORDER BY id ASC FOR UPDATE",
                boundSql(
                        configuration,
                        JobPublicationMapper.class,
                        "lockAllJobIdsForPublication",
                        null));
        assertEquals(
                "SELECT id, name, current_definition_id, current_revision, "
                        + "retired, retired_time, create_time, update_time "
                        + "FROM ifj_job "
                        + "WHERE id = ? FOR UPDATE",
                boundSql(
                        configuration,
                        JobPublicationMapper.class,
                        "selectIdentityForUpdate",
                        Map.of("jobId", 10L)));
    }

    @Test
    void identityInsertShouldForceBothPointerColumnsToNull() throws IOException {
        String sql =
                boundSql(
                        parseMapper(PUBLICATION_MAPPER),
                        JobPublicationMapper.class,
                        "insertIdentity",
                        identity());

        assertEquals(
                "INSERT INTO ifj_job ( id, name, timeout, enabled, "
                        + "description, current_definition_id, "
                        + "current_revision, retired, retired_time, "
                        + "create_time, update_time ) "
                        + "VALUES ( ?, ?, ?, ?, ?, NULL, NULL, FALSE, NULL, ?, ? )",
                sql);
        assertEquals(7, parameterCount(sql));
    }

    @Test
    void initialPointerShouldRequireBothCurrentColumnsToBeNull() throws IOException {
        String sql =
                boundSql(
                        parseMapper(PUBLICATION_MAPPER),
                        JobPublicationMapper.class,
                        "initializeCurrentDefinition",
                        Map.of(
                                "jobId", 10L,
                                "definitionId", 100L,
                                "updateTime", LocalDateTime.now()));

        assertEquals(
                "UPDATE ifj_job SET current_definition_id = ?, "
                        + "current_revision = 1, update_time = ? "
                        + "WHERE id = ? "
                        + "AND current_definition_id IS NULL "
                        + "AND current_revision IS NULL "
                        + "AND retired = FALSE AND retired_time IS NULL",
                sql);
        assertEquals(3, parameterCount(sql));
    }

    @Test
    void advanceShouldSetFixedMirrorAndMatchBothExpectedValues() throws IOException {
        String sql =
                boundSql(
                        parseMapper(PUBLICATION_MAPPER),
                        JobPublicationMapper.class,
                        "advanceCurrentDefinition",
                        advance());

        assertEquals(
                "UPDATE ifj_job SET current_definition_id = ?, "
                        + "current_revision = ?, name = ?, timeout = ?, "
                        + "enabled = ?, description = ?, update_time = ? "
                        + "WHERE id = ? "
                        + "AND current_definition_id = ? "
                        + "AND current_revision = ? "
                        + "AND retired = FALSE AND retired_time IS NULL",
                sql);
        assertEquals(10, parameterCount(sql));
    }

    @Test
    void retirementShouldBeSingleDirectionPointerCas() throws IOException {
        JobIdentityRetirement retirement = new JobIdentityRetirement();
        retirement.setJobId(10L);
        retirement.setExpectedDefinitionId(100L);
        retirement.setExpectedRevision(4L);
        retirement.setRetiredTime(LocalDateTime.now());

        String sql =
                boundSql(
                        parseMapper(PUBLICATION_MAPPER),
                        JobPublicationMapper.class,
                        "retireIdentity",
                        retirement);

        assertEquals(
                "UPDATE ifj_job SET retired = TRUE, retired_time = ?, "
                        + "update_time = ? WHERE id = ? AND retired = FALSE "
                        + "AND retired_time IS NULL "
                        + "AND current_definition_id <=> ? "
                        + "AND current_revision <=> ?",
                sql);
        assertEquals(5, parameterCount(sql));
    }

    @Test
    void snapshotShouldReadOnlyCurrentDefinitionGraph() throws IOException {
        Configuration configuration = parseMapper(SNAPSHOT_MAPPER);

        assertStatements(
                configuration,
                JobDefinitionReferenceSnapshotMapper.class,
                Map.of(
                        "selectAllCurrentJobIds", SqlCommandType.SELECT,
                        "selectCurrentJobReferences", SqlCommandType.SELECT));
        assertEquals(
                "SELECT id FROM ifj_job "
                        + "WHERE retired = FALSE AND retired_time IS NULL "
                        + "AND current_definition_id IS NOT NULL "
                        + "AND current_revision IS NOT NULL ORDER BY id ASC",
                boundSql(
                        configuration,
                        JobDefinitionReferenceSnapshotMapper.class,
                        "selectAllCurrentJobIds",
                        null));
        assertEquals(
                "SELECT job.id AS jobId, node.ref_id AS referencedJobId "
                        + "FROM ifj_job job "
                        + "JOIN ifj_job_definition_node node "
                        + "ON node.definition_id = job.current_definition_id "
                        + "JOIN ifj_job referenced "
                        + "ON referenced.id = node.ref_id "
                        + "AND referenced.retired = FALSE "
                        + "AND referenced.retired_time IS NULL "
                        + "AND referenced.current_definition_id IS NOT NULL "
                        + "AND referenced.current_revision IS NOT NULL "
                        + "WHERE job.retired = FALSE "
                        + "AND job.retired_time IS NULL "
                        + "AND node.node_type = 'JOB' "
                        + "ORDER BY job.id ASC, node.ref_id ASC, node.id ASC",
                boundSql(
                        configuration,
                        JobDefinitionReferenceSnapshotMapper.class,
                        "selectCurrentJobReferences",
                        null));
    }

    @Test
    void taskLockShouldBindDeduplicatedAscendingIdsForUpdate() throws IOException {
        Configuration configuration = parseMapper(TASK_LOCK_MAPPER);

        assertStatements(
                configuration,
                TaskReferenceLockMapper.class,
                Map.of("lockExistingIds", SqlCommandType.SELECT));
        assertEquals(
                "SELECT id FROM ifj_task WHERE id IN ( ? , ? ) "
                        + "AND retired = FALSE "
                        + "AND current_task_definition_id IS NOT NULL "
                        + "AND current_revision IS NOT NULL "
                        + "ORDER BY id ASC FOR UPDATE",
                boundSql(
                        configuration,
                        TaskReferenceLockMapper.class,
                        "lockExistingIds",
                        Map.of("ids", List.of(10L, 20L))));
    }

    @Test
    void mapperXmlShouldUseOnlyFrozenTablesAndNoTextSubstitution() throws IOException {
        assertXmlSurface(PUBLICATION_MAPPER, Set.of("ifj_job"), 8, 2, 9);
        assertXmlSurface(SNAPSHOT_MAPPER, Set.of("ifj_job", "ifj_job_definition_node"), 6, 0, 0);
        assertXmlSurface(TASK_LOCK_MAPPER, Set.of("ifj_task"), 6, 0, 0);
        assertFalse(readMapper(SNAPSHOT_MAPPER).contains("FROM ifj_job_node"));
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

    private void assertXmlSurface(
            String resourceName, Set<String> expectedTables, int selects, int inserts, int updates)
            throws IOException {
        String xml = readMapper(resourceName);
        String lowerCase = xml.toLowerCase(java.util.Locale.ROOT);
        Set<String> actualTables = new LinkedHashSet<>();
        Matcher matcher = TABLE_REFERENCE.matcher(lowerCase);
        while (matcher.find()) {
            actualTables.add(matcher.group());
        }

        assertEquals(expectedTables, actualTables);
        assertEquals(selects, count(lowerCase, "<select "));
        assertEquals(inserts, count(lowerCase, "<insert "));
        assertEquals(updates, count(lowerCase, "<update "));
        assertFalse(lowerCase.contains("<delete "));
        assertFalse(lowerCase.contains("${"));
    }

    private void assertFields(Class<?> type, Set<String> expected) {
        Set<String> actual = new LinkedHashSet<>();
        Arrays.stream(type.getDeclaredFields())
                .filter(field -> !field.isSynthetic())
                .map(Field::getName)
                .forEach(actual::add);
        assertEquals(expected, actual, type::getSimpleName);
        assertEquals(Object.class, type.getSuperclass());
    }

    private void assertMethods(Class<?> type, Set<String> expected) {
        assertEquals(
                expected,
                Arrays.stream(type.getDeclaredMethods())
                        .map(java.lang.reflect.Method::getName)
                        .collect(java.util.stream.Collectors.toSet()),
                type::getSimpleName);
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
            return new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
    }

    private Job identity() {
        Job job = new Job();
        job.setId(10L);
        job.setName("job");
        job.setTimeout(60);
        job.setEnabled(true);
        job.setDescription("description");
        job.setCreateTime(LocalDateTime.now());
        job.setUpdateTime(LocalDateTime.now());
        return job;
    }

    private JobCurrentDefinitionAdvance advance() {
        JobCurrentDefinitionAdvance advance = new JobCurrentDefinitionAdvance();
        advance.setJobId(10L);
        advance.setExpectedDefinitionId(100L);
        advance.setExpectedRevision(1L);
        advance.setDefinitionId(101L);
        advance.setNextRevision(2L);
        advance.setName("job");
        advance.setTimeout(60);
        advance.setEnabled(true);
        advance.setDescription("description");
        advance.setUpdateTime(LocalDateTime.now());
        return advance;
    }

    private int parameterCount(String sql) {
        return (int) sql.chars().filter(value -> value == '?').count();
    }

    private int count(String value, String token) {
        return value.split(Pattern.quote(token), -1).length - 1;
    }

    private String namespace(Class<?> mapperType, String id) {
        return mapperType.getName() + "." + id;
    }

    private String normalizedSql(String sql) {
        return sql.replaceAll("\\s+", " ").replace("FALSE ,", "FALSE,").trim();
    }
}
