package com.ifelio.ijob.infra.mapper.runtime;

import com.ifelio.ijob.contract.enums.InstanceStatus;
import com.ifelio.ijob.contract.runtime.InstanceDTO;
import com.ifelio.ijob.contract.runtime.InstancePageQuery;
import com.ifelio.ijob.infra.coordination.work.ExecutionFencePermit;
import com.ifelio.ijob.infra.entity.runtime.Instance;

import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface InstanceMapper {
    List<Instance> selectByParentInstanceId(Long parentInstanceId);

    Instance selectById(Long id);

    Instance selectCurrentById(Long id);

    Instance selectSummaryById(Long id);

    List<Instance> selectSummaryTreeByRootId(Long rootInstanceId);

    Long selectRootIdByTriggerId(Long triggerId);

    Instance selectRootByTriggerId(Long triggerId);

    int countActiveByTaskId(@Param("taskId") Long taskId);

    int countActiveByPluginType(
            @Param("pluginType") com.ifelio.ijob.contract.enums.PluginType pluginType);

    int countActiveByConcurrencyResourceId(@Param("resourceId") Long resourceId);

    List<InstanceDTO> selectDtoList(InstancePageQuery query);

    List<Instance> selectTreeByRootId(Long rootInstanceId);

    List<Instance> selectBoundedVariableTreeByRootId(Long rootInstanceId);

    int insert(@Param("instance") Instance instance, @Param("permit") ExecutionFencePermit permit);

    int updateById(
            @Param("instance") Instance instance, @Param("permit") ExecutionFencePermit permit);

    int markRunningIfWaiting(
            @Param("id") Long id,
            @Param("startTime") LocalDateTime startTime,
            @Param("permit") ExecutionFencePermit permit);

    int markWaitingPermitIfWaiting(
            @Param("id") Long id,
            @Param("message") String message,
            @Param("deadline") LocalDateTime deadline,
            @Param("permit") ExecutionFencePermit permit);

    int markPausedIfRunning(@Param("id") Long id, @Param("permit") ExecutionFencePermit permit);

    int markRunningIfPaused(@Param("id") Long id, @Param("permit") ExecutionFencePermit permit);

    int markWaitingEventIfRunning(
            @Param("id") Long id, @Param("permit") ExecutionFencePermit permit);

    int resetWaitingEventForContinuation(
            @Param("id") Long id, @Param("permit") ExecutionFencePermit permit);

    int updateProgressIfRunning(
            @Param("id") Long id,
            @Param("progress") Integer progress,
            @Param("permit") ExecutionFencePermit permit);

    int updateInputVariablesIfWaiting(
            @Param("id") Long id,
            @Param("inputVariables") String inputVariables,
            @Param("permit") ExecutionFencePermit permit);

    int updateOutputVariablesIfRunning(
            @Param("id") Long id,
            @Param("outputVariables") String outputVariables,
            @Param("permit") ExecutionFencePermit permit);

    int completeIfCurrent(
            @Param("instance") Instance instance,
            @Param("expectedStatus") InstanceStatus expectedStatus,
            @Param("permit") ExecutionFencePermit permit);

    int resetForRetry(
            @Param("id") Long id,
            @Param("expectedStatus") InstanceStatus expectedStatus,
            @Param("permit") ExecutionFencePermit permit);

    int adoptActiveTree(
            @Param("rootInstanceId") Long rootInstanceId,
            @Param("fenceToken") long fenceToken,
            @Param("claimToken") String claimToken);

    int adoptTreeForRetry(
            @Param("rootInstanceId") Long rootInstanceId,
            @Param("fenceToken") long fenceToken,
            @Param("claimToken") String claimToken);
}
