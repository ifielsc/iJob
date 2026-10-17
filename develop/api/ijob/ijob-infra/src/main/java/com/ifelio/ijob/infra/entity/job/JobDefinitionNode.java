package com.ifelio.ijob.infra.entity.job;

import com.ifelio.ijob.contract.enums.GatewayMode;
import com.ifelio.ijob.contract.enums.JobNodeKind;
import com.ifelio.ijob.contract.enums.TargetType;
import com.ifelio.ijob.infra.coordination.id.IdNamespace;
import com.ifelio.ijob.infra.mybatis.AutoId;

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
