package com.ifelio.ijob.contract.job;

public record JobExecutionContextDTO(
        String id, String name, String definitionId, String revision, Boolean enabled) {}
