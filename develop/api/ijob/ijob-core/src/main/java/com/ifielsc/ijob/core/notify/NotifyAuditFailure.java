// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.core.notify;

public enum NotifyAuditFailure {
    RATE_LIMITED("notification provider rate limit"),
    RATE_WAIT("waiting for shared notification budget"),
    CONFIG_LOOKUP_FAILED("notify configuration lookup failed"),
    CONFIG_NOT_FOUND("notify configuration was not found"),
    CONFIG_DISABLED("notify configuration is disabled"),
    MESSAGE_INVALID("notification message is invalid"),
    CHANNEL_UNAVAILABLE("notification channel is unavailable"),
    CHANNEL_REJECTED("notification channel rejected delivery"),
    CHANNEL_EXCEPTION("notification channel delivery failed"),
    CONFIG_INVALID("notification configuration is invalid", false),
    AUTHENTICATION_FAILED("notification authentication failed", false),
    DELIVERY_TIMEOUT("notification delivery timed out"),
    TRANSPORT_FAILED("notification transport failed"),
    RESPONSE_INVALID("notification response is invalid"),
    REMOTE_REJECTED("notification provider rejected delivery", false),
    REMOTE_FAILURE("notification provider reported a failure"),
    REMOTE_UNAVAILABLE("notification provider is temporarily unavailable");

    private final String summary;
    private final boolean retryable;

    NotifyAuditFailure(String summary) {
        this(summary, true);
    }

    NotifyAuditFailure(String summary, boolean retryable) {
        this.summary = summary;
        this.retryable = retryable;
    }

    public boolean isRetryable() {
        return retryable;
    }

    public String getSummary() {
        return summary;
    }
}
