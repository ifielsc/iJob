package com.ifelio.ijob.contract.job;

import java.util.List;

public record JobValidationDTO(boolean valid, List<Issue> issues, List<Reference> references) {
    public record Issue(
            String path,
            String errorCode,
            String messageKey,
            Object[] messageArgs,
            String message) {}

    public record Reference(
            String nodeKey,
            String targetType,
            String refId,
            String name,
            String definitionId,
            String revision) {}
}
