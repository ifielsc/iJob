package com.ifelio.ijob.infra.mapper.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ifelio.ijob.contract.enums.InstanceStatus;
import com.ifelio.ijob.contract.enums.TargetType;
import com.ifelio.ijob.contract.event.EventWaitPageQuery;
import com.ifelio.ijob.contract.runtime.InstancePageQuery;
import com.ifelio.ijob.infra.entity.runtime.Instance;
import com.ifelio.ijob.infra.entity.runtime.Trigger;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

class RuntimeMapperXmlTest {
    @Test
    void distributedPermitConstructorMappingMustUsePrimitiveAliases() throws Exception {
        Configuration configuration = new Configuration();
        String resource = "mapper/coordination/ConcurrencyPermitMapper.xml";
        try (var input = new ClassPathResource(resource).getInputStream()) {
            new XMLMapperBuilder(input, configuration, resource, configuration.getSqlFragments())
                    .parse();
        }
        var map =
                configuration.getResultMap(
                        "com.ifelio.ijob.infra.mapper.coordination.ConcurrencyPermitMapper.distributedPermitResultMap");
        Class<?>[] arguments =
                map.getConstructorResultMappings().stream()
                        .map(org.apache.ibatis.mapping.ResultMapping::getJavaType)
                        .toArray(Class<?>[]::new);
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(
                () -> map.getType().getDeclaredConstructor(arguments));
    }

    @Test
    void everyInstanceDtoMappingMustHaveASetter() throws Exception {
        Configuration configuration = new Configuration();
        try (var input =
                new ClassPathResource("mapper/runtime/InstanceMapper.xml").getInputStream()) {
            new XMLMapperBuilder(
                            input,
                            configuration,
                            "mapper/runtime/InstanceMapper.xml",
                            configuration.getSqlFragments())
                    .parse();
        }
        var map =
                configuration.getResultMap(
                        InstanceMapper.class.getName() + ".instanceDtoResultMap");
        var meta =
                org.apache.ibatis.reflection.MetaClass.forClass(
                        map.getType(), new org.apache.ibatis.reflection.DefaultReflectorFactory());
        for (var mapping : map.getResultMappings()) {
            assertTrue(meta.hasSetter(mapping.getProperty()), mapping.getProperty());
        }
        String sql =
                boundSql(
                        configuration,
                        InstanceMapper.class,
                        "selectDtoList",
                        new InstancePageQuery());
        assertFalse(sql.contains("input_variables"), sql);
        assertFalse(sql.contains("output_variables"), sql);
    }

    private static final String TRIGGER_MAPPER = "mapper/runtime/TriggerMapper.xml";
    private static final String INSTANCE_MAPPER = "mapper/runtime/InstanceMapper.xml";
    private static final String EVENT_MAPPER = "mapper/runtime/EventRecordMapper.xml";
    private static final String EVENT_WAIT_MAPPER = "mapper/runtime/EventWaitMapper.xml";
    private static final List<String> MAPPERS =
            List.of(
                    TRIGGER_MAPPER,
                    INSTANCE_MAPPER,
                    EVENT_MAPPER,
                    EVENT_WAIT_MAPPER,
                    "mapper/runtime/AppRunMapper.xml",
                    "mapper/runtime/DagGatewayDecisionMapper.xml",
                    "mapper/runtime/RuntimeDataRetentionMapper.xml",
                    "mapper/runtime/InstanceLogMapper.xml",
                    "mapper/event/EventBindingMapper.xml",
                    "mapper/notify/NotifyBindingMapper.xml");
    private static final List<String> TRIGGER_FIELDS =
            List.of(
                    "id",
                    "scheduleId",
                    "targetType",
                    "targetId",
                    "triggerType",
                    "idempotencyKey",
                    "eventCode",
                    "status",
                    "triggerTime",
                    "businessDate",
                    "businessTime",
                    "businessZoneId",
                    "message",
                    "errorCode",
                    "messageKey",
                    "messageArgs",
                    "definitionId",
                    "taskDefinitionId",
                    "variables",
                    "eventId",
                    "eventBindingId",
                    "createTime",
                    "updateTime");
    private static final List<String> INSTANCE_FIELDS =
            List.of(
                    "id",
                    "nodeId",
                    "parentInstanceId",
                    "rootInstanceId",
                    "executionFence",
                    "targetType",
                    "targetId",
                    "targetName",
                    "triggerType",
                    "triggerId",
                    "status",
                    "progress",
                    "retryCount",
                    "startTime",
                    "timeoutAt",
                    "waitReason",
                    "permitWaitDeadline",
                    "endTime",
                    "message",
                    "errorCode",
                    "messageKey",
                    "messageArgs",
                    "definitionId",
                    "definitionNodeId",
                    "taskDefinitionId",
                    "inputVariables",
                    "outputVariables",
                    "variablesTruncated",
                    "createTime",
                    "updateTime");
    private static final List<String> TRIGGER_RESULT_MAPPINGS =
            List.of(
                    "id:id",
                    "scheduleId:schedule_id",
                    "targetType:target_type",
                    "targetId:target_id",
                    "triggerType:trigger_type",
                    "idempotencyKey:idempotency_key",
                    "eventCode:event_code",
                    "status:status",
                    "triggerTime:trigger_time",
                    "businessDate:business_date",
                    "businessTime:business_time",
                    "businessZoneId:business_zone_id",
                    "message:message",
                    "errorCode:error_code",
                    "messageKey:message_key",
                    "messageArgs:message_args",
                    "definitionId:definition_id",
                    "taskDefinitionId:task_definition_id",
                    "variables:variables",
                    "eventId:event_id",
                    "eventBindingId:event_binding_id",
                    "createTime:create_time",
                    "updateTime:update_time");
    private static final List<String> INSTANCE_RESULT_MAPPINGS =
            List.of(
                    "id:id",
                    "nodeId:node_id",
                    "parentInstanceId:parent_instance_id",
                    "rootInstanceId:root_instance_id",
                    "executionFence:execution_fence",
                    "targetType:target_type",
                    "targetId:target_id",
                    "targetName:target_name",
                    "triggerType:trigger_type",
                    "triggerId:trigger_id",
                    "status:status",
                    "progress:progress",
                    "retryCount:retry_count",
                    "startTime:start_time",
                    "timeoutAt:timeout_at",
                    "waitReason:wait_reason",
                    "permitWaitDeadline:permit_wait_deadline",
                    "endTime:end_time",
                    "message:message",
                    "errorCode:error_code",
                    "messageKey:message_key",
                    "messageArgs:message_args",
                    "definitionId:definition_id",
                    "definitionNodeId:definition_node_id",
                    "taskDefinitionId:task_definition_id",
                    "inputVariables:input_variables",
                    "outputVariables:output_variables",
                    "variablesTruncated:variables_truncated",
                    "createTime:create_time",
                    "updateTime:update_time");

    @Test
    void shouldParseAllRuntimeStatements() throws IOException {
        Configuration configuration = parseMappers();

        List.of(
                        "runtime.TriggerMapper.selectById",
                        "runtime.TriggerMapper.selectByIdempotencyKey",
                        "runtime.TriggerMapper.insert",
                        "runtime.TriggerMapper.updateStatusIfCurrent",
                        "runtime.InstanceMapper.selectById",
                        "runtime.InstanceMapper.selectRootByTriggerId",
                        "runtime.InstanceMapper.selectDtoList",
                        "runtime.InstanceMapper.insert",
                        "runtime.InstanceMapper.updateById",
                        "runtime.InstanceMapper.markRunningIfWaiting",
                        "runtime.InstanceMapper.markPausedIfRunning",
                        "runtime.InstanceMapper.markRunningIfPaused",
                        "runtime.InstanceMapper.updateProgressIfRunning",
                        "runtime.InstanceMapper.updateInputVariablesIfWaiting",
                        "runtime.InstanceMapper.updateOutputVariablesIfRunning",
                        "runtime.InstanceMapper.completeIfCurrent",
                        "runtime.InstanceMapper.resetForRetry",
                        "runtime.AppRunMapper.markStaleActiveRunsCrashed",
                        "runtime.AppRunMapper.insertStarting",
                        "runtime.AppRunMapper.markRunning",
                        "runtime.AppRunMapper.heartbeat",
                        "runtime.AppRunMapper.markDraining",
                        "runtime.AppRunMapper.markStopped",
                        "runtime.DagGatewayDecisionMapper.selectByJobInstanceId",
                        "runtime.DagGatewayDecisionMapper.selectByJobInstanceAndNode",
                        "runtime.DagGatewayDecisionMapper.insertIfOwned",
                        "runtime.RuntimeDataRetentionMapper.countTerminalTriggersBefore",
                        "runtime.RuntimeDataRetentionMapper.selectTerminalTriggerIdsBefore",
                        "runtime.RuntimeDataRetentionMapper.deleteRootInstancesByTriggerIds",
                        "runtime.RuntimeDataRetentionMapper.deleteTriggersByIds",
                        "runtime.RuntimeDataRetentionMapper.countTerminalNotificationLogsBefore",
                        "runtime.RuntimeDataRetentionMapper.deleteTerminalNotificationLogsBefore",
                        "runtime.RuntimeDataRetentionMapper.countDeletableEventsBefore",
                        "runtime.RuntimeDataRetentionMapper.selectDeletableEventIdsBefore",
                        "runtime.RuntimeDataRetentionMapper.deleteEventsByIds",
                        "runtime.RuntimeDataRetentionMapper.countTerminalAppRunsBefore",
                        "runtime.RuntimeDataRetentionMapper.deleteTerminalAppRunsBefore",
                        "runtime.EventRecordMapper.selectBySourceAndIdempotency",
                        "runtime.EventRecordMapper.selectFirstMatching",
                        "runtime.EventRecordMapper.insert",
                        "runtime.EventWaitMapper.selectById",
                        "runtime.EventWaitMapper.selectLatestByInstance",
                        "runtime.EventWaitMapper.insertOperator",
                        "runtime.EventWaitMapper.selectOperatorIds",
                        "runtime.EventWaitMapper.selectApprovalList",
                        "runtime.EventWaitMapper.selectWaitingForEvent",
                        "runtime.EventWaitMapper.selectRecoverableWaitingBatch",
                        "runtime.EventWaitMapper.selectResolvedRootIds",
                        "runtime.EventWaitMapper.resolveIfWaiting",
                        "runtime.InstanceLogMapper.insert",
                        "runtime.InstanceLogMapper.selectAfterId",
                        "event.EventBindingMapper.selectListByEventCode",
                        "event.EventBindingMapper.selectIdsForUpdate",
                        "event.EventBindingMapper.deleteByIds",
                        "notify.NotifyBindingMapper.selectListByTargetId")
                .forEach(
                        suffix ->
                                assertTrue(
                                        configuration.hasStatement(
                                                "com.ifelio.ijob.infra.mapper." + suffix, false),
                                        () -> "Missing mapped statement: " + suffix));
    }

    @Test
    void eventWaitRecoveryAndApprovalQueriesShouldBeBounded() throws IOException {
        Configuration configuration = parseMappers();
        String recoverySql =
                normalizedSql(
                        configuration
                                .getMappedStatement(
                                        "com.ifelio.ijob.infra.mapper.runtime.EventWaitMapper."
                                                + "selectRecoverableWaitingBatch")
                                .getBoundSql(java.util.Map.of("limit", 100))
                                .getSql());
        EventWaitPageQuery query = new EventWaitPageQuery();
        String approvalSql =
                normalizedSql(
                        configuration
                                .getMappedStatement(
                                        "com.ifelio.ijob.infra.mapper.runtime.EventWaitMapper."
                                                + "selectApprovalList")
                                .getBoundSql(
                                        java.util.Map.of("query", query, "operatorId", "reviewer"))
                                .getSql());

        assertTrue(recoverySql.contains("AND EXISTS"));
        assertTrue(recoverySql.contains("event_record.arrived_at >= ifj_event_wait.accept_after"));
        assertTrue(recoverySql.endsWith("ORDER BY id ASC LIMIT ?"));
        assertTrue(approvalSql.contains("wait_type = 'APPROVAL'"));
        assertTrue(approvalSql.contains("EXISTS"));
        assertTrue(approvalSql.contains("FROM ifj_event_wait_operator"));
        assertFalse(approvalSql.contains("JSON_"));
        assertTrue(approvalSql.contains("status = ?"));
    }

    @Test
    void instanceQueryShouldUseBoundFiltersAndFixedOrder() throws IOException {
        Configuration configuration = parseMappers();
        InstancePageQuery query = new InstancePageQuery();
        query.setTargetType(TargetType.TASK);
        query.setTargetId(8L);
        query.setTriggerId(9L);
        query.setStatus(InstanceStatus.SUCCESS);

        String sql =
                normalizedSql(
                        configuration
                                .getMappedStatement(
                                        "com.ifelio.ijob.infra.mapper.runtime.InstanceMapper.selectDtoList")
                                .getBoundSql(query)
                                .getSql());

        assertTrue(sql.contains("FROM ifj_instance"));
        assertTrue(sql.contains("target_type = ?"));
        assertTrue(sql.contains("target_id = ?"));
        assertTrue(sql.contains("trigger_id = ?"));
        assertTrue(sql.contains("status = ?"));
        assertTrue(sql.endsWith("ORDER BY create_time DESC, id DESC"));
    }

    @Test
    void runtimeEntitiesShouldExposeExactSnapshotFields() {
        assertEquals(TRIGGER_FIELDS, declaredFields(Trigger.class));
        assertEquals(INSTANCE_FIELDS, declaredFields(Instance.class));
        for (String field : List.of("definitionId", "taskDefinitionId")) {
            assertEquals(Long.class, declaredFieldType(Trigger.class, field));
        }
        for (String field : List.of("definitionId", "definitionNodeId", "taskDefinitionId")) {
            assertEquals(Long.class, declaredFieldType(Instance.class, field));
        }
    }

    @Test
    void resultMapsShouldExposeSnapshotsInExactOrder() throws IOException {
        Configuration configuration = parseMappers();

        assertEquals(
                TRIGGER_RESULT_MAPPINGS,
                resultMappings(configuration, TriggerMapper.class, "triggerResultMap"));
        assertEquals(
                INSTANCE_RESULT_MAPPINGS,
                resultMappings(configuration, InstanceMapper.class, "instanceResultMap"));
        assertTrue(
                resultMappings(configuration, InstanceMapper.class, "instanceDtoResultMap")
                        .containsAll(
                                List.of(
                                        "jobDefinitionId:definition_id",
                                        "definitionNodeId:definition_node_id",
                                        "taskDefinitionId:task_definition_id")));
    }

    @Test
    void triggerSelectAndInsertShouldBindExactSnapshotColumns() throws IOException {
        Configuration configuration = parseMappers();
        Trigger trigger = new Trigger();

        assertEquals(
                "SELECT id, schedule_id, target_type, target_id, trigger_type, idempotency_key,"
                    + " event_code, status, trigger_time, business_date, business_time,"
                    + " business_zone_id, message, error_code, message_key, message_args,"
                    + " definition_id, task_definition_id, variables, event_id, event_binding_id,"
                    + " create_time, update_time FROM ifj_trigger WHERE id = ?",
                boundSql(configuration, TriggerMapper.class, "selectById", 1L));
        assertEquals(
                "INSERT INTO ifj_trigger ( id, schedule_id, target_type, target_id, trigger_type,"
                    + " idempotency_key, event_code, status, trigger_time, business_date,"
                    + " business_time, business_zone_id, message, error_code, message_key,"
                    + " message_args, definition_id, task_definition_id, variables, event_id,"
                    + " event_binding_id ) VALUES ( ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,"
                    + " ?, ?, ?, ?, ? ) ON DUPLICATE KEY UPDATE id = id",
                boundSql(configuration, TriggerMapper.class, "insert", trigger));
        assertEquals(
                List.of(
                        "id",
                        "scheduleId",
                        "targetType",
                        "targetId",
                        "triggerType",
                        "idempotencyKey",
                        "eventCode",
                        "status",
                        "triggerTime",
                        "businessDate",
                        "businessTime",
                        "businessZoneId",
                        "message",
                        "errorCode",
                        "messageKey",
                        "messageArgs",
                        "definitionId",
                        "taskDefinitionId",
                        "variables",
                        "eventId",
                        "eventBindingId"),
                parameterProperties(configuration, TriggerMapper.class, "insert", trigger));
    }

    @Test
    void instanceSelectAndInsertShouldBindExactSnapshotColumns() throws IOException {
        Configuration configuration = parseMappers();
        Instance instance = new Instance();

        assertEquals(
                "SELECT id, node_id, parent_instance_id, root_instance_id, execution_fence,"
                    + " target_type, target_id, trigger_type, trigger_id, status, progress,"
                    + " retry_count, start_time, timeout_at, wait_reason, permit_wait_deadline,"
                    + " end_time, message, error_code, message_key, message_args, definition_id,"
                    + " definition_node_id, task_definition_id, input_variables, output_variables,"
                    + " create_time, update_time FROM ifj_instance WHERE id = ?",
                boundSql(configuration, InstanceMapper.class, "selectById", 1L));
        String insertSql =
                boundSql(
                        configuration,
                        InstanceMapper.class,
                        "insert",
                        java.util.Map.of(
                                "instance",
                                instance,
                                "permit",
                                com.ifelio.ijob.infra.coordination.work.ExecutionFencePermit
                                        .unfenced()));
        assertTrue(insertSql.contains("execution_fence"));
        assertTrue(insertSql.contains("FROM (SELECT 1) seed"));
        assertEquals(
                List.of(
                        "instance.id",
                        "instance.nodeId",
                        "instance.parentInstanceId",
                        "instance.rootInstanceId",
                        "instance.executionFence",
                        "instance.targetType",
                        "instance.targetId",
                        "instance.triggerType",
                        "instance.triggerId",
                        "instance.status",
                        "instance.progress",
                        "instance.retryCount",
                        "instance.startTime",
                        "instance.timeoutAt",
                        "instance.endTime",
                        "instance.message",
                        "instance.errorCode",
                        "instance.messageKey",
                        "instance.messageArgs",
                        "instance.definitionId",
                        "instance.definitionNodeId",
                        "instance.taskDefinitionId",
                        "instance.inputVariables",
                        "instance.outputVariables"),
                parameterProperties(
                        configuration,
                        InstanceMapper.class,
                        "insert",
                        java.util.Map.of(
                                "instance",
                                instance,
                                "permit",
                                com.ifelio.ijob.infra.coordination.work.ExecutionFencePermit
                                        .unfenced())));
    }

    @Test
    void runtimeUpdatesShouldNeverWriteSnapshotColumns() throws IOException {
        Configuration configuration = parseMappers();
        String triggerUpdate =
                boundSql(
                        configuration,
                        TriggerMapper.class,
                        "updateStatusIfCurrent",
                        java.util.Map.of(
                                "id",
                                1L,
                                "expectedStatus",
                                com.ifelio.ijob.contract.enums.TriggerStatus.RUNNING,
                                "status",
                                com.ifelio.ijob.contract.enums.TriggerStatus.SUCCESS,
                                "message",
                                "done",
                                "errorCode",
                                "IJOB-RUNTIME-0000",
                                "messageKey",
                                "runtime.execution.success",
                                "messageArgs",
                                "[]",
                                "permit",
                                com.ifelio.ijob.infra.coordination.work.ExecutionFencePermit
                                        .unfenced()));
        String instanceUpdate =
                boundSql(
                        configuration,
                        InstanceMapper.class,
                        "updateById",
                        java.util.Map.of(
                                "instance",
                                new Instance(),
                                "permit",
                                com.ifelio.ijob.infra.coordination.work.ExecutionFencePermit
                                        .unfenced()));
        String running =
                boundSql(
                        configuration,
                        InstanceMapper.class,
                        "markRunningIfWaiting",
                        java.util.Map.of(
                                "id",
                                1L,
                                "startTime",
                                java.time.LocalDateTime.now(),
                                "permit",
                                com.ifelio.ijob.infra.coordination.work.ExecutionFencePermit
                                        .unfenced()));
        String paused =
                boundSql(
                        configuration,
                        InstanceMapper.class,
                        "markPausedIfRunning",
                        java.util.Map.of(
                                "id",
                                1L,
                                "permit",
                                com.ifelio.ijob.infra.coordination.work.ExecutionFencePermit
                                        .unfenced()));
        String resumed =
                boundSql(
                        configuration,
                        InstanceMapper.class,
                        "markRunningIfPaused",
                        java.util.Map.of(
                                "id",
                                1L,
                                "permit",
                                com.ifelio.ijob.infra.coordination.work.ExecutionFencePermit
                                        .unfenced()));
        String progress =
                boundSql(
                        configuration,
                        InstanceMapper.class,
                        "updateProgressIfRunning",
                        java.util.Map.of(
                                "id",
                                1L,
                                "progress",
                                50,
                                "permit",
                                com.ifelio.ijob.infra.coordination.work.ExecutionFencePermit
                                        .unfenced()));
        String completed =
                boundSql(
                        configuration,
                        InstanceMapper.class,
                        "completeIfCurrent",
                        java.util.Map.of(
                                "instance",
                                new Instance(),
                                "expectedStatus",
                                InstanceStatus.RUNNING,
                                "permit",
                                com.ifelio.ijob.infra.coordination.work.ExecutionFencePermit
                                        .unfenced()));
        String reset =
                boundSql(
                        configuration,
                        InstanceMapper.class,
                        "resetForRetry",
                        java.util.Map.of(
                                "id",
                                1L,
                                "expectedStatus",
                                InstanceStatus.FAILED,
                                "permit",
                                com.ifelio.ijob.infra.coordination.work.ExecutionFencePermit
                                        .unfenced()));

        assertTrue(triggerUpdate.contains("SET status = ?"));
        assertTrue(triggerUpdate.contains("AND status = ?"));
        assertTrue(instanceUpdate.contains("status = ?"));
        assertTrue(instanceUpdate.contains("progress = ?"));
        assertTrue(instanceUpdate.contains("retry_count = COALESCE(?, 0)"));
        assertTrue(running.contains("AND status = 'WAITING'"));
        assertTrue(running.contains("timeout_at = CASE"));
        assertTrue(running.contains("CURRENT_TIMESTAMP(3)"));
        assertTrue(paused.contains("AND status = 'RUNNING'"));
        assertTrue(resumed.contains("AND status = 'PAUSED'"));
        assertTrue(progress.contains("AND status = 'RUNNING'"));
        assertTrue(completed.contains("AND (status = ?"));
        assertTrue(
                completed.contains(
                        "OR (? IN ('RUNNING', 'PAUSED') AND status IN ('RUNNING', 'PAUSED'))"));
        assertTrue(reset.contains("retry_count = retry_count + 1"));
        assertTrue(reset.contains("output_variables = NULL"));
        assertTrue(reset.contains("timeout_at = NULL"));
        assertTrue(reset.contains("AND status = ?"));
        assertSnapshotColumnsAbsent(triggerUpdate);
        assertSnapshotColumnsAbsent(instanceUpdate);
        for (String sql : List.of(running, paused, resumed, progress, completed, reset)) {
            assertSnapshotColumnsAbsent(sql);
        }
        assertEquals(4, count(readResource(TRIGGER_MAPPER), "<update "));
        assertEquals(54, count(readResource(INSTANCE_MAPPER), "<update "));
    }

    @Test
    void runtimeSqlShouldUseOnlyBoundParametersAndIfjTables() throws IOException {
        for (String mapper : MAPPERS) {
            String content = readResource(mapper);
            assertFalse(content.contains("${"), mapper);
            assertFalse(content.contains("CREATE TABLE"), mapper);
        }
    }

    @Test
    void continuationRecoveryShouldIgnoreRootsWithCurrentWaitingRows() throws IOException {
        String mapper = readResource(EVENT_WAIT_MAPPER);

        assertTrue(mapper.contains("selectResolvedRootIds"));
        assertTrue(mapper.contains("NOT EXISTS"));
        assertTrue(mapper.contains("pending.root_instance_id = " + "w.root_instance_id"));
        assertTrue(mapper.contains("pending.status = 'WAITING'"));
    }

    private Configuration parseMappers() throws IOException {
        Configuration configuration = new Configuration();
        configuration.setMapUnderscoreToCamelCase(true);
        for (String mapper : MAPPERS) {
            ClassPathResource resource = new ClassPathResource(mapper);
            try (var input = resource.getInputStream()) {
                new XMLMapperBuilder(input, configuration, mapper, configuration.getSqlFragments())
                        .parse();
            }
        }
        return configuration;
    }

    private String readResource(String name) throws IOException {
        ClassPathResource resource = new ClassPathResource(name);
        try (var input = resource.getInputStream()) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private List<String> declaredFields(Class<?> type) {
        return Arrays.stream(type.getDeclaredFields())
                .filter(field -> !field.isSynthetic())
                .map(Field::getName)
                .toList();
    }

    private Class<?> declaredFieldType(Class<?> type, String fieldName) {
        try {
            return type.getDeclaredField(fieldName).getType();
        } catch (NoSuchFieldException exception) {
            throw new AssertionError(exception);
        }
    }

    private List<String> resultMappings(
            Configuration configuration, Class<?> mapperType, String resultMapId) {
        return configuration
                .getResultMap(mapperType.getName() + "." + resultMapId)
                .getResultMappings()
                .stream()
                .map(mapping -> mapping.getProperty() + ":" + mapping.getColumn())
                .toList();
    }

    private String boundSql(
            Configuration configuration,
            Class<?> mapperType,
            String statementId,
            Object parameter) {
        return normalizedSql(
                configuration
                        .getMappedStatement(mapperType.getName() + "." + statementId)
                        .getBoundSql(parameter)
                        .getSql());
    }

    private List<String> parameterProperties(
            Configuration configuration,
            Class<?> mapperType,
            String statementId,
            Object parameter) {
        return configuration
                .getMappedStatement(mapperType.getName() + "." + statementId)
                .getBoundSql(parameter)
                .getParameterMappings()
                .stream()
                .map(mapping -> mapping.getProperty())
                .toList();
    }

    private void assertSnapshotColumnsAbsent(String sql) {
        assertFalse(sql.matches(".*(?:SET|,) definition_id =.*"), sql);
        assertFalse(sql.matches(".*(?:SET|,) definition_node_id =.*"), sql);
        assertFalse(sql.matches(".*(?:SET|,) task_definition_id =.*"), sql);
    }

    private int count(String value, String token) {
        return value.split(java.util.regex.Pattern.quote(token), -1).length - 1;
    }

    private String normalizedSql(String sql) {
        return sql.replaceAll("\\s+", " ").trim();
    }
}
