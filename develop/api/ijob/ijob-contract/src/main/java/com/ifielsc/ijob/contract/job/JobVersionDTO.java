// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.contract.job;

import java.time.LocalDateTime;

public record JobVersionDTO(
        String jobId,
        String definitionId,
        String revision,
        String name,
        Boolean enabled,
        LocalDateTime publishedAt,
        boolean current) {}
