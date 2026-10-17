package com.ifelio.ijob.infra.mapper.job;

import java.util.List;

public interface JobReferenceSnapshotMapper {
    List<Long> selectAllJobIds();

    List<JobReferenceRow> selectJobReferences();
}
