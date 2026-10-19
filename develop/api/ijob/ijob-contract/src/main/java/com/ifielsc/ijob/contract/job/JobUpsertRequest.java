// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.contract.job;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class JobUpsertRequest {
    private JobLayout layout;
    private String name;
    private Integer timeout;
    private Boolean enabled;
    private String description;
    private List<String> groups;
    private List<JobNodeRequest> nodes;
    private List<JobEdgeRequest> edges;
}
