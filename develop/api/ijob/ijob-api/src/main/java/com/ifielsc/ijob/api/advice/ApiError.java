package com.ifielsc.ijob.api.advice;

import com.ifielsc.ijob.common.response.ErrorDefinition;
import com.ifielsc.ijob.common.response.ResultCode;

enum ApiError implements ErrorDefinition {
    VALIDATION_FAILED(
            ResultCode.PARAM_ERROR,
            "IJOB-API-0400",
            "error.validation.failed",
            "Request validation failed"),
    REQUEST_BINDING_FAILED(
            ResultCode.PARAM_ERROR,
            "IJOB-API-0401",
            "error.request.binding",
            "Request parameter format is invalid"),
    EXECUTION_STOPPED(
            ResultCode.CONFLICT,
            "IJOB-RUNTIME-0409",
            "error.execution.stopped",
            "Execution has been stopped");

    private final ResultCode resultCode;
    private final String errorCode;
    private final String messageKey;
    private final String defaultMessage;

    ApiError(ResultCode resultCode, String errorCode, String messageKey, String defaultMessage) {
        this.resultCode = resultCode;
        this.errorCode = errorCode;
        this.messageKey = messageKey;
        this.defaultMessage = defaultMessage;
    }

    @Override
    public ResultCode resultCode() {
        return resultCode;
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
