package com.ifelio.ijob.infra.entity.runtime;

import com.ifelio.ijob.contract.enums.GatewayDecisionStatus;
import com.ifelio.ijob.infra.coordination.id.IdNamespace;
import com.ifelio.ijob.infra.mybatis.AutoId;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class DagGatewayDecision {
    @AutoId(IdNamespace.DAG_GATEWAY_DECISION)
    private Long id;

    private Long jobInstanceId;
    private Long definitionNodeId;
    private Integer evaluationNo;
    private String selectedEdgeIds;
    private String contextDigest;
    private Integer evaluatorVersion;
    private GatewayDecisionStatus status;
    private String errorCode;
    private Long executionFence;
    private LocalDateTime createTime;
}
