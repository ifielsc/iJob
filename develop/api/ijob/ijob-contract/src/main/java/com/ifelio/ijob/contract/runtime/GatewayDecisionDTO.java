package com.ifelio.ijob.contract.runtime;

import com.ifelio.ijob.contract.enums.GatewayDecisionStatus;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class GatewayDecisionDTO {
    private Long id;
    private Long jobInstanceId;
    private Long definitionNodeId;
    private Integer evaluationNo;
    private String selectedEdgeIds;
    private String contextDigest;
    private Integer evaluatorVersion;
    private GatewayDecisionStatus status;
    private String errorCode;
    private LocalDateTime createTime;
}
