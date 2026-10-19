package com.ifielsc.ijob.plugin.common.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ifielsc.ijob.plugin.batchetl.config.BatchEtlTaskConfig;
import com.ifielsc.ijob.plugin.datacheck.config.DataCheckConfig;
import com.ifielsc.ijob.plugin.event.EventTaskConfig;
import com.ifielsc.ijob.plugin.filetransfer.FileTransferConfig;
import com.ifielsc.ijob.plugin.localjar.LocalJarTaskConfig;
import com.ifielsc.ijob.plugin.push.PushTaskConfig;
import com.ifielsc.ijob.plugin.servermonitor.config.MonitorConfig;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@Component
public class PluginConfigCodec {
    private static final int MAX_CONFIG_BYTES = 8 * 1024 * 1024;

    private final ObjectMapper objectMapper;

    public PluginConfigCodec(ObjectMapper objectMapper) {
        // Configuration values must not inherit the HTTP response's null omission or rounding.
        this.objectMapper =
                objectMapper
                        .copy()
                        .setDefaultPropertyInclusion(
                                JsonInclude.Value.construct(
                                        JsonInclude.Include.NON_NULL, JsonInclude.Include.ALWAYS))
                        .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
                        .enable(DeserializationFeature.USE_BIG_INTEGER_FOR_INTS);
    }

    public <T> T read(String json, Class<T> type, String pluginName) {
        requireSize(json, pluginName);
        try {
            if (type == LocalJarTaskConfig.class) {
                T value =
                        objectMapper
                                .readerFor(type)
                                .with(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
                                .with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                                .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                                .with(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
                                .with(DeserializationFeature.USE_BIG_INTEGER_FOR_INTS)
                                .without(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
                                .readValue(json);
                if (value == null) throw invalid(pluginName, null);
                return value;
            }
            if (type == PushTaskConfig.class) validatePushIntegers(json);
            T value =
                    (type == com.ifielsc.ijob.plugin.etl.config.EtlTaskConfig.class
                                    || type == MonitorConfig.class
                                    || type == BatchEtlTaskConfig.class
                                    || type == DataCheckConfig.class
                                    || type == FileTransferConfig.class
                                    || type == EventTaskConfig.class
                                    || type == com.ifielsc.ijob.plugin.apicall.ApiCallConfig.class)
                            ? objectMapper
                                    .readerFor(type)
                                    .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                                    .with(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
                                    .with(DeserializationFeature.USE_BIG_INTEGER_FOR_INTS)
                                    .without(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
                                    .readValue(json)
                            : objectMapper.readValue(json, type);
            if (value == null) {
                throw invalid(pluginName, null);
            }
            return value;
        } catch (JsonProcessingException exception) {
            throw invalid(pluginName, exception);
        }
    }

    public <T> T convert(Object value, Class<T> type, String pluginName) {
        if (type == com.ifielsc.ijob.plugin.etl.config.EtlTaskConfig.class
                || type == LocalJarTaskConfig.class
                || type == BatchEtlTaskConfig.class
                || type == MonitorConfig.class)
            return read(write(value, pluginName), type, pluginName);
        try {
            return objectMapper.convertValue(value, type);
        } catch (IllegalArgumentException exception) {
            throw invalid(pluginName, exception);
        }
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> toMap(Object value, String pluginName) {
        return convert(value, Map.class, pluginName);
    }

    public String write(Object value, String pluginName) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw invalid(pluginName, exception);
        }
    }

    private void validatePushIntegers(String json) throws JsonProcessingException {
        var node =
                objectMapper
                        .reader()
                        .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                        .readTree(json);
        if (node == null || !node.isObject())
            throw new IllegalArgumentException("Push config is invalid");
        for (String key :
                List.of(
                        "sourceResourceId",
                        "fetchSize",
                        "batchSize",
                        "maxInflight",
                        "publishTimeoutSeconds",
                        "retryTimes",
                        "maxMessageBytes",
                        "maxMessages",
                        "maxTotalPayloadBytes")) requireExactInteger(node.get(key));
        requireExactInteger(node.path("target").get("resourceId"));
    }

    private void requireExactInteger(JsonNode value) {
        if (value == null || value.isNull()) return;
        if ((!value.isIntegralNumber() && !value.isTextual())
                || !value.asText().matches("-?[0-9]+"))
            throw new IllegalArgumentException("Push integer is invalid");
        try {
            Long.parseLong(value.asText());
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Push integer is invalid");
        }
    }

    private void requireSize(String json, String pluginName) {
        if (json == null
                || json.isBlank()
                || json.getBytes(StandardCharsets.UTF_8).length > MAX_CONFIG_BYTES) {
            throw invalid(pluginName, null);
        }
    }

    private IllegalArgumentException invalid(String pluginName, Throwable cause) {
        String message = pluginName + " config is invalid";
        return cause == null
                ? new IllegalArgumentException(message)
                : new IllegalArgumentException(message, cause);
    }
}
