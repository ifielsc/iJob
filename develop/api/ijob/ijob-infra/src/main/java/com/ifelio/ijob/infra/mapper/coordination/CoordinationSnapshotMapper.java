package com.ifelio.ijob.infra.mapper.coordination;

import com.ifelio.ijob.infra.coordination.maintenance.CoordinationSnapshot;

import org.apache.ibatis.annotations.Param;

public interface CoordinationSnapshotMapper {
    CoordinationSnapshot selectSnapshot(
            @Param("maintenanceLeaseName") String maintenanceLeaseName,
            @Param("staleRunMicros") long staleRunMicros);
}
