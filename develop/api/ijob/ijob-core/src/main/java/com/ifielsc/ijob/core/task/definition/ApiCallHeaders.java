package com.ifielsc.ijob.core.task.definition;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ifielsc.ijob.common.exception.BusinessException;
import com.ifielsc.ijob.common.response.ResultCode;
import com.ifielsc.ijob.contract.enums.PluginType;
import com.ifielsc.ijob.contract.task.TaskDetailDTO;
import com.ifielsc.ijob.contract.task.TaskUpsertRequest;
import com.ifielsc.ijob.core.error.CoreMessage;
import com.ifielsc.ijob.core.resource.HiddenHeaders;
import com.ifielsc.ijob.infra.entity.task.TaskDefinition;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

/** The editor receives header names only; publication retains values from an immutable version. */
public final class ApiCallHeaders {
    private static final ObjectMapper JSON =
            new ObjectMapper().enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);

    private ApiCallHeaders() {}

    public static void project(TaskDetailDTO detail) {
        if (detail.getPluginType() != PluginType.API_CALL) return;
        ObjectNode config = object(detail.getPluginConfig());
        detail.setHeaderNames(new ArrayList<>(headers(config).keySet()));
        config.remove("headers");
        ObjectNode stop = stop(config);
        detail.setRemoteStopHeaderNames(new ArrayList<>(headers(stop).keySet()));
        stop.remove("headers");
        detail.setPluginConfig(config.toString());
    }

    public static void merge(
            TaskUpsertRequest request, TaskDefinition source, Long taskId, Long revision) {
        if (request.getPreservedHeaderNames() == null
                && request.getPreservedRemoteStopHeaderNames() == null) return;
        if (request.getPluginType() != PluginType.API_CALL) throw invalid();
        ObjectNode previous = JSON.createObjectNode();
        boolean preserving =
                request.getPreservedHeaderNames() != null
                                && !request.getPreservedHeaderNames().isEmpty()
                        || request.getPreservedRemoteStopHeaderNames() != null
                                && !request.getPreservedRemoteStopHeaderNames().isEmpty();
        if (preserving) {
            if (source == null
                    || source.getPluginType() != PluginType.API_CALL
                    || !source.getTaskId().equals(taskId)
                    || !source.getRevision().equals(revision)) throw invalid();
            previous = object(source.getPluginConfig());
        }
        ObjectNode config = object(request.getPluginConfig());
        if (request.getPreservedHeaderNames() != null)
            mergeHeaders(config, previous, request.getPreservedHeaderNames());
        if (request.getPreservedRemoteStopHeaderNames() != null) {
            if (!config.hasNonNull("remoteStop")
                    && !request.getPreservedRemoteStopHeaderNames().isEmpty()) throw invalid();
            if (config.hasNonNull("remoteStop"))
                mergeHeaders(
                        stop(config), stop(previous), request.getPreservedRemoteStopHeaderNames());
        }
        request.setPluginConfig(config.toString());
    }

    private static void mergeHeaders(
            ObjectNode current, ObjectNode previous, java.util.List<String> names) {
        current.set(
                "headers",
                JSON.valueToTree(HiddenHeaders.merge(headers(current), names, headers(previous))));
    }

    private static ObjectNode stop(ObjectNode config) {
        JsonNode node = config.get("remoteStop");
        if (node == null || node.isNull()) return JSON.createObjectNode();
        if (node instanceof ObjectNode object) return object;
        throw invalid();
    }

    public static ObjectNode object(String text) {
        try {
            JsonNode node = JSON.readTree(text);
            if (node instanceof ObjectNode object) return object;
        } catch (Exception ignored) {
            // Do not include request contents or parser diagnostics in user-visible errors.
        }
        throw invalid();
    }

    private static Map<String, String> headers(ObjectNode config) {
        Map<String, String> values = new LinkedHashMap<>();
        JsonNode node = config.get("headers");
        if (node == null || node.isNull()) return values;
        if (!node.isObject()) throw invalid();
        node.fields()
                .forEachRemaining(
                        entry -> {
                            if (!entry.getValue().isTextual()) throw invalid();
                            values.put(entry.getKey(), entry.getValue().textValue());
                        });
        return values;
    }

    private static BusinessException invalid() {
        return new BusinessException(
                ResultCode.PARAM_ERROR, CoreMessage.PLUGIN_CONFIGURATION_IS_INVALID);
    }
}
