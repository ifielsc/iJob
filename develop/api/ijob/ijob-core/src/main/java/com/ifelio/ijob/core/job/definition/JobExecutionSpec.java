package com.ifelio.ijob.core.job.definition;

public record JobExecutionSpec(
        Long jobId, Long jobDefinitionId, Long revision, String name, Integer timeout) {}
