// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.infra.mapper.workbench;

import com.ifielsc.ijob.contract.workbench.WorkbenchFailureDTO;
import com.ifielsc.ijob.contract.workbench.WorkbenchQuery;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface WorkbenchMapper {
    List<RuntimeNodeRow> selectRuntimeNodes(@Param("history") boolean history);

    long countQueuedWork(@Param("now") java.time.LocalDateTime now);

    long countClaimedWork(@Param("now") java.time.LocalDateTime now);

    long countExpiredWork(@Param("now") java.time.LocalDateTime now);

    java.time.LocalDateTime selectOldestQueuedAt(@Param("now") java.time.LocalDateTime now);

    List<WorkbenchStatusCount> selectWindowStatuses(WorkbenchQuery query);

    List<WorkbenchStatusCount> selectCurrentActiveRoots();

    long countPendingApprovals(@Param("operatorId") String operatorId);

    List<WorkbenchFailureDTO> selectRecentFailures(WorkbenchQuery query);
}
