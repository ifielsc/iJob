package com.ifielsc.ijob.infra.entity.job;

import com.ifielsc.ijob.infra.coordination.id.IdNamespace;
import com.ifielsc.ijob.infra.mybatis.AutoId;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class JobDefinitionEdge {
    @AutoId(IdNamespace.JOB_DEFINITION_EDGE)
    private Long id;

    private Long definitionId;
    private Long fromNodeId;
    private Long toNodeId;
    private String routeKey;
    private Integer routePriority;
    private Boolean defaultRoute;
    private Integer conditionVersion;
    private String conditionPayload;
    private LocalDateTime createTime;
}
