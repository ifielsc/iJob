package com.ifielsc.ijob.contract.job;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

class JobContractBoundaryTest {
    @Test
    void writeRequestShouldExcludePersistenceAndAdjacentAggregateFields() {
        assertEquals(
                Set.of(
                        "name",
                        "timeout",
                        "enabled",
                        "description",
                        "groups",
                        "nodes",
                        "edges",
                        "layout"),
                instanceFieldNames(JobUpsertRequest.class));
        assertFalse(instanceFieldNames(JobUpsertRequest.class).contains("id"));
        assertFalse(instanceFieldNames(JobUpsertRequest.class).contains("schedules"));
        assertFalse(instanceFieldNames(JobUpsertRequest.class).contains("notifyBindings"));
        assertFalse(instanceFieldNames(JobUpsertRequest.class).contains("concurrencyType"));
    }

    @Test
    void nodeAndPolicyRequestsShouldExcludePersistenceAndDisplayFields() {
        assertEquals(
                Set.of("nodeId", "name", "kind", "nodeType", "refId", "gateway", "policy"),
                instanceFieldNames(JobNodeRequest.class));
        assertEquals(
                Set.of("failStrategy", "executeCondition"),
                instanceFieldNames(JobNodePolicyRequest.class));
        assertFalse(instanceFieldNames(JobNodeRequest.class).contains("id"));
        assertFalse(instanceFieldNames(JobNodeRequest.class).contains("refName"));
        assertFalse(instanceFieldNames(JobNodePolicyRequest.class).contains("retryTimes"));
        assertFalse(instanceFieldNames(JobNodePolicyRequest.class).contains("retryInterval"));
    }

    @Test
    void edgeRequestShouldUseOnlyRequestLocalNodeIdentifiers() throws Exception {
        assertEquals(
                Set.of(
                        "fromNodeId",
                        "toNodeId",
                        "routeKey",
                        "priority",
                        "defaultRoute",
                        "condition"),
                instanceFieldNames(JobEdgeRequest.class));
        assertEquals(String.class, JobEdgeRequest.class.getDeclaredField("fromNodeId").getType());
        assertEquals(String.class, JobEdgeRequest.class.getDeclaredField("toNodeId").getType());
    }

    @Test
    void edgeResponseShouldUsePersistedNumericNodeIdentifiers() throws Exception {
        assertEquals(
                Set.of(
                        "id",
                        "fromNodeId",
                        "toNodeId",
                        "routeKey",
                        "priority",
                        "defaultRoute",
                        "conditionVersion",
                        "condition"),
                instanceFieldNames(JobEdgeResponse.class));
        assertEquals(Long.class, JobEdgeResponse.class.getDeclaredField("fromNodeId").getType());
        assertEquals(Long.class, JobEdgeResponse.class.getDeclaredField("toNodeId").getType());
    }

    @Test
    void detailResponseShouldContainOnlySafeContractTypes() throws Exception {
        assertEquals(
                Set.of(
                        "id",
                        "currentDefinitionId",
                        "currentRevision",
                        "name",
                        "timeout",
                        "enabled",
                        "description",
                        "groups",
                        "nodes",
                        "edges",
                        "layout",
                        "publishedAt",
                        "current",
                        "schedules",
                        "notifyBindings",
                        "createTime",
                        "updateTime"),
                instanceFieldNames(JobDetailResponse.class));
        assertEquals(Long.class, JobNodeResponse.class.getDeclaredField("id").getType());
        assertEquals(
                JobNodePolicyResponse.class,
                JobNodeResponse.class.getDeclaredField("policy").getType());
    }

    @Test
    void listAndPageContractsShouldExposeUsefulFixedFields() {
        assertEquals(
                Set.of(
                        "id",
                        "currentDefinitionId",
                        "currentRevision",
                        "name",
                        "timeout",
                        "enabled",
                        "description",
                        "groups",
                        "nodeCount",
                        "updateTime"),
                instanceFieldNames(JobListDTO.class));
        assertEquals(
                Set.of("id", "name", "enabled", "group"), instanceFieldNames(JobPageQuery.class));
    }

    private Set<String> instanceFieldNames(Class<?> type) {
        return Arrays.stream(type.getDeclaredFields())
                .filter(field -> !Modifier.isStatic(field.getModifiers()))
                .map(Field::getName)
                .collect(Collectors.toSet());
    }
}
