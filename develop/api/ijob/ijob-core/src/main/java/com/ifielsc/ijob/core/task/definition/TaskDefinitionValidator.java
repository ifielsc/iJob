// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.core.task.definition;

import com.ifielsc.ijob.common.exception.BusinessException;
import com.ifielsc.ijob.common.response.MessageDefinition;
import com.ifielsc.ijob.common.response.ResultCode;
import com.ifielsc.ijob.common.routing.ExecutionGroupCodes;
import com.ifielsc.ijob.contract.task.TaskUpsertRequest;
import com.ifielsc.ijob.core.error.CoreMessage;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Component
public class TaskDefinitionValidator {
    static final int DEFAULT_RETRY_TIMES = 0;
    static final int DEFAULT_RETRY_INTERVAL = 0;
    static final boolean DEFAULT_ENABLED = true;

    private static final int MAX_NAME_LENGTH = 128;
    private static final int MAX_DESCRIPTION_LENGTH = 500;
    private static final int MAX_PLUGIN_CONFIG_BYTES = 8 * 1024 * 1024;

    public void validate(TaskUpsertRequest request) {
        if (request == null) {
            throw paramError(CoreMessage.REQUEST_BODY_IS_REQUIRED);
        }
        if (isBlank(request.getName())) {
            throw paramError(CoreMessage.TASK_NAME_IS_REQUIRED);
        }
        if (request.getName().trim().length() > MAX_NAME_LENGTH) {
            throw paramError(CoreMessage.TASK_NAME_MUST_NOT_EXCEED_128_CHARACTERS);
        }
        if (request.getPluginType() == null) {
            throw paramError(CoreMessage.PLUGIN_TYPE_IS_REQUIRED);
        }
        if (isBlank(request.getPluginConfig())) {
            throw paramError(CoreMessage.PLUGIN_CONFIGURATION_IS_REQUIRED);
        }
        if (request.getPluginConfig().getBytes(StandardCharsets.UTF_8).length
                > MAX_PLUGIN_CONFIG_BYTES) {
            throw paramError(CoreMessage.PLUGIN_CONFIGURATION_MUST_NOT_EXCEED_8_MIB);
        }
        requireNonNegative(request.getRetryTimes(), "retryTimes");
        requireNonNegative(request.getRetryInterval(), "retryInterval");
        try {
            TaskRetryPolicy.validate(
                    request.getRetryTimes() == null ? DEFAULT_RETRY_TIMES : request.getRetryTimes(),
                    request.getRetryInterval() == null
                            ? DEFAULT_RETRY_INTERVAL
                            : request.getRetryInterval());
        } catch (IllegalArgumentException exception) {
            throw paramError(
                    CoreMessage.RETRY_POLICY_INVALID,
                    TaskRetryPolicy.MAX_RETRIES,
                    TaskRetryPolicy.MAX_INTERVAL_SECONDS,
                    TaskRetryPolicy.MAX_TOTAL_DELAY_SECONDS);
        }
        if (request.getTimeout() != null && request.getTimeout() <= 0) {
            throw paramError(CoreMessage.TIMEOUT_MUST_BE_GREATER_THAN_ZERO);
        }
        if (request.getDescription() != null
                && request.getDescription().length() > MAX_DESCRIPTION_LENGTH) {
            throw paramError(CoreMessage.TASK_DESCRIPTION_MUST_NOT_EXCEED_500_CHARACTERS);
        }
        validateGroups(request);
    }

    private void validateGroups(TaskUpsertRequest request) {
        try {
            request.setGroups(ExecutionGroupCodes.normalize(request.getGroups()));
        } catch (IllegalArgumentException exception) {
            throw paramError(CoreMessage.EXECUTION_GROUPS_ARE_INVALID, exception.getMessage());
        }
    }

    private void requireNonNegative(Integer value, String fieldName) {
        if (value != null && value < 0) {
            throw paramError(CoreMessage.VALUE_MUST_NOT_BE_NEGATIVE, fieldName);
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private BusinessException paramError(MessageDefinition message, Object... arguments) {
        return new BusinessException(ResultCode.PARAM_ERROR, message, arguments);
    }
}
