package com.ifielsc.ijob.contract.job;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class JobEdgeRequest {
    private String fromNodeId;
    private String toNodeId;
    private String routeKey;
    private Integer priority;
    private Boolean defaultRoute;
    private DagConditionExpression condition;
}
