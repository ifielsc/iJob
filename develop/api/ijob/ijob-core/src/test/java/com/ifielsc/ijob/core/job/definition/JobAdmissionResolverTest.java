// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.core.job.definition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

class JobAdmissionResolverTest {
    @Test
    void disabledConfigurationCanBeLockedWithoutAdmittingExecution() {
        JobDefinition disabled = definition();
        disabled.setEnabled(false);
        var resolver = resolver(identity(), disabled);
        resolver.lockPublishedConfiguration(8L);
        assertCode(ResultCode.CONFLICT, resolver, 8L);
        JobPublicationIdentityRow retired = identity();
        retired.setRetired(true);
        retired.setRetiredTime(LocalDateTime.now());
        assertThrows(
                BusinessException.class,
                () -> resolver(retired, disabled).lockPublishedConfiguration(8L));
    }

    @Test
    void shouldLockThenReturnCurrentDefinitionSnapshot() throws Exception {
        List<String> calls = new ArrayList<>();
        JobAdmissionResolver resolver =
                new JobAdmissionResolver(
                        new FakePublicationMapper(identity(), calls),
                        new FakeDefinitionMapper(definition(), calls));

        JobAdmissionSnapshot snapshot = resolver.resolve(8L);

        assertEquals(List.of("identity:8", "definition:31"), calls);
        assertEquals(8L, snapshot.jobId());
        assertEquals(31L, snapshot.jobDefinitionId());
        assertEquals(2L, snapshot.revision());
        assertEquals("job", snapshot.name());
        assertEquals(Integer.valueOf(60), snapshot.timeout());

        Method method = JobAdmissionResolver.class.getMethod("resolve", Long.class);
        Transactional transactional = method.getAnnotation(Transactional.class);
        assertEquals(Propagation.MANDATORY, transactional.propagation());
        assertFalse(transactional.readOnly());
    }

    @Test
    void shouldRejectInvalidMissingLegacyAndCorruptIdentity() {
        assertCode(ResultCode.PARAM_ERROR, resolver(null, definition()), 0L);
        assertCode(ResultCode.NOT_FOUND, resolver(null, definition()), 8L);

        JobPublicationIdentityRow legacy = identity();
        legacy.setCurrentDefinitionId(null);
        legacy.setCurrentRevision(null);
        assertCode(ResultCode.CONFLICT, resolver(legacy, definition()), 8L);

        JobPublicationIdentityRow retired = identity();
        retired.setRetired(true);
        retired.setRetiredTime(LocalDateTime.now());
        assertCode(ResultCode.CONFLICT, resolver(retired, definition()), 8L);

        List<String> calls = new ArrayList<>();
        JobPublicationIdentityRow corrupt = identity();
        corrupt.setCurrentRevision(null);
        JobAdmissionResolver corruptResolver =
                new JobAdmissionResolver(
                        new FakePublicationMapper(corrupt, calls),
                        new FakeDefinitionMapper(definition(), calls));
        assertCode(ResultCode.SYSTEM_ERROR, corruptResolver, 8L);
        assertEquals(List.of("identity:8"), calls);
    }

    @Test
    void shouldRejectMissingMismatchedDisabledOrIncompleteDefinition() {
        assertCode(ResultCode.SYSTEM_ERROR, resolver(identity(), null), 8L);

        JobDefinition mismatched = definition();
        mismatched.setJobId(9L);
        assertCode(ResultCode.SYSTEM_ERROR, resolver(identity(), mismatched), 8L);

        JobDefinition disabled = definition();
        disabled.setEnabled(false);
        assertCode(ResultCode.CONFLICT, resolver(identity(), disabled), 8L);

        JobDefinition incomplete = definition();
        incomplete.setName(" ");
        assertCode(ResultCode.SYSTEM_ERROR, resolver(identity(), incomplete), 8L);
    }

    private static JobAdmissionResolver resolver(
            JobPublicationIdentityRow identity, JobDefinition definition) {
        return new JobAdmissionResolver(
                new FakePublicationMapper(identity, new ArrayList<>()),
                new FakeDefinitionMapper(definition, new ArrayList<>()));
    }

    private static void assertCode(ResultCode code, JobAdmissionResolver resolver, Long jobId) {
        BusinessException exception =
                assertThrows(BusinessException.class, () -> resolver.resolve(jobId));
        assertEquals(code, exception.getCode());
    }

    private static JobPublicationIdentityRow identity() {
        JobPublicationIdentityRow identity = new JobPublicationIdentityRow();
        identity.setId(8L);
        identity.setCurrentDefinitionId(31L);
        identity.setCurrentRevision(2L);
        identity.setRetired(false);
        return identity;
    }

    private static JobDefinition definition() {
        JobDefinition definition = new JobDefinition();
        definition.setId(31L);
        definition.setJobId(8L);
        definition.setRevision(2L);
        definition.setName("job");
        definition.setTimeout(60);
        definition.setEnabled(true);
        definition.setCreateTime(LocalDateTime.now());
        return definition;
    }

    private static final class FakePublicationMapper implements JobPublicationMapper {
        private final JobPublicationIdentityRow identity;
        private final List<String> calls;

        private FakePublicationMapper(JobPublicationIdentityRow identity, List<String> calls) {
            this.identity = identity;
            this.calls = calls;
        }

        @Override
        public List<Long> lockAllJobIdsForPublication() {
            throw new UnsupportedOperationException();
        }

        @Override
        public JobPublicationIdentityRow selectIdentityForUpdate(Long jobId) {
            calls.add("identity:" + jobId);
            return identity;
        }

        @Override
        public int insertIdentity(Job ignored) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int initializeCurrentDefinition(
                Long jobId, Long definitionId, LocalDateTime updateTime) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int advanceCurrentDefinition(JobCurrentDefinitionAdvance ignored) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int retireIdentity(JobIdentityRetirement ignored) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class FakeDefinitionMapper implements JobDefinitionMapper {
        private final JobDefinition definition;
        private final List<String> calls;

        private FakeDefinitionMapper(JobDefinition definition, List<String> calls) {
            this.definition = definition;
            this.calls = calls;
        }

        @Override
        public JobDefinition selectById(Long id) {
            calls.add("definition:" + id);
            return definition;
        }

        @Override
        public List<JobDefinition> selectListByJobId(Long jobId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int insert(JobDefinition ignored) {
            throw new UnsupportedOperationException();
        }
    }
}
