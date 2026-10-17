package com.ifelio.ijob.contract.auth;

/** Stable permission codes; labels are localized separately. */
public enum AccountPermission {
    SUMMARY_READ,
    DEFINITION_READ,
    SENSITIVE_READ,
    CONFIGURATION_WRITE,
    EXECUTION_CONTROL,
    APPROVAL_DECIDE,
    EVENT_PUBLISH,
    USER_MANAGE,
    AUDIT_READ,
    SELF_PASSWORD_CHANGE
}
