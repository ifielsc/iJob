package com.ifielsc.ijob.infra.mapper.job;

import java.util.List;

public interface JobDefinitionReferenceSnapshotMapper {
    List<Long> selectAllCurrentJobIds();

    List<JobReferenceRow> selectCurrentJobReferences();

    Long selectCurrentJobDefinitionId(Long jobId);
}
