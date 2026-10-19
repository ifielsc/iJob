package com.ifielsc.ijob.contract.job;

import com.ifielsc.ijob.contract.enums.JobNodeKind;
import com.ifielsc.ijob.contract.enums.TargetType;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class JobNodeRequest {
    private String nodeId;
    private String name;
    private JobNodeKind kind;
    private TargetType nodeType;
    private Long refId;
    private JobGatewayRequest gateway;
    private JobNodePolicyRequest policy;
}
