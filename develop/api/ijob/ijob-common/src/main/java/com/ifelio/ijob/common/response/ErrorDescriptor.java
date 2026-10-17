package com.ifelio.ijob.common.response;

import java.util.Objects;

public record ErrorDescriptor(
        ResultCode resultCode, String errorCode, String messageKey, String defaultMessage)
        implements ErrorDefinition {
    public ErrorDescriptor {
        Objects.requireNonNull(resultCode, "resultCode must not be null");
        Objects.requireNonNull(errorCode, "errorCode must not be null");
        Objects.requireNonNull(messageKey, "messageKey must not be null");
        Objects.requireNonNull(defaultMessage, "defaultMessage must not be null");
    }
}
