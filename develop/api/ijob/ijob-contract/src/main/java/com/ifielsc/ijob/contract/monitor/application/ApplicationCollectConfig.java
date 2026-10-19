// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.contract.monitor.application;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApplicationCollectConfig {
    private String name;

    private ProcessMatchType matchType;

    private String matchValue;

    @Builder.Default private Boolean required = Boolean.TRUE;

    @Builder.Default private Boolean allowMultiple = Boolean.FALSE;
}
