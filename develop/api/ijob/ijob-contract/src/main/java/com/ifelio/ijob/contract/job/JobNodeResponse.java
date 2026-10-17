package com.ifelio.ijob.contract.job;

import com.ifelio.ijob.contract.enums.JobNodeKind;
import com.ifelio.ijob.contract.enums.TargetType;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class JobNodeResponse {
    private Long id;
    private String nodeKey;
    private String name;
    private JobNodeKind kind;
    private TargetType nodeType;
    private Long refId;
    private String refName;
    private Long refDefinitionId;
    private Long refRevision;
    private String currentRefName;
    private Long currentRefDefinitionId;
    private Long currentRefRevision;
    private Boolean referenceAvailable;
    private JobGatewayResponse gateway;
    private JobNodePolicyResponse policy;
}
