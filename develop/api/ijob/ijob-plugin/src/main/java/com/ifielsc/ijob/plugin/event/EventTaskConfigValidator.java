// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.plugin.event;

import com.ifielsc.ijob.contract.enums.EventAction;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.regex.Pattern;

public final class EventTaskConfigValidator {
    private static final Pattern EVENT_CODE = Pattern.compile("[A-Za-z0-9._:-]{1,128}");

    private EventTaskConfigValidator() {}

    public static void validate(EventTaskConfig config, Integer taskTimeout) {
        if (config == null) {
            throw new IllegalArgumentException("Event configuration is required");
        }
        EventAction action = config.getAction() == null ? EventAction.PUBLISH : config.getAction();
        if (action != EventAction.WAIT_APPROVAL
                && (config.getEventCode() == null
                        || !EVENT_CODE.matcher(config.getEventCode()).matches())) {
            throw new IllegalArgumentException("Event code is invalid");
        }
        optionalTemplate(config.getBusinessKey(), 256, "Event business key");
        optionalTemplate(config.getCorrelationKey(), 256, "Event correlation key");
        optionalTemplate(config.getIdempotencyKey(), 256, "Event idempotency key");
        optionalTemplate(config.getSummary(), 500, "Event summary");
        positiveOrNull(config.getTimeoutSeconds(), "Event wait timeout");
        range(config.getLookbackSeconds(), 0, 604_800, "Event lookback");
        validateOccurredAt(config.getOccurredAt());
        if ((action == EventAction.WAIT_EVENT || action == EventAction.WAIT_APPROVAL)
                && taskTimeout != null
                && taskTimeout > 0) {
            throw new IllegalArgumentException(
                    "Waiting EVENT tasks must use timeoutSeconds instead of task timeout");
        }
        if (action == EventAction.WAIT_APPROVAL) {
            validateApproval(config);
        }
        if (action == EventAction.PROBE_FILE) {
            validateFileProbe(config);
        }
    }

    public static boolean isValidEventCode(String eventCode) {
        return eventCode != null && EVENT_CODE.matcher(eventCode).matches();
    }

    private static void validateOccurredAt(String occurredAt) {
        if (occurredAt == null || containsTemplate(occurredAt)) {
            return;
        }
        try {
            LocalDateTime.parse(occurredAt);
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException("Event occurredAt must be ISO local date-time");
        }
    }

    private static void validateApproval(EventTaskConfig config) {
        if (config.getAllowedOperatorIds() == null
                || config.getAllowedOperatorIds().isEmpty()
                || config.getAllowedOperatorIds().stream()
                        .anyMatch(
                                value ->
                                        value == null
                                                || value.isBlank()
                                                || value.length() > 128
                                                || value.contains("${"))) {
            throw new IllegalArgumentException("Approval operators are invalid");
        }
    }

    private static void validateFileProbe(EventTaskConfig config) {
        if (config.getFileResourceId() == null
                || config.getFileResourceId() <= 0
                || config.getFilePath() == null
                || config.getFilePath().isBlank()
                || config.getFilePath().length() > 1024
                || config.getFilePattern() == null
                || config.getFilePattern().isBlank()
                || config.getFilePattern().length() > 128
                || config.getFilePattern().contains("${")) {
            throw new IllegalArgumentException("File probe configuration is invalid");
        }
        range(config.getMinStableAgeSeconds(), 0, 86_400, "File stable age");
        range(config.getMaxMatches(), 1, 1_000, "File match limit");
        range(config.getMaxDepth(), 0, 128, "File probe depth");
        range(config.getMaxScannedEntries(), 1, 1_000_000, "File probe entry limit");
        range(config.getScanTimeoutSeconds(), 1, 3_600, "File probe scan timeout");
        try {
            PortableFileNameMatcher.compile(
                    config.getFilePattern(), !Boolean.FALSE.equals(config.getFileCaseSensitive()));
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("File probe pattern is invalid");
        }
    }

    private static void optionalTemplate(String value, int max, String field) {
        if (value != null && value.length() > max) {
            throw new IllegalArgumentException(field + " is invalid");
        }
    }

    private static void positiveOrNull(Integer value, String field) {
        if (value != null && value <= 0) {
            throw new IllegalArgumentException(field + " is invalid");
        }
    }

    private static void range(Integer value, int min, int max, String field) {
        if (value != null && (value < min || value > max)) {
            throw new IllegalArgumentException(field + " is invalid");
        }
    }

    private static boolean containsTemplate(String value) {
        return value != null && value.contains("${");
    }
}
