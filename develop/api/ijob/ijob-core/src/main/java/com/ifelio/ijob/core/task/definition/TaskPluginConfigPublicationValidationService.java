package com.ifelio.ijob.core.task.definition;

import com.ifelio.ijob.common.exception.BusinessException;
import com.ifelio.ijob.common.response.ResultCode;
import com.ifelio.ijob.contract.enums.PluginType;
import com.ifelio.ijob.contract.enums.ResourceType;
import com.ifelio.ijob.contract.resource.ResourceConfigView;
import com.ifelio.ijob.core.error.CoreMessage;
import com.ifelio.ijob.core.resource.ResourceConfigService;
import com.ifelio.ijob.infra.mapper.resource.ResourceMapper;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class TaskPluginConfigPublicationValidationService {
    private final Map<PluginType, TaskPluginConfigPublicationValidator> validators =
            new EnumMap<>(PluginType.class);
    private final ResourceMapper resourceMapper;
    private final ResourceConfigService resourceConfigs;
    private final DataCheckReferences dataCheckReferences;

    public TaskPluginConfigPublicationValidationService(
            List<TaskPluginConfigPublicationValidator> candidates,
            ResourceMapper resourceMapper,
            ResourceConfigService resourceConfigs,
            DataCheckReferences dataCheckReferences) {
        this.dataCheckReferences = dataCheckReferences;
        this.resourceMapper = resourceMapper;
        this.resourceConfigs = resourceConfigs;
        for (TaskPluginConfigPublicationValidator candidate : candidates) {
            Set<PluginType> types = candidate.supportTypes();
            if (types == null || types.isEmpty()) {
                throw new IllegalStateException("task plugin publication validator has no types");
            }
            for (PluginType type : types) {
                TaskPluginConfigPublicationValidator existing =
                        validators.putIfAbsent(type, candidate);
                if (existing != null) {
                    throw new IllegalStateException(
                            "duplicate task plugin publication validator: " + type);
                }
            }
        }
    }

    public void validate(
            PluginType type, String pluginConfig, TaskPluginConfigPublicationContext context) {
        TaskPluginConfigPublicationValidator validator = validators.get(type);
        if (validator != null) {
            try {
                validator.validate(type, pluginConfig, context);
                validateResourceReferences(referencedResourceIds(type, pluginConfig));
                if (type == PluginType.FILE_TRANSFER || type == PluginType.EVENT)
                    validateFileReferences(type, pluginConfig);
                if (type == PluginType.PUSH) validatePushReferences(pluginConfig);
                if (type == PluginType.ETL) validateEtlReferences(pluginConfig);
                if (type == PluginType.BATCH_ETL) validateBatchEtlReferences(pluginConfig);
                if (type == PluginType.API_CALL) validateServiceCall(pluginConfig);
                if (type == PluginType.DATA_CHECK) dataCheckReferences.validate(pluginConfig);
                if (type == PluginType.SERVER_MONITOR)
                    dataCheckReferences.validateMonitor(pluginConfig);
            } catch (BusinessException exception) {
                throw exception;
            } catch (IllegalArgumentException exception) {
                throw new BusinessException(
                        ResultCode.PARAM_ERROR,
                        CoreMessage.PLUGIN_CONFIGURATION_IS_INVALID,
                        exception);
            }
        }
    }

    public Set<Long> referencedResourceIds(PluginType type, String pluginConfig) {
        TaskPluginConfigPublicationValidator validator = validators.get(type);
        if (validator == null) {
            return Set.of();
        }
        try {
            Set<Long> ids = validator.referencedResourceIds(type, pluginConfig);
            if (ids == null || ids.stream().anyMatch(id -> id == null || id <= 0)) {
                throw new IllegalStateException("plugin resource reference result is invalid");
            }
            return Set.copyOf(ids);
        } catch (BusinessException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new BusinessException(
                    ResultCode.SYSTEM_ERROR,
                    CoreMessage.PLUGIN_RESOURCE_REFERENCE_RESOLUTION_FAILED,
                    exception);
        }
    }

    private void validateBatchEtlReferences(String pluginConfig) {
        var config = ApiCallHeaders.object(pluginConfig);
        validateTypedResource(config.path("sourceResourceId").asText(), ResourceType.DB);
        validateTypedResource(config.path("targetResourceId").asText(), ResourceType.DB);
    }

    private void validateEtlReferences(String pluginConfig) {
        var config = ApiCallHeaders.object(pluginConfig);
        for (String side : List.of("source", "target")) {
            var node = config.path(side);
            String type = node.path("type").asText();
            ResourceType resourceType =
                    switch (type) {
                        case "DATABASE" -> ResourceType.DB;
                        case "FILE" -> ResourceType.FILE;
                        case "KAFKA" -> ResourceType.KAFKA;
                        case "RABBITMQ" -> ResourceType.RABBITMQ;
                        default -> throw new IllegalArgumentException("Invalid ETL resource type");
                    };
            if (side.equals("target")
                    && (resourceType == ResourceType.KAFKA
                            || resourceType == ResourceType.RABBITMQ))
                throw new IllegalArgumentException("Invalid ETL target type");
            validateTypedResource(node.path("resourceId").asText(), resourceType);
        }
        for (String section : List.of("transforms", "validations")) {
            for (var item : config.path(section)) {
                var id = item.path("properties").get("resourceId");
                if (id != null && !id.isNull()) validateTypedResource(id.asText(), ResourceType.DB);
            }
        }
    }

    private void validatePushReferences(String pluginConfig) {
        var config = ApiCallHeaders.object(pluginConfig);
        var target = config.path("target");
        ResourceType targetType;
        try {
            targetType = ResourceType.valueOf(target.path("type").asText());
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Invalid push type");
        }
        if (targetType != ResourceType.KAFKA && targetType != ResourceType.RABBITMQ)
            throw new IllegalArgumentException("Invalid push type");
        validateTypedResource(config.path("sourceResourceId").asText(), ResourceType.DB);
        validateTypedResource(target.path("resourceId").asText(), targetType);
    }

    private void validateTypedResource(String raw, ResourceType type) {
        if (!raw.matches("[1-9][0-9]*"))
            throw new IllegalArgumentException("Invalid resource reference");
        var resource = resourceMapper.selectById(Long.parseLong(raw));
        if (resource == null
                || resource.getType() != type
                || !Boolean.TRUE.equals(resource.getEnabled()))
            throw new IllegalArgumentException("Resource unavailable");
    }

    private void validateFileReferences(PluginType type, String pluginConfig) {
        var config = ApiCallHeaders.object(pluginConfig);
        if (type == PluginType.EVENT && !"PROBE_FILE".equals(config.path("action").asText()))
            return;
        List<String> fields =
                type == PluginType.EVENT
                        ? List.of("fileResourceId")
                        : "READ_ONLY".equals(config.path("mode").asText())
                                ? List.of("sourceResourceId")
                                : List.of("sourceResourceId", "targetResourceId");
        for (String field : fields) {
            String raw = config.path(field).asText();
            if (!raw.matches("[1-9][0-9]*"))
                throw new IllegalArgumentException("Invalid file reference");
            long id = Long.parseLong(raw);
            var resource = resourceMapper.selectById(id);
            if (resource == null
                    || resource.getType() != ResourceType.FILE
                    || !Boolean.TRUE.equals(resource.getEnabled()))
                throw new IllegalArgumentException("File resource is unavailable");
        }
    }

    private void validateServiceCall(String pluginConfig) {
        var config = ApiCallHeaders.object(pluginConfig);
        long id;
        try {
            id = Long.parseLong(config.path("resourceId").asText());
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Invalid service reference");
        }
        var resource = resourceMapper.selectById(id);
        if (resource == null
                || resource.getType() != ResourceType.SERVICE
                || !Boolean.TRUE.equals(resource.getEnabled()))
            throw new IllegalArgumentException("Service resource is unavailable");
        var view = (ResourceConfigView.Service) resourceConfigs.view(resource);
        boolean soap = "WEB_SERVICE".equals(view.getProtocol());
        if (soap != (config.hasNonNull("webService")))
            throw new IllegalArgumentException("Service protocol does not match task");
        var remoteStop = config.path("remoteStop");
        if (!remoteStop.isMissingNode() && !remoteStop.isNull()) {
            if (!remoteStop.isObject() || soap != remoteStop.hasNonNull("webService"))
                throw new IllegalArgumentException("Remote stop protocol does not match task");
            if ("OAUTH2_CLIENT_CREDENTIALS".equals(view.getAuthType())
                    && remoteStop.path("headers").isObject()) {
                var stopNames = remoteStop.path("headers").fieldNames();
                while (stopNames.hasNext())
                    if ("Authorization".equalsIgnoreCase(stopNames.next()))
                        throw new IllegalArgumentException("Authorization cannot override OAuth2");
            }
        }
        if ("OAUTH2_CLIENT_CREDENTIALS".equals(view.getAuthType())
                && config.path("headers").isObject()) {
            var names = config.path("headers").fieldNames();
            while (names.hasNext())
                if ("Authorization".equalsIgnoreCase(names.next()))
                    throw new IllegalArgumentException("Authorization cannot override OAuth2");
        }
    }

    private void validateResourceReferences(Set<Long> referencedIds) {
        if (referencedIds.isEmpty()) {
            return;
        }
        List<Long> expected = referencedIds.stream().sorted().toList();
        List<Long> existing = resourceMapper.selectIdsForUpdate(new ArrayList<>(expected));
        if (existing == null) {
            throw new BusinessException(
                    ResultCode.SYSTEM_ERROR,
                    CoreMessage.PLUGIN_RESOURCE_REFERENCE_VALIDATION_FAILED);
        }
        if (!existing.equals(expected)) {
            throw new BusinessException(
                    ResultCode.NOT_FOUND,
                    CoreMessage.A_RESOURCE_REFERENCED_BY_THE_PLUGIN_DOES_NOT_EXIST);
        }
    }
}
