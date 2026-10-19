// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.contract.job;

import com.ifielsc.ijob.contract.enums.JobNodeKind;
import com.ifielsc.ijob.contract.enums.TargetType;

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
