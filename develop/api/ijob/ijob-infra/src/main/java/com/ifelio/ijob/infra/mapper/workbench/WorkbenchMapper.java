package com.ifelio.ijob.infra.mapper.workbench;

import com.ifelio.ijob.contract.workbench.WorkbenchFailureDTO;
import com.ifelio.ijob.contract.workbench.WorkbenchQuery;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface WorkbenchMapper {
    List<WorkbenchStatusCount> selectWindowStatuses(WorkbenchQuery query);

    List<WorkbenchStatusCount> selectCurrentActiveRoots();

    long countPendingApprovals(@Param("operatorId") String operatorId);

    List<WorkbenchFailureDTO> selectRecentFailures(WorkbenchQuery query);
}
