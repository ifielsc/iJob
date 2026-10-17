package com.ifelio.ijob.infra.entity.job;

import com.ifelio.ijob.contract.enums.ExecuteCondition;
import com.ifelio.ijob.contract.enums.FailStrategy;
import com.ifelio.ijob.infra.coordination.id.IdNamespace;
import com.ifelio.ijob.infra.mybatis.AutoId;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class JobDefinitionNodePolicy {
    @AutoId(IdNamespace.JOB_DEFINITION_NODE_POLICY)
    private Long id;

    private Long definitionId;
    private Long nodeId;
    private FailStrategy failStrategy;
    private ExecuteCondition executeCondition;
    private LocalDateTime createTime;
}
