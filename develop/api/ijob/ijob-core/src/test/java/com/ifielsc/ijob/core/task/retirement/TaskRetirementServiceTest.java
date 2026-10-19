package com.ifielsc.ijob.core.task.retirement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ifielsc.ijob.common.exception.BusinessException;
import com.ifielsc.ijob.common.response.ResultCode;
import com.ifielsc.ijob.infra.entity.task.TaskDefinition;
import com.ifielsc.ijob.infra.mapper.schedule.ScheduleMapper;
import com.ifielsc.ijob.infra.mapper.task.TaskCurrentDefinitionAdvance;
import com.ifielsc.ijob.infra.mapper.task.TaskCurrentDefinitionInitialization;
import com.ifielsc.ijob.infra.mapper.task.TaskDefinitionMapper;
import com.ifielsc.ijob.infra.mapper.task.TaskIdentityRetirement;
import com.ifielsc.ijob.infra.mapper.task.TaskPublicationIdentityInsert;
import com.ifielsc.ijob.infra.mapper.task.TaskPublicationIdentityRow;
import com.ifielsc.ijob.infra.mapper.task.TaskPublicationMapper;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

class TaskRetirementServiceTest {
    @Test
    void activeVersionedTaskShouldValidateDefinitionAndRetireWithCas() {
        Harness harness = readyVersionedHarness();

        TaskRetirementResult result = harness.service.retire(10L);

        assertEquals(10L, result.taskId());
        assertFalse(result.alreadyRetired());
        assertNotNull(result.retiredTime());
        assertEquals(
                List.of(
                        "identity-lock",
                        "definition-select",
                        "identity-retire",
                        "schedule-disable"),
                harness.order);
        TaskIdentityRetirement retirement = harness.publication.retirement;
        assertEquals(10L, retirement.getTaskId());
        assertEquals(1000L, retirement.getExpectedDefinitionId());
        assertEquals(4L, retirement.getExpectedRevision());
        assertEquals(result.retiredTime(), retirement.getRetiredTime());
    }

    @Test
    void activeLegacyDoubleNullTaskShouldRetireWithoutDefinitionRead() {
        Harness harness = new Harness();
        harness.publication.identity = identity(10L, null, null);

        TaskRetirementResult result = harness.service.retire(10L);

        assertFalse(result.alreadyRetired());
        assertEquals(
                List.of("identity-lock", "identity-retire", "schedule-disable"), harness.order);
        assertNull(harness.publication.retirement.getExpectedDefinitionId());
        assertNull(harness.publication.retirement.getExpectedRevision());
    }

    @Test
    void alreadyRetiredTaskShouldBeIdempotentAndPreserveOriginalTime() {
        Harness harness = new Harness();
        LocalDateTime original = LocalDateTime.of(2026, 8, 15, 18, 0);
        TaskPublicationIdentityRow identity = identity(10L, 1000L, 4L);
        identity.setRetired(true);
        identity.setRetiredTime(original);
        harness.publication.identity = identity;

        TaskRetirementResult result = harness.service.retire(10L);

        assertEquals(new TaskRetirementResult(10L, original, true), result);
        assertEquals(List.of("identity-lock", "schedule-disable"), harness.order);
        assertNull(harness.publication.retirement);
    }

    @Test
    void invalidMissingAndMismatchedIdentityShouldBeClassified() {
        Harness invalid = new Harness();
        assertCode(ResultCode.PARAM_ERROR, () -> invalid.service.retire(0L));
        assertTrue(invalid.order.isEmpty());

        Harness missing = new Harness();
        assertCode(ResultCode.NOT_FOUND, () -> missing.service.retire(10L));
        assertEquals(List.of("identity-lock"), missing.order);

        Harness mismatched = new Harness();
        mismatched.publication.identity = identity(11L, 1000L, 4L);
        assertCode(ResultCode.SYSTEM_ERROR, () -> mismatched.service.retire(10L));
        assertEquals(List.of("identity-lock"), mismatched.order);
    }

    @Test
    void brokenRetiredStateShouldFailBeforeDefinitionOrWrite() {
        for (TaskPublicationIdentityRow identity :
                List.of(
                        identityWithRetired(null, null),
                        identityWithRetired(false, LocalDateTime.now()),
                        identityWithRetired(true, null))) {
            Harness harness = new Harness();
            harness.publication.identity = identity;

            assertCode(ResultCode.SYSTEM_ERROR, () -> harness.service.retire(10L));
            assertEquals(List.of("identity-lock"), harness.order);
        }
    }

    @Test
    void brokenPointerStateShouldFailBeforeDefinitionOrWrite() {
        for (TaskPublicationIdentityRow identity :
                List.of(
                        identity(10L, 1000L, null),
                        identity(10L, null, 4L),
                        identity(10L, -1L, 4L),
                        identity(10L, 1000L, 0L))) {
            Harness harness = new Harness();
            harness.publication.identity = identity;

            assertCode(ResultCode.SYSTEM_ERROR, () -> harness.service.retire(10L));
            assertEquals(List.of("identity-lock"), harness.order);
        }
    }

    @Test
    void activeVersionedTaskShouldRejectDefinitionOwnershipMismatch() {
        List<TaskDefinition> invalidDefinitions = new ArrayList<>();
        invalidDefinitions.add(null);
        invalidDefinitions.add(definition(1001L, 10L, 4L));
        invalidDefinitions.add(definition(1000L, 11L, 4L));
        invalidDefinitions.add(definition(1000L, 10L, 5L));

        for (TaskDefinition invalid : invalidDefinitions) {
            Harness harness = readyVersionedHarness();
            harness.definitions.selected = invalid;

            assertCode(ResultCode.SYSTEM_ERROR, () -> harness.service.retire(10L));
            assertEquals(List.of("identity-lock", "definition-select"), harness.order);
        }
    }

    @Test
    void conditionalRetireRowMismatchShouldBeSystemError() {
        Harness harness = readyVersionedHarness();
        harness.publication.retireRows = 0;

        assertCode(ResultCode.SYSTEM_ERROR, () -> harness.service.retire(10L));
        assertEquals("identity-retire", harness.order.get(harness.order.size() - 1));
    }

    @Test
    void mapperExceptionShouldPropagateWithoutRetrying() {
        Harness harness = new Harness();
        IllegalStateException failure = new IllegalStateException("lock");
        harness.publication.failure = failure;

        IllegalStateException thrown =
                assertThrows(IllegalStateException.class, () -> harness.service.retire(10L));

        assertSame(failure, thrown);
        assertEquals(List.of("identity-lock"), harness.order);
    }

    @Test
    void batchShouldPropagateLateFailureForTransactionRollback() {
        Harness harness = readyVersionedHarness();

        assertCode(ResultCode.SYSTEM_ERROR, () -> harness.service.retireBatch(List.of(10L, 11L)));

        assertEquals(
                List.of(
                        "identity-lock",
                        "definition-select",
                        "identity-retire",
                        "schedule-disable",
                        "identity-lock"),
                harness.order);
    }

    @Test
    void publicEntrypointShouldDeclareRequiredWriteTransaction() throws Exception {
        Method method = TaskRetirementService.class.getMethod("retire", Long.class);
        Transactional annotation = method.getAnnotation(Transactional.class);

        assertNotNull(annotation);
        assertFalse(annotation.readOnly());
        assertEquals(
                org.springframework.transaction.annotation.Propagation.REQUIRED,
                annotation.propagation());
    }

    private static Harness readyVersionedHarness() {
        Harness harness = new Harness();
        harness.publication.identity = identity(10L, 1000L, 4L);
        harness.definitions.selected = definition(1000L, 10L, 4L);
        return harness;
    }

    private static void assertCode(ResultCode expected, Runnable invocation) {
        BusinessException exception = assertThrows(BusinessException.class, invocation::run);
        assertEquals(expected, exception.getCode());
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

    private static final class Harness {
        private final List<String> order = new ArrayList<>();
        private final RecordingPublicationMapper publication =
                new RecordingPublicationMapper(order);
        private final RecordingDefinitionMapper definitions = new RecordingDefinitionMapper(order);
        private final ScheduleMapper schedules = scheduleMapper(order);
        private final TaskRetirementService service =
                new TaskRetirementService(
                        publication,
                        definitions,
                        schedules,
                        (com.ifielsc.ijob.infra.mapper.event.EventBindingMapper)
                                Proxy.newProxyInstance(
                                        getClass().getClassLoader(),
                                        new Class<?>[] {
                                            com.ifielsc.ijob.infra.mapper.event.EventBindingMapper
                                                    .class
                                        },
                                        (proxy, method, args) -> {
                                            assertEquals(false, args[2]);
                                            return 0;
                                        }));
    }

    private static ScheduleMapper scheduleMapper(List<String> order) {
        return (ScheduleMapper)
                Proxy.newProxyInstance(
                        ScheduleMapper.class.getClassLoader(),
                        new Class<?>[] {ScheduleMapper.class},
                        (proxy, method, args) -> {
                            if ("disableByTargetId".equals(method.getName())) {
                                order.add("schedule-disable");
                                return 0;
                            }
                            throw unsupported();
                        });
    }

    private static final class RecordingPublicationMapper implements TaskPublicationMapper {
        private final List<String> order;
        private TaskPublicationIdentityRow identity;
        private TaskIdentityRetirement retirement;
        private int retireRows = 1;
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
        public int retireIdentity(TaskIdentityRetirement retirement) {
            order.add("identity-retire");
            this.retirement = retirement;
            return retireRows;
        }

        @Override
        public int insertIdentity(TaskPublicationIdentityInsert identity) {
            throw unsupported();
        }

        @Override
        public int initializeCurrentDefinition(TaskCurrentDefinitionInitialization initialization) {
            throw unsupported();
        }

        @Override
        public int advanceCurrentDefinition(TaskCurrentDefinitionAdvance advance) {
            throw unsupported();
        }
    }

    private static final class RecordingDefinitionMapper implements TaskDefinitionMapper {
        private final List<String> order;
        private TaskDefinition selected;

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
            throw unsupported();
        }
    }

    private static UnsupportedOperationException unsupported() {
        return new UnsupportedOperationException("not used by retirement tests");
    }
}
