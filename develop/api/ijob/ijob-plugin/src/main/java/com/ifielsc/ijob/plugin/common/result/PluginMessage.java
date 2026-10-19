// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.plugin.common.result;

import com.ifielsc.ijob.common.response.MessageDefinition;

/** Stable built-in plugin results; diagnostic logs are always English. */
public enum PluginMessage implements MessageDefinition {
    EVENT_FAILED("IJOB-PLUGIN-0049", "result.plugin.event-failed", "Event execution failed"),
    EVENT_ACCESS_DENIED(
            "IJOB-PLUGIN-0050", "result.plugin.event-access-denied", "Event access denied"),
    EVENT_CONFIG_INVALID(
            "IJOB-PLUGIN-0051",
            "result.plugin.event-config-invalid",
            "Event configuration is invalid"),
    FILE_PROBE_UNAVAILABLE(
            "IJOB-PLUGIN-0052",
            "result.plugin.file-probe-unavailable",
            "File probe target is unavailable"),
    FILE_PROBE_FAILED("IJOB-PLUGIN-0053", "result.plugin.file-probe-failed", "File probe failed"),
    FILE_PROBE_TIMEOUT(
            "IJOB-PLUGIN-0054", "result.plugin.file-probe-timeout", "File probe scan timeout"),
    EVENT_SERVICE_FAILED(
            "IJOB-PLUGIN-0055",
            "result.plugin.event-service-failed",
            "Event service is temporarily unavailable"),
    TRANSFER_PUBLICATION_UNCERTAIN(
            "IJOB-PLUGIN-0056",
            "result.plugin.transfer-publication-uncertain",
            "File publication result is uncertain; source files were retained"),
    API_FAILED("IJOB-PLUGIN-0001", "result.plugin.api-failed", "API call failed"),
    API_TIMEOUT("IJOB-PLUGIN-0002", "result.plugin.api-timeout", "API call timeout"),
    API_STATUS("IJOB-PLUGIN-0003", "result.plugin.api-status", "api invoke failed status:{0}"),
    API_BUSINESS_FAILED(
            "IJOB-PLUGIN-0004", "result.plugin.api-business-failed", "business response failed"),
    BATCH_ETL_TIMEOUT(
            "IJOB-PLUGIN-0005", "result.plugin.batch-etl-timeout", "Batch ETL execution timeout"),
    BATCH_ETL_RESOURCE_INVALID(
            "IJOB-PLUGIN-0006",
            "result.plugin.batch-etl-resource-invalid",
            "Batch ETL resource or statement is invalid"),
    BATCH_ETL_FAILED(
            "IJOB-PLUGIN-0007", "result.plugin.batch-etl-failed", "Batch ETL execution failed"),
    BATCH_ETL_CONFIG_INVALID(
            "IJOB-PLUGIN-0008",
            "result.plugin.batch-etl-config-invalid",
            "Batch ETL config is invalid"),
    SQL_TIMEOUT("IJOB-PLUGIN-0009", "result.plugin.sql-timeout", "SQL query timeout"),
    SQL_FAILED("IJOB-PLUGIN-0010", "result.plugin.sql-failed", "SQL execution failed"),
    LOCAL_JAR_FAILED(
            "IJOB-PLUGIN-0011",
            "result.plugin.local-jar-failed",
            "Local JAR task execution failed"),
    LOCAL_JAR_NO_RESULT(
            "IJOB-PLUGIN-0012",
            "result.plugin.local-jar-no-result",
            "Local JAR task returned no result"),
    LOCAL_JAR_REPORTED_FAILURE(
            "IJOB-PLUGIN-0013",
            "result.plugin.local-jar-reported-failure",
            "Local JAR task failed"),
    LOCAL_JAR_TIMEOUT(
            "IJOB-PLUGIN-0014", "result.plugin.local-jar-timeout", "Local JAR task timed out"),
    DATA_CHECK_SUCCESS(
            "IJOB-PLUGIN-0015", "result.plugin.data-check-success", "data check success"),
    DATA_CHECK_FAILED("IJOB-PLUGIN-0016", "result.plugin.data-check-failed", "data check failed"),
    DATA_CHECK_TIMEOUT(
            "IJOB-PLUGIN-0017", "result.plugin.data-check-timeout", "Data check query timeout"),
    DATA_CHECK_RESOURCE_INVALID(
            "IJOB-PLUGIN-0018",
            "result.plugin.data-check-resource-invalid",
            "data check resource is invalid"),
    DATA_CHECK_STATEMENT_INVALID(
            "IJOB-PLUGIN-0019",
            "result.plugin.data-check-statement-invalid",
            "data check statement or access is invalid"),
    EVENT_INBOUND_MATCHED(
            "IJOB-PLUGIN-0020",
            "result.plugin.event-inbound-matched",
            "Inbound event already matches"),
    EVENT_ALREADY_PUBLISHED(
            "IJOB-PLUGIN-0021", "result.plugin.event-already-published", "Event already published"),
    EVENT_PUBLISHED("IJOB-PLUGIN-0022", "result.plugin.event-published", "Event published"),
    EVENT_ALREADY_ARRIVED(
            "IJOB-PLUGIN-0023", "result.plugin.event-already-arrived", "Event already arrived"),
    APPROVAL_ALREADY_APPROVED(
            "IJOB-PLUGIN-0024",
            "result.plugin.approval-already-approved",
            "Approval already approved"),
    APPROVAL_REJECTED(
            "IJOB-PLUGIN-0025", "result.plugin.approval-rejected", "Approval was rejected"),
    EVENT_WAIT_TIMEOUT(
            "IJOB-PLUGIN-0026", "result.plugin.event-wait-timeout", "Event wait timed out"),
    APPROVAL_WITHDRAWN(
            "IJOB-PLUGIN-0027", "result.plugin.approval-withdrawn", "Approval was withdrawn"),
    EVENT_WAIT_STOPPED(
            "IJOB-PLUGIN-0028", "result.plugin.event-wait-stopped", "Event wait was stopped"),
    FILE_EVENTS_PUBLISHED(
            "IJOB-PLUGIN-0029",
            "result.plugin.file-events-published",
            "Published {0} file event(s), skipped {1} duplicate(s)"),
    WAITING_APPROVAL("IJOB-PLUGIN-0030", "result.plugin.waiting-approval", "Waiting for approval"),
    WAITING_EVENT("IJOB-PLUGIN-0031", "result.plugin.waiting-event", "Waiting for event"),
    WAITING_APPROVAL_INDEFINITE(
            "IJOB-PLUGIN-0032",
            "result.plugin.waiting-approval-indefinite",
            "Waiting for approval without timeout"),
    WAITING_EVENT_INDEFINITE(
            "IJOB-PLUGIN-0033",
            "result.plugin.waiting-event-indefinite",
            "Waiting for event without timeout"),
    SCRIPT_EXIT_NONZERO(
            "IJOB-PLUGIN-0034",
            "result.plugin.script-exit-nonzero",
            "Script exited with a non-zero status"),
    SCRIPT_TIMEOUT(
            "IJOB-PLUGIN-0035", "result.plugin.script-timeout", "Script execution timed out"),
    SCRIPT_FAILED("IJOB-PLUGIN-0036", "result.plugin.script-failed", "Script execution failed"),
    MONITOR_TIMEOUT("IJOB-PLUGIN-0037", "result.plugin.monitor-timeout", "server monitor timeout"),
    MONITOR_FAILED("IJOB-PLUGIN-0038", "result.plugin.monitor-failed", "server monitor failed"),
    MONITOR_REJECTED(
            "IJOB-PLUGIN-0039",
            "result.plugin.monitor-rejected",
            "server monitor request rejected"),
    MONITOR_RESOURCE_INVALID(
            "IJOB-PLUGIN-0040",
            "result.plugin.monitor-resource-invalid",
            "server monitor resource is invalid"),
    PUSH_TIMEOUT("IJOB-PLUGIN-0041", "result.plugin.push-timeout", "Push publish timeout"),
    PUSH_FAILED("IJOB-PLUGIN-0042", "result.plugin.push-failed", "push failed"),
    TRANSFER_TIMEOUT("IJOB-PLUGIN-0043", "result.plugin.transfer-timeout", "File transfer timeout"),
    TRANSFER_FAILED("IJOB-PLUGIN-0044", "result.plugin.transfer-failed", "file transfer failed"),
    ETL_TIMEOUT("IJOB-PLUGIN-0045", "result.plugin.etl-timeout", "ETL execution timeout"),
    ETL_RESOURCE_INVALID(
            "IJOB-PLUGIN-0046",
            "result.plugin.etl-resource-invalid",
            "ETL resource or statement is invalid"),
    ETL_FAILED("IJOB-PLUGIN-0047", "result.plugin.etl-failed", "ETL execution failed"),
    ETL_CONFIG_INVALID(
            "IJOB-PLUGIN-0048", "result.plugin.etl-config-invalid", "ETL config is invalid");
    private final String errorCode;
    private final String messageKey;
    private final String defaultMessage;

    PluginMessage(String code, String key, String message) {
        errorCode = code;
        messageKey = key;
        defaultMessage = message;
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
