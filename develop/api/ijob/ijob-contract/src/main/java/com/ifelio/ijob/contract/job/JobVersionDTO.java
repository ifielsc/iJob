package com.ifelio.ijob.contract.job;

import java.time.LocalDateTime;

public record JobVersionDTO(
        String jobId,
        String definitionId,
        String revision,
        String name,
        Boolean enabled,
        LocalDateTime publishedAt,
        boolean current) {}
