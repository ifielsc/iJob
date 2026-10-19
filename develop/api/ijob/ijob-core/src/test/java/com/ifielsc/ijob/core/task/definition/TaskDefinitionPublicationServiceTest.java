// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.core.task.definition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ifielsc.ijob.common.exception.BusinessException;
import com.ifielsc.ijob.common.response.ResultCode;
import com.ifielsc.ijob.contract.enums.PluginType;
import com.ifielsc.ijob.contract.task.TaskUpsertRequest;
import com.ifielsc.ijob.infra.entity.task.TaskDefinition;
import com.ifielsc.ijob.infra.mapper.group.DefinitionGroupRow;
import com.ifielsc.ijob.infra.mapper.task.TaskCurrentDefinitionAdvance;
import com.ifielsc.ijob.infra.mapper.task.TaskCurrentDefinitionInitialization;
import com.ifielsc.ijob.infra.mapper.task.TaskDefinitionGroupMapper;
import com.ifielsc.ijob.infra.mapper.task.TaskDefinitionMapper;
import com.ifielsc.ijob.infra.mapper.task.TaskIdentityRetirement;
import com.ifielsc.ijob.infra.mapper.task.TaskPublicationIdentityInsert;
import com.ifielsc.ijob.infra.mapper.task.TaskPublicationIdentityRow;
import com.ifielsc.ijob.infra.mapper.task.TaskPublicationMapper;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

class TaskDefinitionPublicationServiceTest {
    @Test
    void createShouldWriteIdentityRevisionOneAndInitializePointer() {
        Harness harness = new Harness();
        TaskUpsertRequest request = request();
        request.setName("  task  ");
        request.setRetryTimes(null);
        request.setRetryInterval(null);
        request.setEnabled(null);

        TaskPublicationResult result = harness.service.create(request);

        assertEquals(new TaskPublicationResult(100L, 200L, 1L), result);
        assertEquals(
                List.of("identity-insert", "definition-insert:1", "pointer-initialize"),
                harness.order);
        TaskPublicationIdentityInsert identity = harness.publication.insertedIdentity;
        TaskDefinition definition = harness.definitions.inserted;
        assertEquals("task", identity.getName());
        assertEquals(0, identity.getRetryTimes());
        assertEquals(0, identity.getRetryInterval());
        assertTrue(identity.getEnabled());
        assertMirrorsEqual(identity, definition);
        assertEquals(identity.getCreateTime(), definition.getCreateTime());
        assertEquals(identity.getCreateTime(), identity.getUpdateTime());
        assertEquals(100L, definition.getTaskId());
        assertEquals(1L, definition.getRevision());
        assertEquals(100L, harness.publication.initialization.getTaskId());
        assertEquals(200L, harness.publication.initialization.getDefinitionId());
        assertEquals(identity.getCreateTime(), harness.publication.initialization.getUpdateTime());
    }

    @Test
    void createShouldPersistPluginSpecificSnapshot() {
        TaskPluginConfigSnapshotter snapshotter =
                new TaskPluginConfigSnapshotter() {
                    @Override
                    public PluginType supportType() {
                        return PluginType.LOCAL_JAR;
                    }

                    @Override
                    public String snapshot(String pluginConfig) {
                        assertEquals("draft", pluginConfig);
                        return "captured";
                    }
                };
        Harness harness = new Harness(List.of(snapshotter));
        TaskUpsertRequest request = request();
        request.setPluginType(PluginType.LOCAL_JAR);
        request.setPluginConfig("draft");

        harness.service.create(request);

        assertEquals("captured", harness.definitions.inserted.getPluginConfig());
        assertEquals("captured", request.getPluginConfig());
    }

    @Test
    void createShouldPersistCanonicalGroupsOnThePublishedDefinition() {
        Harness harness = new Harness();
        TaskUpsertRequest request = request();
        request.setGroups(List.of(" GPU ", "cpu", "gpu"));

        harness.service.create(request);

        assertEquals(List.of("200:cpu", "200:gpu"), harness.groups.inserted);
        assertEquals(List.of("cpu", "gpu"), request.getGroups());
    }

    @Test
    void publishShouldLockValidateWriteNextRevisionAndAdvance() {
        Harness harness = new Harness();
        harness.publication.identity = identity(10L, 1000L, 4L);
        harness.definitions.selected = definition(1000L, 10L, 4L);
        TaskUpsertRequest request = request();
        request.setName("next task");
        request.setRetryTimes(3);
        request.setRetryInterval(20);
        request.setEnabled(false);

        TaskPublicationResult result = harness.service.publish(10L, 4L, request);

        assertEquals(new TaskPublicationResult(10L, 200L, 5L), result);
        assertEquals(
                List.of(
                        "identity-lock",
                        "definition-select",
                        "definition-insert:5",
                        "pointer-advance"),
                harness.order);
        TaskCurrentDefinitionAdvance advance = harness.publication.advance;
        assertEquals(10L, advance.getTaskId());
        assertEquals(1000L, advance.getExpectedDefinitionId());
        assertEquals(4L, advance.getExpectedRevision());
        assertEquals(200L, advance.getDefinitionId());
        assertEquals(5L, advance.getNextRevision());
        assertEquals("next task", advance.getName());
        assertEquals(3, advance.getRetryTimes());
        assertEquals(20, advance.getRetryInterval());
        assertFalse(advance.getEnabled());
        assertEquals(harness.definitions.inserted.getCreateTime(), advance.getUpdateTime());
    }

    @Test
    void invalidIdsAndRequestShouldFailBeforeMapperCalls() {
        Harness harness = new Harness();

        assertCode(ResultCode.PARAM_ERROR, () -> harness.service.create(null));
        assertCode(ResultCode.PARAM_ERROR, () -> harness.service.publish(0L, 1L, request()));
        assertCode(ResultCode.PARAM_ERROR, () -> harness.service.publish(10L, 0L, request()));
        assertCode(ResultCode.PARAM_ERROR, () -> harness.service.publish(10L, 1L, null));
        assertTrue(harness.order.isEmpty());
    }

    @Test
    void validatorShouldRejectEveryFrozenInvalidFieldBeforeWrites() {
        List<Consumer<TaskUpsertRequest>> invalidators =
                List.of(
                        value -> value.setName(" "),
                        value -> value.setName("x".repeat(129)),
                        value -> value.setPluginType(null),
                        value -> value.setPluginConfig(" "),
                        value -> value.setRetryTimes(-1),
                        value -> value.setRetryInterval(-1),
                        value -> value.setTimeout(0),
                        value -> value.setDescription("x".repeat(501)));

        for (Consumer<TaskUpsertRequest> invalidator : invalidators) {
            Harness harness = new Harness();
            TaskUpsertRequest request = request();
            invalidator.accept(request);

            assertCode(ResultCode.PARAM_ERROR, () -> harness.service.create(request));
            assertTrue(harness.order.isEmpty());
        }
    }

    @Test
    void publishShouldClassifyIdentityAdmissionFailures() {
        assertPublishCode(new Harness(), null, ResultCode.NOT_FOUND);
        assertPublishCode(new Harness(), identity(11L, 1000L, 4L), ResultCode.SYSTEM_ERROR);
        assertPublishCode(new Harness(), identity(10L, null, null), ResultCode.CONFLICT);
        assertPublishCode(new Harness(), identity(10L, 1000L, null), ResultCode.SYSTEM_ERROR);
        assertPublishCode(new Harness(), identity(10L, -1L, 4L), ResultCode.SYSTEM_ERROR);
        assertPublishCode(new Harness(), identity(10L, 1000L, 5L), ResultCode.CONFLICT);

        Harness retired = new Harness();
        TaskPublicationIdentityRow retiredIdentity = identity(10L, 1000L, 4L);
        retiredIdentity.setRetired(true);
        retiredIdentity.setRetiredTime(LocalDateTime.now());
        assertPublishCode(retired, retiredIdentity, ResultCode.CONFLICT);
    }

    @Test
    void publishShouldRejectBrokenRetiredStateAsIntegrityError() {
        for (TaskPublicationIdentityRow identity :
                List.of(
                        identityWithRetired(null, null),
                        identityWithRetired(false, LocalDateTime.now()),
                        identityWithRetired(true, null))) {
            assertPublishCode(new Harness(), identity, ResultCode.SYSTEM_ERROR);
        }
    }

    @Test
    void publishShouldRejectCurrentDefinitionOwnershipMismatch() {
        List<TaskDefinition> invalidDefinitions = new ArrayList<>();
        invalidDefinitions.add(null);
        invalidDefinitions.add(definition(1001L, 10L, 4L));
        invalidDefinitions.add(definition(1000L, 11L, 4L));
        invalidDefinitions.add(definition(1000L, 10L, 5L));

        for (TaskDefinition invalid : invalidDefinitions) {
            Harness harness = readyPublishHarness();
            harness.definitions.selected = invalid;

            assertCode(ResultCode.SYSTEM_ERROR, () -> harness.service.publish(10L, 4L, request()));
            assertEquals(List.of("identity-lock", "definition-select"), harness.order);
        }
    }

    @Test
    void publishShouldRejectRevisionOverflowAsConflict() {
        Harness harness = new Harness();
        harness.publication.identity = identity(10L, 1000L, Long.MAX_VALUE);
        harness.definitions.selected = definition(1000L, 10L, Long.MAX_VALUE);

        assertCode(
                ResultCode.CONFLICT, () -> harness.service.publish(10L, Long.MAX_VALUE, request()));
        assertEquals(List.of("identity-lock", "definition-select"), harness.order);
    }

    @Test
    void everyCreateWriteFailureShouldBeSystemError() {
        for (CreateFailure failure : CreateFailure.values()) {
            Harness harness = new Harness();
            switch (failure) {
                case IDENTITY_ROWS -> harness.publication.insertRows = 0;
                case IDENTITY_ID -> harness.publication.generateIdentityId = false;
                case DEFINITION_ROWS -> harness.definitions.insertRows = 0;
                case DEFINITION_ID -> harness.definitions.generateId = false;
                case POINTER_ROWS -> harness.publication.initialRows = 0;
            }

            assertCode(ResultCode.SYSTEM_ERROR, () -> harness.service.create(request()));
            assertFalse(harness.order.isEmpty());
        }
    }

    @Test
    void publishWriteFailuresShouldStopAtFailingOperation() {
        for (PublishFailure failure : PublishFailure.values()) {
            Harness harness = readyPublishHarness();
            switch (failure) {
                case DEFINITION_ROWS -> harness.definitions.insertRows = 0;
                case DEFINITION_ID -> harness.definitions.generateId = false;
                case POINTER_ROWS -> harness.publication.advanceRows = 0;
            }

            assertCode(ResultCode.SYSTEM_ERROR, () -> harness.service.publish(10L, 4L, request()));
            assertFalse(harness.order.isEmpty());
        }
    }

    @Test
    void mapperExceptionShouldPropagateWithoutRetrying() {
        Harness harness = new Harness();
        IllegalStateException failure = new IllegalStateException("lock");
        harness.publication.failure = failure;

        IllegalStateException thrown =
                assertThrows(
                        IllegalStateException.class,
                        () -> harness.service.publish(10L, 1L, request()));

        assertSame(failure, thrown);
        assertEquals(List.of("identity-lock"), harness.order);
    }

    @Test
    void publicEntrypointsShouldDeclareRequiredWriteTransactions() throws Exception {
        for (Method method :
                List.of(
                        TaskDefinitionPublicationService.class.getMethod(
                                "create", TaskUpsertRequest.class),
                        TaskDefinitionPublicationService.class.getMethod(
                                "publish", Long.class, Long.class, TaskUpsertRequest.class))) {
            Transactional annotation = method.getAnnotation(Transactional.class);
            assertNotNull(annotation);
            assertFalse(annotation.readOnly());
            assertEquals(
                    org.springframework.transaction.annotation.Propagation.REQUIRED,
                    annotation.propagation());
        }
    }

    private static void assertPublishCode(
            Harness harness, TaskPublicationIdentityRow identity, ResultCode expected) {
        harness.publication.identity = identity;
        assertCode(expected, () -> harness.service.publish(10L, 4L, request()));
        assertEquals(List.of("identity-lock"), harness.order);
    }

    private static void assertCode(ResultCode expected, Runnable invocation) {
        BusinessException exception = assertThrows(BusinessException.class, invocation::run);
        assertEquals(expected, exception.getCode());
    }

    private static Harness readyPublishHarness() {
        Harness harness = new Harness();
        harness.publication.identity = identity(10L, 1000L, 4L);
        harness.definitions.selected = definition(1000L, 10L, 4L);
        return harness;
    }

    private static TaskPublicationIdentityRow identity(Long id, Long definitionId, Long revision) {
        TaskPublicationIdentityRow identity = new TaskPublicationIdentityRow();
        identity.setId(id);
        identity.setCurrentTaskDefinitionId(definitionId);
        identity.setCurrentRevision(revision);
        identity.setRetired(false);
        identity.setRetiredTime(null);
        identity.setCreateTime(LocalDateTime.now());
        identity.setUpdateTime(LocalDateTime.now());
        return identity;
    }

    private static TaskPublicationIdentityRow identityWithRetired(
            Boolean retired, LocalDateTime retiredTime) {
        TaskPublicationIdentityRow identity = identity(10L, 1000L, 4L);
        identity.setRetired(retired);
        identity.setRetiredTime(retiredTime);
        return identity;
    }

    private static TaskDefinition definition(Long id, Long taskId, Long revision) {
        TaskDefinition definition = new TaskDefinition();
        definition.setId(id);
        definition.setTaskId(taskId);
        definition.setRevision(revision);
        return definition;
    }

    private static TaskUpsertRequest request() {
        TaskUpsertRequest request = new TaskUpsertRequest();
        request.setName("task");
        request.setPluginType(PluginType.SCRIPT);
        request.setPluginConfig("{}");
        request.setRetryTimes(2);
        request.setRetryInterval(10);
        request.setTimeout(60);
        request.setEnabled(true);
        request.setDescription("description");
        return request;
    }

    private static void assertMirrorsEqual(
            TaskPublicationIdentityInsert identity, TaskDefinition definition) {
        assertEquals(identity.getName(), definition.getName());
        assertEquals(identity.getPluginType(), definition.getPluginType());
        assertEquals(identity.getPluginConfig(), definition.getPluginConfig());
        assertEquals(identity.getRetryTimes(), definition.getRetryTimes());
        assertEquals(identity.getRetryInterval(), definition.getRetryInterval());
        assertEquals(identity.getTimeout(), definition.getTimeout());
        assertEquals(identity.getEnabled(), definition.getEnabled());
        assertEquals(identity.getDescription(), definition.getDescription());
    }

    private enum CreateFailure {
        IDENTITY_ROWS,
        IDENTITY_ID,
        DEFINITION_ROWS,
        DEFINITION_ID,
        POINTER_ROWS
    }

    private enum PublishFailure {
        DEFINITION_ROWS,
        DEFINITION_ID,
        POINTER_ROWS
    }

    private static final class Harness {
        private final List<String> order = new ArrayList<>();
        private final RecordingPublicationMapper publication =
                new RecordingPublicationMapper(order);
        private final RecordingDefinitionMapper definitions = new RecordingDefinitionMapper(order);
        private final RecordingDefinitionGroupMapper groups = new RecordingDefinitionGroupMapper();
        private final TaskDefinitionPublicationService service;

        private Harness() {
            this(List.of());
        }

        private Harness(List<TaskPluginConfigSnapshotter> snapshotters) {
            service =
                    new TaskDefinitionPublicationService(
                            new TaskDefinitionValidator(),
                            new TaskPluginAvailabilityService(List.of()),
                            new TaskPluginConfigPublicationValidationService(
                                    List.of(), null, null, null),
                            new TaskPluginConfigSnapshotService(snapshotters),
                            publication,
                            definitions,
                            groups);
        }
    }

    private static final class RecordingDefinitionGroupMapper implements TaskDefinitionGroupMapper {
        private final List<String> inserted = new ArrayList<>();

        @Override
        public int insert(Long definitionId, String groupCode, LocalDateTime createTime) {
            inserted.add(definitionId + ":" + groupCode);
            return 1;
        }

        @Override
        public List<DefinitionGroupRow> selectByDefinitionIds(List<Long> definitionIds) {
            return List.of();
        }
    }

    private static final class RecordingPublicationMapper implements TaskPublicationMapper {
        private final List<String> order;
        private TaskPublicationIdentityRow identity;
        private TaskPublicationIdentityInsert insertedIdentity;
        private TaskCurrentDefinitionInitialization initialization;
        private TaskCurrentDefinitionAdvance advance;
        private int insertRows = 1;
        private int initialRows = 1;
        private int advanceRows = 1;
        private boolean generateIdentityId = true;
        private RuntimeException failure;

        private RecordingPublicationMapper(List<String> order) {
            this.order = order;
        }

        @Override
        public TaskPublicationIdentityRow selectIdentityForUpdate(Long taskId) {
            order.add("identity-lock");
            if (failure != null) {
                throw failure;
            }
            return identity;
        }

        @Override
        public int insertIdentity(TaskPublicationIdentityInsert identity) {
            order.add("identity-insert");
            if (generateIdentityId) {
                identity.setId(100L);
            }
            insertedIdentity = identity;
            return insertRows;
        }

        @Override
        public int initializeCurrentDefinition(TaskCurrentDefinitionInitialization initialization) {
            order.add("pointer-initialize");
            this.initialization = initialization;
            return initialRows;
        }

        @Override
        public int advanceCurrentDefinition(TaskCurrentDefinitionAdvance advance) {
            order.add("pointer-advance");
            this.advance = advance;
            return advanceRows;
        }

        @Override
        public int retireIdentity(TaskIdentityRetirement retirement) {
            throw unsupported();
        }
    }

    private static final class RecordingDefinitionMapper implements TaskDefinitionMapper {
        private final List<String> order;
        private TaskDefinition selected;
        private TaskDefinition inserted;
        private int insertRows = 1;
        private boolean generateId = true;

        private RecordingDefinitionMapper(List<String> order) {
            this.order = order;
        }

        @Override
        public TaskDefinition selectById(Long id) {
            order.add("definition-select");
            return selected;
        }

        @Override
        public List<TaskDefinition> selectListByTaskId(Long taskId) {
            throw unsupported();
        }

        @Override
        public int insert(TaskDefinition definition) {
            order.add("definition-insert:" + definition.getRevision());
            if (generateId) {
                definition.setId(200L);
            }
            inserted = definition;
            return insertRows;
        }
    }

    private static UnsupportedOperationException unsupported() {
        return new UnsupportedOperationException("not used by publication tests");
    }
}
