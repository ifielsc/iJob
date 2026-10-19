// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.core.job.definition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ifielsc.ijob.infra.mapper.job.JobReferenceRow;
import com.ifielsc.ijob.infra.mapper.job.JobReferenceSnapshotMapper;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Set;

class JobReferenceSnapshotProviderTest {
    @Test
    void snapshotShouldIncludeLeafJobsAndDeduplicateReferences() {
        JobReferenceSnapshotProvider provider =
                provider(
                        List.of(1L, 2L, 3L),
                        List.of(
                                new JobReferenceRow(1L, 2L),
                                new JobReferenceRow(1L, 2L),
                                new JobReferenceRow(1L, 3L)));

        Map<Long, Set<Long>> snapshot = provider.loadSnapshot();

        assertEquals(Set.of(1L, 2L, 3L), snapshot.keySet());
        assertEquals(Set.of(2L, 3L), snapshot.get(1L));
        assertEquals(Set.of(), snapshot.get(2L));
        assertEquals(Set.of(), snapshot.get(3L));
    }

    @Test
    void snapshotShouldPreserveDanglingChildWithoutInventingJobKey() {
        JobReferenceSnapshotProvider provider =
                provider(List.of(1L), List.of(new JobReferenceRow(1L, 99L)));

        Map<Long, Set<Long>> snapshot = provider.loadSnapshot();

        assertEquals(Set.of(99L), snapshot.get(1L));
        assertFalse(snapshot.containsKey(99L));
    }

    @Test
    void snapshotShouldIgnoreReferencesWhoseParentIsNotAJob() {
        JobReferenceSnapshotProvider provider =
                provider(List.of(1L), List.of(new JobReferenceRow(9L, 1L)));

        assertEquals(Map.of(1L, Set.of()), provider.loadSnapshot());
    }

    @Test
    void snapshotAndNestedSetsShouldBeImmutable() {
        JobReferenceSnapshotProvider provider =
                provider(List.of(1L, 2L), List.of(new JobReferenceRow(1L, 2L)));
        Map<Long, Set<Long>> snapshot = provider.loadSnapshot();

        assertThrows(UnsupportedOperationException.class, () -> snapshot.put(3L, Set.of()));
        assertThrows(UnsupportedOperationException.class, () -> snapshot.get(1L).add(3L));
    }

    @Test
    void providerShouldDeclareReadOnlyTransaction() throws Exception {
        Method method = JobReferenceSnapshotProvider.class.getMethod("loadSnapshot");
        org.springframework.transaction.annotation.Transactional annotation =
                method.getAnnotation(
                        org.springframework.transaction.annotation.Transactional.class);

        assertTrue(annotation.readOnly());
    }

    private JobReferenceSnapshotProvider provider(
            List<Long> jobIds, List<JobReferenceRow> references) {
        return new JobReferenceSnapshotProvider(new StubMapper(jobIds, references));
    }

    private record StubMapper(List<Long> jobIds, List<JobReferenceRow> references)
            implements JobReferenceSnapshotMapper {
        @Override
        public List<Long> selectAllJobIds() {
            return jobIds;
        }

        @Override
        public List<JobReferenceRow> selectJobReferences() {
            return references;
        }
    }
}
