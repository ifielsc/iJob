package com.ifielsc.ijob.core.auth;

import com.ifielsc.ijob.common.response.ErrorDefinition;
import com.ifielsc.ijob.common.response.ResultCode;

public enum SecurityError implements ErrorDefinition {
    INPUT(ResultCode.PARAM_ERROR, "0400", "input", "Invalid account or audit query parameters"),
    PASSWORD(
            ResultCode.PARAM_ERROR,
            "0402",
            "password",
            "Password must contain at least 12 characters and at most 72 UTF-8 bytes"),
    DENIED(ResultCode.FORBIDDEN, "0403", "denied", "Permission denied"),
    NOT_FOUND(ResultCode.NOT_FOUND, "0404", "not-found", "Account or audit record not found"),
    CHANGE_REQUIRED(
            ResultCode.FORBIDDEN,
            "0405",
            "change-required",
            "Change your password before accessing business functions"),
    LAST_ADMIN(
            ResultCode.CONFLICT,
            "0406",
            "last-admin",
            "At least one enabled administrator must remain"),
    SELF_DISABLE(
            ResultCode.CONFLICT, "0407", "self-disable", "You cannot disable your own account"),
    CONFLICT(ResultCode.CONFLICT, "0409", "conflict", "Account changed or username already exists"),
    SELF_DELETE(ResultCode.CONFLICT, "0410", "self-delete", "You cannot delete your own account"),
    APPROVER_INVALID(
            ResultCode.PARAM_ERROR,
            "0411",
            "approver-invalid",
            "Select enabled accounts with approval permission"),
    DATABASE_REQUIRED(
            ResultCode.SERVICE_UNAVAILABLE,
            "0503",
            "database-required",
            "This operation requires database authentication");
    private final ResultCode resultCode;
    private final String suffix;
    private final String key;
    private final String message;

    SecurityError(ResultCode resultCode, String suffix, String key, String message) {
        this.resultCode = resultCode;
        this.suffix = suffix;
        this.key = key;
        this.message = message;
    }

    public ResultCode resultCode() {
        return resultCode;
    }

    public String errorCode() {
        return "IJOB-SECURITY-" + suffix;
    }

    public String messageKey() {
        return "error.security." + key;
    }

    public String defaultMessage() {
        return message;
    }
}
