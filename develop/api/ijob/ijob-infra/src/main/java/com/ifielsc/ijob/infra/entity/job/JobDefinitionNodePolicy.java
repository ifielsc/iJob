// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.infra.entity.job;

import com.ifielsc.ijob.contract.enums.ExecuteCondition;
import com.ifielsc.ijob.contract.enums.FailStrategy;
import com.ifielsc.ijob.infra.coordination.id.IdNamespace;
import com.ifielsc.ijob.infra.mybatis.AutoId;

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
