// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.infra.mapper.coordination;

import com.ifielsc.ijob.infra.coordination.maintenance.CoordinationSnapshot;

import org.apache.ibatis.annotations.Param;

public interface CoordinationSnapshotMapper {
    CoordinationSnapshot selectSnapshot(
            @Param("maintenanceLeaseName") String maintenanceLeaseName,
            @Param("staleRunMicros") long staleRunMicros);
}
