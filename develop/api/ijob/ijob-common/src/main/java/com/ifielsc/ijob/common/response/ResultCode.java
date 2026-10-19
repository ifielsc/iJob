package com.ifielsc.ijob.common.response;

public enum ResultCode implements ErrorDefinition {
    SUCCESS(200, "IJOB-COMMON-0000", "common.success", "Success"),

    PARAM_ERROR(400, "IJOB-COMMON-0400", "error.common.invalid-request", "Invalid request"),
    UNAUTHORIZED(401, "IJOB-COMMON-0401", "error.common.unauthorized", "Authentication required"),
    FORBIDDEN(403, "IJOB-COMMON-0403", "error.common.forbidden", "Access denied"),
    NOT_FOUND(404, "IJOB-COMMON-0404", "error.common.not-found", "Resource not found"),
    METHOD_NOT_ALLOWED(
            405, "IJOB-COMMON-0405", "error.common.method-not-allowed", "Method not allowed"),
    NOT_ACCEPTABLE(
            406,
            "IJOB-COMMON-0406",
            "error.common.not-acceptable",
            "Response format not supported"),
    PAYLOAD_TOO_LARGE(
            413, "IJOB-COMMON-0413", "error.common.payload-too-large", "Request is too large"),
    UNSUPPORTED_MEDIA_TYPE(
            415,
            "IJOB-COMMON-0415",
            "error.common.unsupported-media-type",
            "Content type not supported"),
    CONFLICT(
            409,
            "IJOB-COMMON-0409",
            "error.common.conflict",
            "Request conflicts with current state"),
    TOO_MANY_REQUESTS(
            429, "IJOB-COMMON-0429", "error.common.too-many-requests", "Too many requests"),

    SYSTEM_ERROR(500, "IJOB-COMMON-0500", "error.common.internal", "Internal server error"),
    SERVICE_UNAVAILABLE(503, "IJOB-COMMON-0503", "error.common.unavailable", "Service unavailable"),
    GATEWAY_TIMEOUT(504, "IJOB-COMMON-0504", "error.common.gateway-timeout", "Service timed out");

    private final int code;
    private final String errorCode;
    private final String messageKey;
    private final String defaultMessage;

    ResultCode(int code, String errorCode, String messageKey, String defaultMessage) {
        this.code = code;
        this.errorCode = errorCode;
        this.messageKey = messageKey;
        this.defaultMessage = defaultMessage;
    }

    public int code() {
        return code;
    }

    public String message() {
        return defaultMessage;
    }

    @Override
    public ResultCode resultCode() {
        return this;
    }

    @Override
    public String errorCode() {
        return errorCode;
    }

    @Override
    public String messageKey() {
        return messageKey;
    }

    @Override
    public String defaultMessage() {
        return defaultMessage;
    }
}
