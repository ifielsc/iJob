package com.ifelio.ijob.core.job.definition;

import com.ifelio.ijob.infra.mapper.job.JobReferenceRow;
import com.ifelio.ijob.infra.mapper.job.JobReferenceSnapshotMapper;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class JobReferenceSnapshotProvider {
    private final JobReferenceSnapshotMapper mapper;

    @Transactional(readOnly = true)
    public Map<Long, Set<Long>> loadSnapshot() {
        List<Long> jobIds = mapper.selectAllJobIds();
        List<JobReferenceRow> references = mapper.selectJobReferences();

        Map<Long, Set<Long>> mutableSnapshot = new LinkedHashMap<>();
        for (Long jobId : jobIds) {
            mutableSnapshot.put(jobId, new LinkedHashSet<>());
        }
        for (JobReferenceRow reference : references) {
            Set<Long> children = mutableSnapshot.get(reference.jobId());
            if (children != null) {
                children.add(reference.referencedJobId());
            }
        }

        Map<Long, Set<Long>> immutableSnapshot = new LinkedHashMap<>();
        mutableSnapshot.forEach(
                (jobId, children) ->
                        immutableSnapshot.put(
                                jobId, Collections.unmodifiableSet(new LinkedHashSet<>(children))));
        return Collections.unmodifiableMap(immutableSnapshot);
    }
}
