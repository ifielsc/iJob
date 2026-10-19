package com.ifielsc.ijob.core.task.definition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.ifielsc.ijob.common.exception.BusinessException;
import com.ifielsc.ijob.common.response.ResultCode;
import com.ifielsc.ijob.contract.enums.PluginType;
import com.ifielsc.ijob.infra.entity.task.TaskDefinition;
import com.ifielsc.ijob.infra.mapper.task.TaskCurrentDefinitionAdvance;
import com.ifielsc.ijob.infra.mapper.task.TaskCurrentDefinitionInitialization;
import com.ifielsc.ijob.infra.mapper.task.TaskDefinitionMapper;
import com.ifielsc.ijob.infra.mapper.task.TaskIdentityRetirement;
import com.ifielsc.ijob.infra.mapper.task.TaskPublicationIdentityInsert;
import com.ifielsc.ijob.infra.mapper.task.TaskPublicationIdentityRow;
import com.ifielsc.ijob.infra.mapper.task.TaskPublicationMapper;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

class TaskAdmissionResolverTest {
    @Test
    void shouldLockThenReturnImmutableCurrentDefinitionSnapshot() throws Exception {
        List<String> calls = new ArrayList<>();
        TaskAdmissionResolver resolver =
                new TaskAdmissionResolver(
                        new FakePublicationMapper(identity(), calls),
                        new FakeDefinitionMapper(definition(), calls));

        TaskAdmissionSnapshot snapshot = resolver.resolve(9L);

        assertEquals(List.of("identity:9", "definition:41"), calls);
        assertEquals(9L, snapshot.taskId());
        assertEquals(41L, snapshot.taskDefinitionId());
        assertEquals(3L, snapshot.revision());
        assertEquals(PluginType.SCRIPT, snapshot.executionSpec().getPluginType());
        assertEquals("secret-config", snapshot.executionSpec().getPluginConfig());
        assertFalse(snapshot.executionSpec().toString().contains("secret-config"));

        Method method = TaskAdmissionResolver.class.getMethod("resolve", Long.class);
        Transactional transactional = method.getAnnotation(Transactional.class);
        assertEquals(Propagation.MANDATORY, transactional.propagation());
        assertFalse(transactional.readOnly());
    }

    @Test
    void shouldRejectInvalidMissingRetiredAndLegacyIdentity() {
        assertCode(ResultCode.PARAM_ERROR, resolver(null, definition()), 0L);
        assertCode(ResultCode.NOT_FOUND, resolver(null, definition()), 9L);

        TaskPublicationIdentityRow retired = identity();
        retired.setRetired(true);
        retired.setRetiredTime(LocalDateTime.now());
        assertCode(ResultCode.CONFLICT, resolver(retired, definition()), 9L);

        TaskPublicationIdentityRow legacy = identity();
        legacy.setCurrentTaskDefinitionId(null);
        legacy.setCurrentRevision(null);
        assertCode(ResultCode.CONFLICT, resolver(legacy, definition()), 9L);
    }

    @Test
    void shouldRejectCorruptIdentityBeforeDefinitionRead() {
        List<String> calls = new ArrayList<>();
        TaskPublicationIdentityRow identity = identity();
        identity.setCurrentRevision(null);
        TaskAdmissionResolver resolver =
                new TaskAdmissionResolver(
                        new FakePublicationMapper(identity, calls),
                        new FakeDefinitionMapper(definition(), calls));

        assertCode(ResultCode.SYSTEM_ERROR, resolver, 9L);
        assertEquals(List.of("identity:9"), calls);
    }

    @Test
    void shouldRejectMissingOrMismatchedDefinition() {
        assertCode(ResultCode.SYSTEM_ERROR, resolver(identity(), null), 9L);

        TaskDefinition mismatched = definition();
        mismatched.setTaskId(10L);
        assertCode(ResultCode.SYSTEM_ERROR, resolver(identity(), mismatched), 9L);
    }

    @Test
    void shouldRejectDisabledOrIncompleteDefinition() {
        TaskDefinition disabled = definition();
        disabled.setEnabled(false);
        assertCode(ResultCode.CONFLICT, resolver(identity(), disabled), 9L);

        TaskDefinition incomplete = definition();
        incomplete.setPluginConfig(" ");
        assertCode(ResultCode.SYSTEM_ERROR, resolver(identity(), incomplete), 9L);
    }

    private static TaskAdmissionResolver resolver(
            TaskPublicationIdentityRow identity, TaskDefinition definition) {
        return new TaskAdmissionResolver(
                new FakePublicationMapper(identity, new ArrayList<>()),
                new FakeDefinitionMapper(definition, new ArrayList<>()));
    }

    private static void assertCode(ResultCode code, TaskAdmissionResolver resolver, Long taskId) {
        BusinessException exception =
                assertThrows(BusinessException.class, () -> resolver.resolve(taskId));
        assertEquals(code, exception.getCode());
    }

    private static TaskPublicationIdentityRow identity() {
        TaskPublicationIdentityRow identity = new TaskPublicationIdentityRow();
        identity.setId(9L);
        identity.setCurrentTaskDefinitionId(41L);
        identity.setCurrentRevision(3L);
        identity.setRetired(false);
        return identity;
    }

    private static TaskDefinition definition() {
        TaskDefinition definition = new TaskDefinition();
        definition.setId(41L);
        definition.setTaskId(9L);
        definition.setRevision(3L);
        definition.setName("task");
        definition.setPluginType(PluginType.SCRIPT);
        definition.setPluginConfig("secret-config");
        definition.setRetryTimes(1);
        definition.setRetryInterval(2);
        definition.setTimeout(60);
        definition.setEnabled(true);
        definition.setCreateTime(LocalDateTime.now());
        return definition;
    }

    private static final class FakePublicationMapper implements TaskPublicationMapper {
        private final TaskPublicationIdentityRow identity;
        private final List<String> calls;

        private FakePublicationMapper(TaskPublicationIdentityRow identity, List<String> calls) {
            this.identity = identity;
            this.calls = calls;
        }

        @Override
        public TaskPublicationIdentityRow selectIdentityForUpdate(Long taskId) {
            calls.add("identity:" + taskId);
            return identity;
        }

        @Override
        public int insertIdentity(TaskPublicationIdentityInsert ignored) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int initializeCurrentDefinition(TaskCurrentDefinitionInitialization ignored) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int advanceCurrentDefinition(TaskCurrentDefinitionAdvance ignored) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int retireIdentity(TaskIdentityRetirement ignored) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class FakeDefinitionMapper implements TaskDefinitionMapper {
        private final TaskDefinition definition;
        private final List<String> calls;

        private FakeDefinitionMapper(TaskDefinition definition, List<String> calls) {
            this.definition = definition;
            this.calls = calls;
        }

        @Override
        public TaskDefinition selectById(Long id) {
            calls.add("definition:" + id);
            return definition;
        }

        @Override
        public List<TaskDefinition> selectListByTaskId(Long taskId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int insert(TaskDefinition ignored) {
            throw new UnsupportedOperationException();
        }
    }
}
