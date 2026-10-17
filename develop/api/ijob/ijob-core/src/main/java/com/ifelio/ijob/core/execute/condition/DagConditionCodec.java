package com.ifelio.ijob.core.execute.condition;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.ifelio.ijob.common.exception.BusinessException;
import com.ifelio.ijob.common.response.ResultCode;
import com.ifelio.ijob.contract.job.DagConditionExpression;

import java.nio.charset.StandardCharsets;

public final class DagConditionCodec {
    public static final int CONDITION_VERSION = 1;

    private static final ObjectMapper OBJECT_MAPPER =
            new ObjectMapper()
                    .enable(DeserializationFeature.USE_BIG_INTEGER_FOR_INTS)
                    .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
                    .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
                    .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);

    private DagConditionCodec() {}

    public static String encode(DagConditionExpression expression) {
        if (expression == null) {
            return null;
        }
        try {
            String payload = OBJECT_MAPPER.writeValueAsString(expression);
            if (payload.getBytes(StandardCharsets.UTF_8).length
                    > DagConditionValidator.MAX_PAYLOAD_BYTES) {
                throw BusinessException.localized(
                        ResultCode.PARAM_ERROR, "Gateway condition payload exceeds 16384 bytes");
            }
            return payload;
        } catch (JsonProcessingException exception) {
            throw BusinessException.localized(
                    ResultCode.PARAM_ERROR, "Gateway condition cannot be serialized");
        }
    }

    public static DagConditionExpression decode(Integer version, String payload) {
        if (payload == null) {
            return null;
        }
        if (version == null || version != CONDITION_VERSION) {
            throw BusinessException.localized(
                    ResultCode.SYSTEM_ERROR, "Gateway condition version is not supported");
        }
        if (payload.getBytes(StandardCharsets.UTF_8).length
                > DagConditionValidator.MAX_PAYLOAD_BYTES) {
            throw BusinessException.localized(
                    ResultCode.SYSTEM_ERROR, "Gateway condition payload is invalid");
        }
        try {
            return OBJECT_MAPPER.readValue(payload, DagConditionExpression.class);
        } catch (JsonProcessingException exception) {
            throw BusinessException.localized(
                    ResultCode.SYSTEM_ERROR, "Gateway condition payload is invalid");
        }
    }
}
