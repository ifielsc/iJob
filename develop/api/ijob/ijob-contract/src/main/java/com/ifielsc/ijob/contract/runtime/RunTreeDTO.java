// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.contract.runtime;

import lombok.Data;

import java.util.List;

@Data
public class RunTreeDTO {
    private Long rootInstanceId;
    private List<InstanceDTO> nodes;
    private List<GatewayDecisionDTO> gatewayDecisions;
    private boolean truncated;
    private boolean gatewayDecisionsTruncated;
    private int maxNodes;
}
