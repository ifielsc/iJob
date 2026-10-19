// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.plugin.push;

import com.ifielsc.ijob.contract.enums.ResourceType;
import com.ifielsc.ijob.plugin.push.resolver.PushTaskPublishConfig;
import com.ifielsc.ijob.plugin.support.sql.ReadOnlySqlGuard;

import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class PushTaskConfigValidator {
    private static final int MAX_SQL_LENGTH = 1_000_000;
    private static final int MAX_BATCH_SIZE = 10_000;
    private static final int MAX_INFLIGHT = 10_000;
    private static final int MAX_PUBLISH_TIMEOUT_SECONDS = 3_600;
    private static final int MAX_RETRIES = 10;
    private static final int MAX_MESSAGE_BYTES = 16 * 1024 * 1024;
    private static final long MAX_MESSAGES = 100_000_000L;
    private static final long MAX_TOTAL_PAYLOAD_BYTES = 100L * 1024 * 1024 * 1024;

    public void validate(PushTaskConfig config) {
        if (config == null) {
            throw invalid("push config is required");
        }
        requirePositive(config.getSourceResourceId(), "push sourceResourceId must be positive");
        validateSql(config.getSql());
        validateTarget(config.getTarget());
        if (!"json".equals(config.getSerializer())) {
            throw invalid("push serializer must be json");
        }
        if (!"none".equals(config.getCompression()) && !"gzip".equals(config.getCompression())) {
            throw invalid("push compression must be none or gzip");
        }
        requireRange(
                config.getFetchSize(),
                1,
                MAX_BATCH_SIZE,
                "push fetchSize must be between 1 and 10000");
        requireRange(
                config.getBatchSize(),
                1,
                MAX_BATCH_SIZE,
                "push batchSize must be between 1 and 10000");
        requireRange(
                config.getMaxInflight(),
                1,
                MAX_INFLIGHT,
                "push maxInflight must be between 1 and 10000");
        requireRange(
                config.getPublishTimeoutSeconds(),
                1,
                MAX_PUBLISH_TIMEOUT_SECONDS,
                "push publishTimeoutSeconds must be between 1 and 3600");
        requireRange(
                config.getRetryTimes(), 0, MAX_RETRIES, "push retryTimes must be between 0 and 10");
        requireRange(
                config.getMaxMessageBytes(),
                1024,
                MAX_MESSAGE_BYTES,
                "push maxMessageBytes must be between 1024 and 16777216");
        requireRange(
                config.getMaxMessages(),
                1,
                MAX_MESSAGES,
                "push maxMessages must be between 1 and 100000000");
        requireRange(
                config.getMaxTotalPayloadBytes(),
                config.getMaxMessageBytes(),
                MAX_TOTAL_PAYLOAD_BYTES,
                "push maxTotalPayloadBytes is invalid");
    }

    private void validateSql(String sql) {
        if (sql == null || sql.isBlank()) {
            throw invalid("push SQL is required");
        }
        if (sql.length() > MAX_SQL_LENGTH || sql.indexOf('\0') >= 0) {
            throw invalid("push SQL is invalid");
        }
        if (!ReadOnlySqlGuard.isReadQuery(sql)) {
            throw invalid("push SQL must be a read query");
        }
    }

    private void validateTarget(PushTaskPublishConfig target) {
        if (target == null) {
            throw invalid("push target is required");
        }
        requirePositive(target.getResourceId(), "push target resourceId must be positive");
        ResourceType type = target.getType();
        if (type != ResourceType.RABBITMQ && type != ResourceType.KAFKA) {
            throw invalid("push target type must be RABBITMQ or KAFKA");
        }
        Map<String, Object> properties = target.getProperties();
        if (properties == null) {
            throw invalid("push target properties are required");
        }
        if (type == ResourceType.RABBITMQ) {
            requireTextProperty(properties, "exchange", true, 255);
            requireTextProperty(properties, "routingKey", false, 255);
        } else {
            String topic = requireTextProperty(properties, "topic", false, 249);
            if (!topic.matches("[a-zA-Z0-9._-]+") || topic.equals(".") || topic.equals("..")) {
                throw invalid("push Kafka topic is invalid");
            }
        }
    }

    private String requireTextProperty(
            Map<String, Object> properties, String name, boolean allowEmpty, int maxLength) {
        Object raw = properties.get(name);
        if (!(raw instanceof String value)
                || (!allowEmpty && value.isBlank())
                || value.length() > maxLength) {
            throw invalid("push target " + name + " is invalid");
        }
        return value;
    }

    private void requirePositive(Long value, String message) {
        if (value == null || value <= 0) {
            throw invalid(message);
        }
    }

    private void requireRange(Integer value, int minimum, int maximum, String message) {
        if (value == null || value < minimum || value > maximum) {
            throw invalid(message);
        }
    }

    private void requireRange(Long value, long minimum, long maximum, String message) {
        if (value == null || value < minimum || value > maximum) {
            throw invalid(message);
        }
    }

    private IllegalArgumentException invalid(String message) {
        return new IllegalArgumentException(message);
    }
}
