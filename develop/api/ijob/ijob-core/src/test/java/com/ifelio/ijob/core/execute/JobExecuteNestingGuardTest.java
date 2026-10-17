package com.ifelio.ijob.core.execute;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.ifelio.ijob.core.execute.variable.VariableResolver;
import com.ifelio.ijob.core.instance.InstanceService;
import com.ifelio.ijob.core.job.definition.JobRuntimeNestingGuard;
import com.ifelio.ijob.infra.entity.runtime.Instance;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

class JobExecuteNestingGuardTest {
    @Test
    void initialAndRetryExecutionShouldGuardPersistedAncestryFirst() {
        RecordingGuard guard = new RecordingGuard();
        RecordingInstances instances = new RecordingInstances(instance(9L));
        JobExecuteService service =
                new JobExecuteService(
                        new VariableResolver(),
                        guard,
                        null,
                        null,
                        null,
                        null,
                        null,
                        instances,
                        null,
                        null,
                        null,
                        null,
                        null);

        assertThrows(GuardReachedException.class, () -> service.execute(9L));
        assertThrows(GuardReachedException.class, () -> service.executeRetry(9L, null, null, null));

        assertEquals(List.of(9L, 9L), guard.instanceIds);
    }

    private static Instance instance(Long id) {
        Instance instance = new Instance();
        instance.setId(id);
        return instance;
    }

    private static final class RecordingInstances extends InstanceService {
        private final Instance instance;

        private RecordingInstances(Instance instance) {
            super(null, null, null, null);
            this.instance = instance;
        }

        @Override
        public Instance getInstance(Long id) {
            return instance;
        }
    }

    private static final class RecordingGuard extends JobRuntimeNestingGuard {
        private final List<Long> instanceIds = new ArrayList<>();

        private RecordingGuard() {
            super(null);
        }

        @Override
        public void validateInstance(Instance instance) {
            instanceIds.add(instance.getId());
            throw new GuardReachedException();
        }
    }

    private static final class GuardReachedException extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }
}
