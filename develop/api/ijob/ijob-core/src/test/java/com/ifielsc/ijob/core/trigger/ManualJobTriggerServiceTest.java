// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.core.trigger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ifielsc.ijob.contract.runtime.ManualTriggerRequest;
import com.ifielsc.ijob.core.execute.variable.VariableSnapshotService;
import com.ifielsc.ijob.core.job.definition.JobAdmissionResolver;
import com.ifielsc.ijob.core.job.definition.JobAdmissionSnapshot;
import com.ifielsc.ijob.infra.entity.runtime.Trigger;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;

class ManualJobTriggerServiceTest {
    @Test
    void shouldResolveSnapshotInsertTriggerAndDispatchAfterCommit() {
        JobAdmissionSnapshot snapshot = new JobAdmissionSnapshot(9L, 41L, 3L, "job", 60);
        RecordingResolver resolver = new RecordingResolver(snapshot);
        RecordingRuntime runtime = new RecordingRuntime();
        RecordingDispatcher dispatcher = new RecordingDispatcher();
        ManualJobTriggerService service =
                new ManualJobTriggerService(
                        resolver,
                        runtime,
                        dispatcher,
                        new VariableSnapshotService(new ObjectMapper()));

        ManualTriggerRequest request = new ManualTriggerRequest();
        request.setRequestId("job-request-9");
        Long triggerId = service.trigger(9L, request);

        assertEquals(71L, triggerId);
        assertEquals(9L, resolver.jobId);
        assertEquals(snapshot, runtime.snapshot);
        assertEquals("job-request-9", runtime.requestId);
        assertEquals(71L, dispatcher.triggerId);
    }

    @Test
    void triggerShouldUseRequiredWriteTransaction() throws Exception {
        Method method = ManualJobTriggerService.class.getMethod("trigger", Long.class);
        Transactional annotation = method.getAnnotation(Transactional.class);

        assertNotNull(annotation);
        assertFalse(annotation.readOnly());
    }

    private static final class RecordingResolver extends JobAdmissionResolver {
        private final JobAdmissionSnapshot snapshot;
        private Long jobId;

        private RecordingResolver(JobAdmissionSnapshot snapshot) {
            super(null, null);
            this.snapshot = snapshot;
        }

        @Override
        public JobAdmissionSnapshot resolve(Long id) {
            jobId = id;
            return snapshot;
        }
    }

    private static final class RecordingRuntime extends TriggerRuntimeService {
        private JobAdmissionSnapshot snapshot;
        private String requestId;

        private RecordingRuntime() {
            super(null);
        }

        @Override
        public Trigger createManualJobTrigger(
                JobAdmissionSnapshot resolvedSnapshot, String variables, String resolvedRequestId) {
            snapshot = resolvedSnapshot;
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
