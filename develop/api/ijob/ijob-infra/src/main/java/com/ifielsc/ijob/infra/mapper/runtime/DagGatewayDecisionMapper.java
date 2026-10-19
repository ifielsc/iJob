package com.ifielsc.ijob.infra.mapper.runtime;

import com.ifielsc.ijob.infra.entity.runtime.DagGatewayDecision;

import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface DagGatewayDecisionMapper {
    List<DagGatewayDecision> selectByJobInstanceId(Long jobInstanceId);

    List<DagGatewayDecision> selectByJobInstanceIds(
            @Param("jobInstanceIds") List<Long> jobInstanceIds);

    List<DagGatewayDecision> selectByJobInstanceAndNode(
            @Param("jobInstanceId") Long jobInstanceId,
            @Param("definitionNodeId") Long definitionNodeId);

    int insertIfOwned(
            @Param("decision") DagGatewayDecision decision,
            @Param("rootInstanceId") Long rootInstanceId,
            @Param("triggerId") Long triggerId);
}
