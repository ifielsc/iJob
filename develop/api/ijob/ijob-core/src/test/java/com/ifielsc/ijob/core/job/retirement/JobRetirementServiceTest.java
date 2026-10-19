// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.core.job.retirement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.ifielsc.ijob.common.exception.BusinessException;
import com.ifielsc.ijob.common.response.ResultCode;
import com.ifielsc.ijob.infra.entity.job.Job;
import com.ifielsc.ijob.infra.entity.job.JobDefinition;
import com.ifielsc.ijob.infra.mapper.job.JobCurrentDefinitionAdvance;
import com.ifielsc.ijob.infra.mapper.job.JobDefinitionMapper;
import com.ifielsc.ijob.infra.mapper.job.JobIdentityRetirement;
import com.ifielsc.ijob.infra.mapper.job.JobPublicationIdentityRow;
import com.ifielsc.ijob.infra.mapper.job.JobPublicationMapper;
import com.ifielsc.ijob.infra.mapper.schedule.ScheduleMapper;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

class JobRetirementServiceTest {
    @Test
    void versionedJobShouldLockBarrierValidateAndRetireWithCas() {
        Harness harness = readyHarness();

        JobRetirementResult result = harness.service.retire(10L);

        assertFalse(result.alreadyRetired());
        assertNotNull(result.retiredTime());
        assertEquals(
                List.of(
                        "barrier",
                        "identity-lock",
                        "definition-select",
                        "identity-retire",
                        "schedule-disable"),
                harness.order);
        assertEquals(1000L, harness.publication.retirement.getExpectedDefinitionId());
        assertEquals(4L, harness.publication.retirement.getExpectedRevision());
    }

    @Test
    void legacyDoubleNullJobShouldRetireWithoutDefinitionRead() {
        Harness harness = new Harness();
        harness.publication.identity = identity(10L, null, null);

        harness.service.retire(10L);

        assertEquals(
                List.of("barrier", "identity-lock", "identity-retire", "schedule-disable"),
                harness.order);
        assertNull(harness.publication.retirement.getExpectedDefinitionId());
    }

    @Test
    void retiredJobShouldBeIdempotent() {
        Harness harness = new Harness();
        LocalDateTime retiredTime = LocalDateTime.of(2026, 8, 22, 1, 0);
        JobPublicationIdentityRow identity = identity(10L, 1000L, 4L);
        identity.setRetired(true);
        identity.setRetiredTime(retiredTime);
        harness.publication.identity = identity;

        JobRetirementResult result = harness.service.retire(10L);

        assertEquals(new JobRetirementResult(10L, retiredTime, true), result);
        assertEquals(List.of("barrier", "identity-lock", "schedule-disable"), harness.order);
        assertNull(harness.publication.retirement);
    }

    @Test
    void missingBrokenAndCasMismatchShouldBeClassified() {
        Harness missing = new Harness();
        assertCode(ResultCode.NOT_FOUND, () -> missing.service.retire(10L));

        Harness broken = new Harness();
        broken.publication.identity = identity(10L, 1000L, null);
        assertCode(ResultCode.SYSTEM_ERROR, () -> broken.service.retire(10L));

        Harness mismatch = readyHarness();
        mismatch.publication.retireRows = 0;
        assertCode(ResultCode.SYSTEM_ERROR, () -> mismatch.service.retire(10L));
    }

    @Test
    void malformedBarrierShouldFailBeforeIdentityLock() {
        for (List<Long> ids : List.of(List.of(2L, 1L), List.of(1L, 1L), List.of(0L))) {
            Harness harness = new Harness();
            harness.publication.lockedIds = ids;

            assertCode(ResultCode.SYSTEM_ERROR, () -> harness.service.retire(10L));
            assertEquals(List.of("barrier"), harness.order);
        }
    }

    @Test
    void batchShouldLockBarrierOnceAndPropagateLateFailure() {
        Harness harness = readyHarness();

        assertCode(ResultCode.SYSTEM_ERROR, () -> harness.service.retireBatch(List.of(10L, 11L)));

        assertEquals(
                List.of(
                        "barrier",
                        "identity-lock",
                        "definition-select",
                        "identity-retire",
                        "schedule-disable",
                        "identity-lock"),
                harness.order);
    }

    @Test
    void publicEntrypointShouldUseRequiredWriteTransaction() throws Exception {
        Method method = JobRetirementService.class.getMethod("retire", Long.class);
        Transactional annotation = method.getAnnotation(Transactional.class);

        assertNotNull(annotation);
        assertFalse(annotation.readOnly());
        assertEquals(
                org.springframework.transaction.annotation.Propagation.REQUIRED,
                annotation.propagation());
    }

    private static Harness readyHarness() {
        Harness harness = new Harness();
        harness.publication.identity = identity(10L, 1000L, 4L);
        harness.definitions.selected = definition(1000L, 10L, 4L);
        return harness;
    }

    private static JobPublicationIdentityRow identity(Long id, Long definitionId, Long revision) {
        JobPublicationIdentityRow identity = new JobPublicationIdentityRow();
        identity.setId(id);
        identity.setCurrentDefinitionId(definitionId);
        identity.setCurrentRevision(revision);
        identity.setRetired(false);
        identity.setRetiredTime(null);
        return identity;
    }

    private static JobDefinition definition(Long id, Long jobId, Long revision) {
        JobDefinition definition = new JobDefinition();
        definition.setId(id);
        definition.setJobId(jobId);
        definition.setRevision(revision);
        return definition;
    }

    private static void assertCode(ResultCode code, Runnable action) {
        BusinessException exception = assertThrows(BusinessException.class, action::run);
        assertEquals(code, exception.getCode());
    }

    private static final class Harness {
        private final List<String> order = new ArrayList<>();
        private final RecordingPublicationMapper publication =
                new RecordingPublicationMapper(order);
        private final RecordingDefinitionMapper definitions = new RecordingDefinitionMapper(order);
        private final ScheduleMapper schedules = scheduleMapper(order);
        private final JobRetirementService service =
                new JobRetirementService(
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

    private static final class RecordingPublicationMapper implements JobPublicationMapper {
        private final List<String> order;
        private List<Long> lockedIds = List.of();
        private JobPublicationIdentityRow identity;
        private JobIdentityRetirement retirement;
        private int retireRows = 1;

        private RecordingPublicationMapper(List<String> order) {
            this.order = order;
        }

        @Override
        public List<Long> lockAllJobIdsForPublication() {
            order.add("barrier");
            return lockedIds;
        }

        @Override
        public JobPublicationIdentityRow selectIdentityForUpdate(Long id) {
            order.add("identity-lock");
            return identity;
        }

        @Override
        public int retireIdentity(JobIdentityRetirement selected) {
            order.add("identity-retire");
            retirement = selected;
            return retireRows;
        }

        @Override
        public int insertIdentity(Job ignored) {
            throw unsupported();
        }

        @Override
        public int initializeCurrentDefinition(
                Long jobId, Long definitionId, LocalDateTime updateTime) {
            throw unsupported();
        }

        @Override
        public int advanceCurrentDefinition(JobCurrentDefinitionAdvance ignored) {
            throw unsupported();
        }
    }

    private static final class RecordingDefinitionMapper implements JobDefinitionMapper {
        private final List<String> order;
        private JobDefinition selected;

        private RecordingDefinitionMapper(List<String> order) {
            this.order = order;
        }

        @Override
        public JobDefinition selectById(Long id) {
            order.add("definition-select");
            return selected;
        }

        @Override
        public List<JobDefinition> selectListByJobId(Long id) {
            throw unsupported();
        }

        @Override
        public int insert(JobDefinition ignored) {
            throw unsupported();
        }
    }

    private static UnsupportedOperationException unsupported() {
        return new UnsupportedOperationException("not used");
    }
}
