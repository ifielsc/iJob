package com.ifelio.ijob.core.task.definition;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.pagehelper.PageHelper;
import com.ifelio.ijob.common.exception.BusinessException;
import com.ifelio.ijob.common.response.ResultCode;
import com.ifelio.ijob.contract.enums.PluginType;
import com.ifelio.ijob.contract.enums.ResourceType;
import com.ifelio.ijob.core.error.CoreMessage;
import com.ifelio.ijob.infra.entity.task.TaskDefinition;
import com.ifelio.ijob.infra.mapper.notify.NotifyConfigMapper;
import com.ifelio.ijob.infra.mapper.resource.ResourceMapper;
import com.ifelio.ijob.infra.mapper.task.TaskDefinitionResourceReferenceMapper;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Locks notification rows in the publication transaction; deletion uses those same locks. */
@Component
@RequiredArgsConstructor
public class DataCheckReferences {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final ResourceMapper resources;
    private final NotifyConfigMapper notifications;
    private final TaskDefinitionResourceReferenceMapper definitions;

    public void validate(String config) {
        JsonNode root = object(config);
        long resourceId = positiveId(root.get("resourceId"), true);
        var resource = resources.selectById(resourceId);
        if (resource == null
                || resource.getType() != ResourceType.DB
                || !Boolean.TRUE.equals(resource.getEnabled())) throw invalid();
        validateNotifications(notificationIds(root));
    }

    public void validateMonitor(String config) {
        JsonNode root = object(config);
        long resourceId = positiveId(root.get("resourceId"), true);
        var resource = resources.selectById(resourceId);
        if (resource == null
                || resource.getType() != ResourceType.MONITOR_SERVER
                || !Boolean.TRUE.equals(resource.getEnabled())) throw invalid();
        validateNotifications(monitorNotificationIds(root));
    }

    private void validateNotifications(Set<Long> referenced) {
        List<Long> ids = referenced.stream().sorted().toList();
        if (!ids.isEmpty()) {
            if (!ids.equals(notifications.selectIdsForUpdate(ids))) throw invalid();
            for (long id : ids) {
                var notification = notifications.selectById(id);
                if (notification == null || !Boolean.TRUE.equals(notification.getEnabled()))
                    throw invalid();
            }
        }
    }

    public void requireUnreferenced(List<Long> deleting) {
        // Caller holds notification row locks until its deletion transaction commits.
        Long upper = definitions.selectMaxDefinitionId();
        if (upper == null) return;
        if (upper <= 0) throw scanFailed();
        long after = 0;
        while (after < upper) {
            List<TaskDefinition> batch;
            PageHelper.startPage(1, 500, false);
            try {
                batch = definitions.selectDefinitionBatch(after, upper);
            } finally {
                PageHelper.clearPage();
            }
            if (batch == null || batch.size() > 500) throw scanFailed();
            if (batch.isEmpty()) break;
            for (TaskDefinition definition : batch) {
                if (definition == null
                        || definition.getId() == null
                        || definition.getId() <= after
                        || definition.getId() > upper
                        || definition.getPluginType() == null) throw scanFailed();
                after = definition.getId();
                if (definition.getPluginType() != PluginType.DATA_CHECK
                        && definition.getPluginType() != PluginType.SERVER_MONITOR) continue;
                Set<Long> referenced;
                try {
                    referenced =
                            definition.getPluginType() == PluginType.SERVER_MONITOR
                                    ? monitorNotificationIds(object(definition.getPluginConfig()))
                                    : notificationIds(object(definition.getPluginConfig()));
                } catch (RuntimeException exception) {
                    throw scanFailed();
                }
                if (referenced.stream().anyMatch(deleting::contains))
                    throw new BusinessException(
                            ResultCode.CONFLICT,
                            definition.getPluginType() == PluginType.SERVER_MONITOR
                                    ? CoreMessage.NOTIFICATION_REFERENCED_BY_MONITOR
                                    : CoreMessage.NOTIFICATION_REFERENCED_BY_DATA_CHECK);
            }
        }
    }

    private static Set<Long> monitorNotificationIds(JsonNode root) {
        JsonNode notify = root.get("notify");
        if (notify == null || notify.isNull()) return Set.of();
        if (!notify.isObject()) throw invalid();
        JsonNode enabled = notify.get("enabled");
        if (enabled != null && !enabled.isBoolean()) throw invalid();
        if (enabled == null || !enabled.booleanValue()) return Set.of();
        return Set.of(positiveId(notify.get("notifyConfigId"), true));
    }

    private static Set<Long> notificationIds(JsonNode root) {
        JsonNode handlers = root.get("handlers");
        if (handlers == null || handlers.isNull()) return Set.of();
        if (!handlers.isArray()) throw invalid();
        Set<Long> ids = new HashSet<>();
        for (JsonNode handler : handlers) {
            if (!handler.isObject() || !handler.path("type").isTextual()) throw invalid();
            if ("NOTIFY".equalsIgnoreCase(handler.path("type").asText()))
                ids.add(positiveId(handler.path("params").get("notifyConfigId"), false));
        }
        return ids;
    }

    private static long positiveId(JsonNode value, boolean allowText) {
        if (value == null || !(value.isIntegralNumber() || (allowText && value.isTextual())))
            throw invalid();
        String text = value.asText();
        if (!text.matches("[1-9][0-9]*")) throw invalid();
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException exception) {
            throw invalid();
        }
    }

    private static JsonNode object(String config) {
        try {
            JsonNode root = JSON.readTree(config);
            if (root == null || !root.isObject()) throw invalid();
            return root;
        } catch (java.io.IOException exception) {
            throw invalid();
        }
    }

    private static BusinessException invalid() {
        return new BusinessException(
                ResultCode.PARAM_ERROR, CoreMessage.PLUGIN_CONFIGURATION_IS_INVALID);
    }

    private static BusinessException scanFailed() {
        return new BusinessException(
                ResultCode.SYSTEM_ERROR, CoreMessage.NOTIFICATION_REFERENCE_CHECK_FAILED);
    }
}
