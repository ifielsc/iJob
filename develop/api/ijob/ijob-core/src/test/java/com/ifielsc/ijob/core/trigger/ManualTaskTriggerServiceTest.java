// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.core.trigger;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ifielsc.ijob.contract.enums.PluginType;
import com.ifielsc.ijob.contract.runtime.ManualTriggerRequest;
import com.ifielsc.ijob.core.execute.variable.VariableSnapshotService;
import com.ifielsc.ijob.core.task.definition.TaskAdmissionResolver;
import com.ifielsc.ijob.core.task.definition.TaskAdmissionSnapshot;
import com.ifielsc.ijob.core.task.definition.TaskExecutionSpec;
import com.ifielsc.ijob.infra.entity.runtime.Trigger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Map;
import java.util.stream.Stream;

class ManualTaskTriggerServiceTest {
    @Test
    void unavailableRunnerShouldRejectBeforeCreatingOrDispatchingTrigger() {
        RecordingTriggerRuntimeService runtimeService = new RecordingTriggerRuntimeService();
        RecordingDispatcher dispatcher = new RecordingDispatcher();
        ManualTaskTriggerService service =
                new ManualTaskTriggerService(
                        new RecordingResolver(snapshot(PluginType.LOCAL_JAR)),
                        runtimeService,
                        dispatcher,
                        new VariableSnapshotService(new ObjectMapper()),
                        new com.ifielsc.ijob.core.execute.task.TaskRunnerFactory(
                                java.util.List.of()));
        var failure =
                org.junit.jupiter.api.Assertions.assertThrows(
                        com.ifielsc.ijob.common.exception.BusinessException.class,
                        () -> service.trigger(9L));
        assertEquals(com.ifielsc.ijob.common.response.ResultCode.CONFLICT, failure.getCode());
        org.junit.jupiter.api.Assertions.assertNull(runtimeService.snapshot);
        org.junit.jupiter.api.Assertions.assertNull(dispatcher.triggerId);
    }

    @Test
    void shouldCreateSnapshotTriggerAndDispatchEnabledScriptTask() {
        TaskAdmissionSnapshot snapshot = snapshot(PluginType.SCRIPT);
        RecordingResolver resolver = new RecordingResolver(snapshot);
        RecordingTriggerRuntimeService runtimeService = new RecordingTriggerRuntimeService();
        RecordingDispatcher dispatcher = new RecordingDispatcher();
        ManualTaskTriggerService service =
                new ManualTaskTriggerService(
                        resolver,
                        runtimeService,
                        dispatcher,
                        new VariableSnapshotService(new ObjectMapper()));

        ManualTriggerRequest request = new ManualTriggerRequest();
        request.setRequestId("task-request-7");
        request.setVariables(Map.of("batch", 7));
        Long triggerId = service.trigger(9L, request);

        assertEquals(71L, triggerId);
        assertEquals(9L, resolver.taskId);
        assertEquals(snapshot, runtimeService.snapshot);
        assertEquals("{\"batch\":7}", runtimeService.variables);
        assertEquals("task-request-7", runtimeService.requestId);
        assertEquals(71L, dispatcher.triggerId);
    }

    @ParameterizedTest
    @MethodSource("pluginTypes")
    void shouldCreateManualTriggerForEveryPublishedPluginType(PluginType pluginType) {
        RecordingTriggerRuntimeService runtimeService = new RecordingTriggerRuntimeService();
        RecordingDispatcher dispatcher = new RecordingDispatcher();
        ManualTaskTriggerService service =
                new ManualTaskTriggerService(
                        new RecordingResolver(snapshot(pluginType)),
                        runtimeService,
                        dispatcher,
                        new VariableSnapshotService(new ObjectMapper()));

        Long triggerId = service.trigger(9L);

        assertEquals(pluginType, runtimeService.snapshot.executionSpec().getPluginType());
        assertEquals(71L, triggerId);
        assertEquals(71L, dispatcher.triggerId);
    }

    private static Stream<PluginType> pluginTypes() {
        return Stream.of(PluginType.values());
    }

    private static TaskAdmissionSnapshot snapshot(PluginType pluginType) {
        TaskExecutionSpec spec =
                new TaskExecutionSpec("task", pluginType, "secret-config", 0, 0, null, true, null);
        return new TaskAdmissionSnapshot(9L, 41L, 3L, spec);
    }

    private static final class RecordingResolver extends TaskAdmissionResolver {
        private final TaskAdmissionSnapshot snapshot;
        private Long taskId;

        private RecordingResolver(TaskAdmissionSnapshot snapshot) {
            super(null, null);
            this.snapshot = snapshot;
        }

        @Override
        public TaskAdmissionSnapshot resolve(Long id) {
            taskId = id;
            return snapshot;
        }
    }

    private static final class RecordingTriggerRuntimeService extends TriggerRuntimeService {
        private TaskAdmissionSnapshot snapshot;
        private String variables;
        private String requestId;

        private RecordingTriggerRuntimeService() {
            super(null);
        }

        @Override
        public Trigger createManualTaskTrigger(
                TaskAdmissionSnapshot resolvedSnapshot,
                String resolvedVariables,
                String resolvedRequestId) {
            snapshot = resolvedSnapshot;
            variables = resolvedVariables;
            requestId = resolvedRequestId;
            Trigger trigger = new Trigger();
            trigger.setId(71L);
            return trigger;
        }
    }

    private static final class RecordingDispatcher extends TriggerCommandDispatcher {
        private Long triggerId;

        private RecordingDispatcher() {
            super(null);
        }

        @Override
        public void dispatchAfterCommit(Long id) {
            triggerId = id;
        }
    }
}
