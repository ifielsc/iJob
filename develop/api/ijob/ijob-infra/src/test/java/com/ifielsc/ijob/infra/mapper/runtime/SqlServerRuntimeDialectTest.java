package com.ifielsc.ijob.infra.mapper.runtime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.util.List;
import java.util.Map;

class SqlServerRuntimeDialectTest {
    @Test
    void absentEventWaitTimesMustBindAsTimestampNulls() throws Exception {
        Configuration configuration = new Configuration();
        configuration.setDatabaseId("sqlserver");
        parse(configuration, "mapper/runtime/EventWaitMapper.xml");
        var statement =
                configuration.getMappedStatement(
                        "com.ifielsc.ijob.infra.mapper.runtime.EventWaitMapper.insert");
        var wait = new com.ifielsc.ijob.infra.entity.runtime.EventWait();
        var bound = statement.getBoundSql(wait);
        var nullTypes = new java.util.HashMap<Integer, Integer>();
        var prepared =
                (java.sql.PreparedStatement)
                        java.lang.reflect.Proxy.newProxyInstance(
                                getClass().getClassLoader(),
                                new Class<?>[] {java.sql.PreparedStatement.class},
                                (proxy, method, args) -> {
                                    if (method.getName().equals("setNull")) {
                                        nullTypes.put((Integer) args[0], (Integer) args[1]);
                                    }
                                    return null;
                                });
        new org.apache.ibatis.scripting.defaults.DefaultParameterHandler(statement, wait, bound)
                .setParameters(prepared);
        org.junit.jupiter.api.Assertions.assertEquals(java.sql.Types.TIMESTAMP, nullTypes.get(11));
        org.junit.jupiter.api.Assertions.assertEquals(java.sql.Types.TIMESTAMP, nullTypes.get(16));
    }

    @Test
    void commandClaimMustPutAliasBeforeTableHints() throws IOException {
        Configuration configuration = new Configuration();
        configuration.setDatabaseId("sqlserver");
        parse(configuration, "mapper/coordination/RuntimeCommandMapper.xml");
        String sql =
                configuration
                        .getMappedStatement(
                                "com.ifielsc.ijob.infra.mapper.coordination.RuntimeCommandMapper.selectDispatchableRequestIds")
                        .getBoundSql(Map.of("limit", 2, "triggerId", 1L, "fenceToken", 1L))
                        .getSql();
        assertTrue(
                sql.contains(
                        "FROM ifj_runtime_command command_record WITH (UPDLOCK, READPAST,"
                                + " ROWLOCK)"));
    }

    @Test
    void jobListMustAvoidReservedIdentityAlias() throws IOException {
        Configuration configuration = new Configuration();
        configuration.setDatabaseId("sqlserver");
        parse(configuration, "mapper/job/JobCurrentDefinitionMapper.xml");
        var query = new com.ifielsc.ijob.contract.job.JobPageQuery();
        query.setName("中文任务");
        String sql =
                configuration
                        .getMappedStatement(
                                "com.ifielsc.ijob.infra.mapper.job.JobCurrentDefinitionMapper.selectDtoList")
                        .getBoundSql(query)
                        .getSql();
        assertTrue(sql.contains("FROM ifj_job job_identity"));
        assertFalse(sql.contains(" identity."));
        assertTrue(sql.contains("ORDER BY job_identity.update_time"));
    }

    @Test
    void publicationAndReferenceQueriesMustUseBitLiterals() throws IOException {
        Configuration configuration = new Configuration();
        configuration.setDatabaseId("sqlserver");
        for (String resource :
                List.of(
                        "task/TaskPublicationMapper",
                        "task/TaskMapper",
                        "task/TaskReferenceLockMapper",
                        "task/TaskReferenceMapper",
                        "job/JobPublicationMapper",
                        "job/JobMapper",
                        "job/JobDefinitionReferenceSnapshotMapper")) {
            parse(configuration, "mapper/" + resource + ".xml");
        }
        for (String query :
                List.of(
                        "task.TaskPublicationMapper.insertIdentity",
                        "task.TaskPublicationMapper.initializeCurrentDefinition",
                        "task.TaskPublicationMapper.advanceCurrentDefinition",
                        "task.TaskMapper.selectById",
                        "task.TaskReferenceLockMapper.selectCurrentTaskDefinitionId",
                        "job.JobPublicationMapper.insertIdentity",
                        "job.JobPublicationMapper.initializeCurrentDefinition",
                        "job.JobPublicationMapper.advanceCurrentDefinition",
                        "job.JobMapper.selectById",
                        "job.JobDefinitionReferenceSnapshotMapper.selectAllCurrentJobIds",
                        "job.JobDefinitionReferenceSnapshotMapper.selectCurrentJobDefinitionId")) {
            String sql =
                    configuration
                            .getMappedStatement("com.ifielsc.ijob.infra.mapper." + query)
                            .getBoundSql(Map.of("id", 1L))
                            .getSql();
            assertFalse(sql.contains("FALSE"), query);
            assertTrue(sql.contains("0"), query);
        }
    }

    @Test
    void runtimeLookupsMustUseSqlServerBounds() throws IOException {
        Configuration configuration = new Configuration();
        configuration.setDatabaseId("sqlserver");
        for (String mapper :
                List.of(
                        "EventRecordMapper",
                        "EventWaitMapper",
                        "InstanceLogMapper",
                        "InstanceMapper",
                        "TriggerMapper")) {
            parse(configuration, "mapper/runtime/" + mapper + ".xml");
        }
        Map<String, Object> params =
                Map.of(
                        "limit",
                        2,
                        "id",
                        1L,
                        "instanceId",
                        1L,
                        "triggerId",
                        1L,
                        "afterId",
                        0L,
                        "eventCode",
                        "qa13",
                        "now",
                        "2026-10-01");
        for (String query :
                List.of(
                        "EventRecordMapper.selectFirstMatching",
                        "EventRecordMapper.selectMatchingForWait",
                        "EventWaitMapper.selectLatestByInstance",
                        "EventWaitMapper.selectRecoverableWaitingBatch",
                        "EventWaitMapper.selectTimedOut",
                        "EventWaitMapper.selectResolvedRootIds",
                        "InstanceLogMapper.selectAfterId",
                        "InstanceMapper.selectRootByTriggerId",
                        "TriggerMapper.selectWaitingIds")) {
            String sql =
                    configuration
                            .getMappedStatement("com.ifielsc.ijob.infra.mapper.runtime." + query)
                            .getBoundSql(params)
                            .getSql();
            assertTrue(sql.contains("TOP ("), query);
            assertFalse(sql.contains("LIMIT"), query);
            assertTrue(sql.contains("ORDER BY"), query);
        }
    }

    @Test
    void coordinationAgesMustNotUseSqliteScalarMax() throws IOException {
        Configuration configuration = new Configuration();
        configuration.setDatabaseId("sqlserver");
        parse(configuration, "mapper/coordination/CoordinationSnapshotMapper.xml");
        String sql =
                configuration
                        .getMappedStatement(
                                "com.ifielsc.ijob.infra.mapper.coordination.CoordinationSnapshotMapper.selectSnapshot")
                        .getBoundSql(
                                Map.of(
                                        "staleRunMicros",
                                        8000000,
                                        "leaseName",
                                        "runtime-maintenance"))
                        .getSql();
        assertFalse(sql.contains("MAX(0,"));
        assertTrue(sql.contains("DATEDIFF_BIG(MILLISECOND, MIN(create_time)"));
        assertTrue(sql.contains("DATEDIFF_BIG(MILLISECOND, MIN(expires_at)"));
    }

    private void parse(Configuration configuration, String resource) throws IOException {
        try (var input = new ClassPathResource(resource).getInputStream()) {
            new XMLMapperBuilder(input, configuration, resource, configuration.getSqlFragments())
                    .parse();
        }
    }
}
