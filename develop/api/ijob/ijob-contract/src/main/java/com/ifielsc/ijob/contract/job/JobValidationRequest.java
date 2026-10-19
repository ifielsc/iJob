// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.contract.job;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class JobValidationRequest {
    private Long jobId;
    private Long expectedRevision;
    private JobUpsertRequest definition;
}
