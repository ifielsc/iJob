package com.ifelio.ijob.core.task.definition;

public record TaskAdmissionSnapshot(
        Long taskId, Long taskDefinitionId, Long revision, TaskExecutionSpec executionSpec) {}
