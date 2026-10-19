package com.ifielsc.ijob.contract.job;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class JobEdgeResponse {
    private Long id;
    private Long fromNodeId;
    private Long toNodeId;
    private String routeKey;
    private Integer priority;
    private Boolean defaultRoute;
    private Integer conditionVersion;
    private DagConditionExpression condition;
}
