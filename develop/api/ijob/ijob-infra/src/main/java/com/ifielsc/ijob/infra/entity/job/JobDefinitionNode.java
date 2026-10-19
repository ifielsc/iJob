// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.infra.entity.job;

import com.ifielsc.ijob.contract.enums.GatewayMode;
import com.ifielsc.ijob.contract.enums.JobNodeKind;
import com.ifielsc.ijob.contract.enums.TargetType;
import com.ifielsc.ijob.infra.coordination.id.IdNamespace;
import com.ifielsc.ijob.infra.mybatis.AutoId;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class JobDefinitionNode {
    @AutoId(IdNamespace.JOB_DEFINITION_NODE)
    private Long id;

    private Long definitionId;
    private String nodeKey;
    private String name;
    private JobNodeKind nodeKind;
    private GatewayMode gatewayMode;
    private TargetType nodeType;
    private Long refId;
    private Long refDefinitionId;
    private Long refTaskDefinitionId;
    private LocalDateTime createTime;
}
