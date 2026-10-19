// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.api.configuration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ifielsc.ijob.contract.notify.NotificationDeliveryDTO;
import com.ifielsc.ijob.contract.notify.NotifySendLogDTO;
import com.ifielsc.ijob.contract.runtime.GatewayDecisionDTO;
import com.ifielsc.ijob.contract.runtime.InstanceDTO;
import com.ifielsc.ijob.contract.runtime.InstanceLogDTO;
import com.ifielsc.ijob.contract.runtime.RunTreeDTO;
import com.ifielsc.ijob.contract.runtime.RuntimeCommandDTO;
import com.ifielsc.ijob.contract.runtime.TriggerDTO;
import com.ifielsc.ijob.contract.schedule.ScheduleDTO;
import com.ifielsc.ijob.contract.task.TaskDetailDTO;
import com.ifielsc.ijob.contract.task.TaskListDTO;
import com.ifielsc.ijob.contract.task.TaskPublicationDTO;
import com.ifielsc.ijob.contract.task.TaskVersionDTO;

import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.util.List;
import java.util.Map;

class TaskJsonConfigurationTest {
    private final ObjectMapper mapper;

    TaskJsonConfigurationTest() {
        Jackson2ObjectMapperBuilder builder = new Jackson2ObjectMapperBuilder();
        new TaskJsonConfiguration().taskIdentifiers().customize(builder);
        mapper = builder.build();
    }

    @Test
    void logSnapshotsKeepNestedLongIdentifiersExact() {
        InstanceLogDTO log = new InstanceLogDTO();
        log.setId(Long.MAX_VALUE);
        log.setInstanceId(9007199254740993L);
        var snapshot =
                new com.ifielsc.ijob.contract.runtime.InstanceLogSnapshotDTO(List.of(log), true);
        JsonNode json = mapper.valueToTree(snapshot);
        assertExact(json.get("rows").get(0), "id", Long.MAX_VALUE);
        assertExact(json.get("rows").get(0), "instanceId", 9007199254740993L);
        assertTrue(json.get("truncated").asBoolean());
    }

    @Test
    void taskHistoryMetadataKeepsAllLongIdentifiersExact() {
        TaskVersionDTO version = new TaskVersionDTO();
        version.setTaskId(9007199254740993L);
        version.setDefinitionId(Long.MAX_VALUE);
        version.setRevision(9007199254740995L);
        version.setCurrent(true);
        JsonNode json = mapper.valueToTree(version);
        assertExact(json, "taskId", 9007199254740993L);
        assertExact(json, "definitionId", Long.MAX_VALUE);
        assertExact(json, "revision", 9007199254740995L);
        assertTrue(json.get("current").isBoolean());
    }

    @Test
    void scheduleIdsAreStringsButInputVariablesRemainNumeric() {
        ScheduleDTO schedule = new ScheduleDTO();
        schedule.setId(Long.MAX_VALUE);
        schedule.setTargetId(Long.MAX_VALUE - 1);
        schedule.setCalendarId(Long.MAX_VALUE - 2);
        schedule.setIntervalSec(300);
        schedule.setVariables(Map.of("large", Long.MAX_VALUE));
        JsonNode json = mapper.valueToTree(schedule);
        assertExact(json, "id", Long.MAX_VALUE);
        assertExact(json, "targetId", Long.MAX_VALUE - 1);
        assertExact(json, "calendarId", Long.MAX_VALUE - 2);
        assertTrue(json.get("intervalSec").isInt());
        assertTrue(json.get("variables").get("large").isIntegralNumber());
        assertEquals(Long.MAX_VALUE, json.get("variables").get("large").longValue());
    }

    @Test
    void taskAndPublicationIdentifiersRemainExactWhileCountersRemainNumeric() throws Exception {
        TaskListDTO task = new TaskListDTO();
        task.setId(Long.MAX_VALUE);
        task.setCurrentTaskDefinitionId(Long.MAX_VALUE - 1);
        task.setCurrentRevision(12L);
        task.setRetryTimes(0);
        JsonNode json = mapper.readTree(mapper.writeValueAsString(task));
        assertExact(json, "id", Long.MAX_VALUE);
        assertExact(json, "currentTaskDefinitionId", Long.MAX_VALUE - 1);
        assertExact(json, "currentRevision", 12L);
        assertTrue(json.get("retryTimes").isInt());
        TaskDetailDTO detail = new TaskDetailDTO();
        detail.setId(Long.MAX_VALUE);
        assertExact(mapper.valueToTree(detail), "id", Long.MAX_VALUE);
        TaskPublicationDTO result = new TaskPublicationDTO(Long.MAX_VALUE, Long.MAX_VALUE - 1, 1L);
        JsonNode publication = mapper.valueToTree(result);
        assertExact(publication, "taskId", Long.MAX_VALUE);
        assertExact(publication, "definitionId", Long.MAX_VALUE - 1);
        assertExact(publication, "revision", 1L);
    }

    @Test
    void runtimeReferencesAndLogCursorsRemainExactAndNullStaysNull() {
        TriggerDTO trigger = new TriggerDTO();
        trigger.setId(Long.MAX_VALUE);
        trigger.setTargetId(Long.MAX_VALUE - 1);
        trigger.setTaskDefinitionId(Long.MAX_VALUE - 2);
        JsonNode json = mapper.valueToTree(trigger);
        assertExact(json, "id", Long.MAX_VALUE);
        assertExact(json, "targetId", Long.MAX_VALUE - 1);
        assertExact(json, "taskDefinitionId", Long.MAX_VALUE - 2);
        assertTrue(json.get("rootInstanceId").isNull());
        InstanceDTO instance = new InstanceDTO();
        instance.setId(Long.MAX_VALUE);
        instance.setTriggerId(Long.MAX_VALUE - 1);
        instance.setProgress(50);
        JsonNode instanceJson = mapper.valueToTree(instance);
        assertExact(instanceJson, "id", Long.MAX_VALUE);
        assertExact(instanceJson, "triggerId", Long.MAX_VALUE - 1);
        assertTrue(instanceJson.get("progress").isInt());
        InstanceLogDTO log = new InstanceLogDTO();
        log.setId(Long.MAX_VALUE);
        log.setInstanceId(Long.MAX_VALUE - 1);
        assertExact(mapper.valueToTree(log), "id", Long.MAX_VALUE);
        assertExact(mapper.valueToTree(log), "instanceId", Long.MAX_VALUE - 1);
    }

    @Test
    void nestedRuntimeAndNotificationReferencesAreStringsWithoutChangingNumericMeasures() {
        long large = 9007199254740993L;
        GatewayDecisionDTO decision = new GatewayDecisionDTO();
        decision.setId(Long.MAX_VALUE);
        decision.setJobInstanceId(large);
        decision.setDefinitionNodeId(large + 1);
        decision.setEvaluationNo(2);
        decision.setSelectedEdgeIds("[9007199254740993]");
        RunTreeDTO tree = new RunTreeDTO();
        tree.setRootInstanceId(large);
        tree.setGatewayDecisions(List.of(decision));
        tree.setMaxNodes(2000);
        JsonNode json = mapper.valueToTree(tree);
        assertExact(json, "rootInstanceId", large);
        assertTrue(json.get("maxNodes").isInt());
        JsonNode nested = json.get("gatewayDecisions").get(0);
        assertExact(nested, "id", Long.MAX_VALUE);
        assertExact(nested, "jobInstanceId", large);
        assertExact(nested, "definitionNodeId", large + 1);
        assertTrue(nested.get("evaluationNo").isInt());
        assertEquals("[9007199254740993]", nested.get("selectedEdgeIds").textValue());
        RuntimeCommandDTO command = new RuntimeCommandDTO();
        command.setInstanceId(large);
        command.setRootInstanceId(large);
        command.setTriggerId(Long.MAX_VALUE);
        JsonNode cmd = mapper.valueToTree(command);
        assertExact(cmd, "instanceId", large);
        assertExact(cmd, "rootInstanceId", large);
        assertExact(cmd, "triggerId", Long.MAX_VALUE);
        NotifySendLogDTO log = new NotifySendLogDTO();
        log.setId(Long.MAX_VALUE);
        log.setTargetId(large);
        log.setInstanceId(large);
        log.setNotifyBindingId(large + 1);
        log.setNotifyConfigId(large + 2);
        log.setDurationMs(308L);
        JsonNode send = mapper.valueToTree(log);
        for (String field :
                List.of("id", "targetId", "instanceId", "notifyBindingId", "notifyConfigId")) {
            assertTrue(send.get(field).isTextual(), field);
        }
        assertExact(send, "notifyConfigId", large + 2);
        assertTrue(send.get("durationMs").isNumber());
        assertTrue(send.get("operatorId").isNull());
    }

    @Test
    void notificationDeliveryIdentifiersAreExactAndAttemptsRemainNumeric() {
        NotificationDeliveryDTO delivery = new NotificationDeliveryDTO();
        delivery.setId(Long.MAX_VALUE);
        delivery.setNotifyConfigId(9007199254740993L);
        delivery.setTargetId(Long.MAX_VALUE - 1);
        delivery.setInstanceId(Long.MAX_VALUE - 2);
        delivery.setAttemptCount(1);
        delivery.setMaxAttempts(5);
        JsonNode json = mapper.valueToTree(delivery);
        assertExact(json, "id", Long.MAX_VALUE);
        assertExact(json, "notifyConfigId", 9007199254740993L);
        assertExact(json, "targetId", Long.MAX_VALUE - 1);
        assertExact(json, "instanceId", Long.MAX_VALUE - 2);
        assertTrue(json.get("attemptCount").isInt());
        assertTrue(json.get("maxAttempts").isInt());
        assertTrue(mapper.valueToTree(new NotificationDeliveryDTO()).get("instanceId").isNull());
    }

    private static void assertExact(JsonNode json, String field, long expected) {
        assertTrue(json.get(field).isTextual(), field);
        assertEquals(Long.toString(expected), json.get(field).textValue());
    }
}
