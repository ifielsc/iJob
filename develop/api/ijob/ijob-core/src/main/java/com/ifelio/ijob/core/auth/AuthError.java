package com.ifelio.ijob.core.auth;

import com.ifelio.ijob.common.response.ErrorDefinition;
import com.ifelio.ijob.common.response.ResultCode;

public enum AuthError implements ErrorDefinition {
    CREDENTIALS_REQUIRED(
            ResultCode.PARAM_ERROR,
            "IJOB-AUTH-0400",
            "error.auth.credentials-required",
            "Username and password are required"),
    INVALID_CREDENTIALS(
            ResultCode.UNAUTHORIZED,
            "IJOB-AUTH-0401",
            "error.auth.invalid-credentials",
            "Invalid username or password"),
    DISABLED(
            ResultCode.SERVICE_UNAVAILABLE,
            "IJOB-AUTH-0503",
            "error.auth.disabled",
            "Authentication service is disabled"),
    MISCONFIGURED(
            ResultCode.SERVICE_UNAVAILABLE,
            "IJOB-AUTH-0504",
            "error.auth.misconfigured",
            "Authentication service is not configured correctly");

    private final ResultCode resultCode;
    private final String errorCode;
    private final String messageKey;
    private final String defaultMessage;

    AuthError(ResultCode resultCode, String errorCode, String messageKey, String defaultMessage) {
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
