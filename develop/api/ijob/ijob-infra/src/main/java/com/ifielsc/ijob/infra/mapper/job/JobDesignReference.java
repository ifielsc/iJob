// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.infra.mapper.job;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class JobDesignReference {
    private Long id;
    private String name;
    private Long definitionId;
    private Long revision;
    private Boolean enabled;
}
