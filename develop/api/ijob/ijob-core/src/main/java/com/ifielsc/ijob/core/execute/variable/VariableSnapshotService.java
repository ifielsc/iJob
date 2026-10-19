// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.core.execute.variable;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ifielsc.ijob.common.exception.BusinessException;
import com.ifielsc.ijob.common.response.ResultCode;
import com.ifielsc.ijob.infra.entity.runtime.Instance;
import com.ifielsc.ijob.infra.entity.runtime.Trigger;

import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@Service
public class VariableSnapshotService {
    public static final String RESERVED_PREFIX = "ijob.";
    public static final int MAX_VARIABLE_COUNT = 100;
    public static final int MAX_VARIABLE_NAME_LENGTH = 128;
    public static final int MAX_SNAPSHOT_BYTES = 65_536;

    private static final TypeReference<Map<String, Object>> VARIABLE_TYPE =
            new TypeReference<>() {};

    private final ObjectMapper objectMapper;

    public VariableSnapshotService(ObjectMapper objectMapper) {
        // API response null omission must not change persisted variable semantics.
        this.objectMapper =
                objectMapper
                        .copy()
                        .setDefaultPropertyInclusion(
                                JsonInclude.Value.construct(
                                        JsonInclude.Include.ALWAYS, JsonInclude.Include.ALWAYS));
    }

    public String serializeForm(com.ifielsc.ijob.contract.parameter.ParameterForm form) {
        if (form == null) return null;
        com.ifielsc.ijob.core.parameter.ParameterRules.definitions(form.getFields());
        try {
            String value = objectMapper.writeValueAsString(form);
            if (value.getBytes(StandardCharsets.UTF_8).length > MAX_SNAPSHOT_BYTES)
                throw com.ifielsc.ijob.core.parameter.ParameterRules.invalid("form");
            return value;
        } catch (JsonProcessingException e) {
            throw com.ifielsc.ijob.core.parameter.ParameterRules.invalid("form");
        }
    }

    public com.ifielsc.ijob.contract.parameter.ParameterForm deserializeForm(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return objectMapper
                    .readerFor(com.ifielsc.ijob.contract.parameter.ParameterForm.class)
                    .with(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
                    .readValue(value);
        } catch (JsonProcessingException e) {
            throw invalidSnapshot();
        }
    }

    public String serializeUserVariables(
            Map<String, Object> variables, com.ifielsc.ijob.contract.parameter.ParameterForm form) {
        if (form != null) {
            serializeForm(form);
            com.ifielsc.ijob.core.parameter.ParameterRules.values(form.getFields(), variables);
        }
        return serializeUserVariables(variables);
    }

    public String serializeUserVariables(Map<String, Object> variables) {
        Map<String, Object> normalized = normalize(variables, true, ResultCode.PARAM_ERROR);
        if (normalized.isEmpty()) {
            return null;
        }
        try {
            String json = objectMapper.writeValueAsString(normalized);
            if (json.getBytes(StandardCharsets.UTF_8).length > MAX_SNAPSHOT_BYTES) {
                throw BusinessException.localized(
                        ResultCode.PARAM_ERROR, "Variable snapshot exceeds 65536 bytes");
            }
            return json;
        } catch (JsonProcessingException exception) {
            throw BusinessException.localized(
                    ResultCode.PARAM_ERROR, "Variables must be JSON serializable");
        }
    }

    public Map<String, Object> buildTaskInputs(Instance instance, Trigger trigger) {
        if (instance == null) {
            throw invalidSnapshot();
        }
        Map<String, Object> variables =
                new LinkedHashMap<>(deserialize(instance.getInputVariables()));
        putBuiltIns(variables, instance, trigger);
        return Collections.unmodifiableMap(variables);
    }

    public Map<String, Object> deserializeUserVariables(String json) {
        return deserialize(json);
    }

    public String serializeOutputs(Map<String, Object> variables) {
        if (variables == null || variables.isEmpty()) {
            return null;
        }
        validateRuntimeVariables(variables);
        return serialize(variables, ResultCode.SYSTEM_ERROR);
    }

    public String mergeUpstreamOutputs(String baseVariables, Iterable<String> upstreamOutputs) {
        Map<String, Object> merged = new LinkedHashMap<>(deserialize(baseVariables));
        Set<String> producedKeys = new HashSet<>();
        for (String output : upstreamOutputs) {
            Map<String, Object> values = deserializeOutputs(output);
            for (Map.Entry<String, Object> entry : values.entrySet()) {
                if (!producedKeys.add(entry.getKey())) {
                    throw BusinessException.localized(
                            ResultCode.CONFLICT,
                            "Upstream variable name is ambiguous: " + entry.getKey());
                }
                if (entry.getValue() == null) {
                    merged.remove(entry.getKey());
                } else {
                    merged.put(entry.getKey(), entry.getValue());
                }
            }
        }
        if (merged.isEmpty()) {
            return null;
        }
        validateRuntimeVariables(merged);
        return serialize(merged, ResultCode.SYSTEM_ERROR);
    }

    private Map<String, Object> deserialize(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        if (json.getBytes(StandardCharsets.UTF_8).length > MAX_SNAPSHOT_BYTES) {
            throw invalidSnapshot();
        }
        try {
            return normalize(readVariables(json), false, ResultCode.SYSTEM_ERROR);
        } catch (JsonProcessingException exception) {
            throw invalidSnapshot();
        }
    }

    private Map<String, Object> deserializeOutputs(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        if (json.getBytes(StandardCharsets.UTF_8).length > MAX_SNAPSHOT_BYTES) {
            throw invalidSnapshot();
        }
        try {
            Map<String, Object> values = readVariables(json);
            validateRuntimeVariables(values);
            return values;
        } catch (JsonProcessingException exception) {
            throw invalidSnapshot();
        }
    }

    private void validateRuntimeVariables(Map<String, Object> variables) {
        if (variables.size() > MAX_VARIABLE_COUNT) {
            throw invalidSnapshot();
        }
        variables.keySet().forEach(key -> validateName(key, true, ResultCode.SYSTEM_ERROR));
    }

    private Map<String, Object> readVariables(String json) throws JsonProcessingException {
        return objectMapper
                .readerFor(VARIABLE_TYPE)
                .with(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
                .readValue(json);
    }

    private String serialize(Map<String, Object> variables, ResultCode errorCode) {
        try {
            String json = objectMapper.writeValueAsString(variables);
            if (json.getBytes(StandardCharsets.UTF_8).length > MAX_SNAPSHOT_BYTES) {
                throw new BusinessException(errorCode, "Variable snapshot exceeds 65536 bytes");
            }
            return json;
        } catch (JsonProcessingException exception) {
            throw new BusinessException(errorCode, "Variables must be JSON serializable");
        }
    }

    private Map<String, Object> normalize(
            Map<String, Object> variables, boolean rejectReservedNames, ResultCode errorCode) {
        if (variables == null || variables.isEmpty()) {
            return Map.of();
        }
        if (variables.size() > MAX_VARIABLE_COUNT) {
            throw new BusinessException(errorCode, "Variable count exceeds 100");
        }
        Map<String, Object> normalized = new LinkedHashMap<>();
        variables.forEach(
                (key, value) -> {
                    validateName(key, rejectReservedNames, errorCode);
                    if (value != null) {
                        normalized.put(key, value);
                    }
                });
        return Collections.unmodifiableMap(normalized);
    }

    private void validateName(String key, boolean rejectReservedNames, ResultCode errorCode) {
        if (key == null || key.isBlank() || key.length() > MAX_VARIABLE_NAME_LENGTH) {
            throw new BusinessException(
                    errorCode, "Variable name must contain 1 to 128 characters");
        }
        if (rejectReservedNames && key.startsWith(RESERVED_PREFIX)) {
            throw new BusinessException(
                    errorCode, "Variable names starting with ijob. are reserved");
        }
    }

    private void putBuiltIns(Map<String, Object> variables, Instance instance, Trigger trigger) {
        if (instance.getId() != null) {
            variables.put("ijob.instance.id", instance.getId());
        }
        if (instance.getRootInstanceId() != null) {
            variables.put("ijob.instance.rootId", instance.getRootInstanceId());
        }
        if (trigger == null) {
            return;
        }
        if (trigger.getId() == null
                || trigger.getTriggerType() == null
                || trigger.getTriggerTime() == null) {
            throw invalidSnapshot();
        }
        variables.put("ijob.trigger.id", trigger.getId());
        variables.put("ijob.trigger.type", trigger.getTriggerType().name());
        variables.put(
                "ijob.trigger.time",
                trigger.getTriggerTime().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        variables.put(
                "ijob.trigger.date",
                trigger.getTriggerTime().format(DateTimeFormatter.ISO_LOCAL_DATE));
        // Pre-upgrade triggers have no recorded business context; do not infer it on retry.
        if (trigger.getBusinessDate() != null
                && trigger.getBusinessTime() != null
                && trigger.getBusinessZoneId() != null) {
            variables.put("ijob.business.date", trigger.getBusinessDate());
            variables.put("ijob.business.time", trigger.getBusinessTime());
            variables.put("ijob.business.zoneId", trigger.getBusinessZoneId());
        }
        if (trigger.getScheduleId() != null) {
            variables.put("ijob.schedule.id", trigger.getScheduleId());
        }
    }

    private BusinessException invalidSnapshot() {
        return BusinessException.localized(
                ResultCode.SYSTEM_ERROR, "Runtime variable snapshot is invalid");
    }
}
