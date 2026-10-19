package com.ifielsc.ijob.core.task;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ifielsc.ijob.common.exception.BusinessException;
import com.ifielsc.ijob.common.response.ResultCode;
import com.ifielsc.ijob.contract.enums.PluginType;
import com.ifielsc.ijob.contract.enums.ResourceType;
import com.ifielsc.ijob.contract.resource.ResourceConfigView;
import com.ifielsc.ijob.contract.task.TaskExecutionContextDTO;
import com.ifielsc.ijob.core.error.CoreMessage;
import com.ifielsc.ijob.core.resource.ResourceConfigService;
import com.ifielsc.ijob.core.task.definition.ApiCallHeaders;
import com.ifielsc.ijob.infra.mapper.resource.ResourceMapper;
import com.ifielsc.ijob.infra.mapper.task.TaskMapper;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class TaskExecutionContextService {
    private final TaskMapper tasks;
    private final ResourceMapper resources;
    private final ObjectMapper json;
    private final ResourceConfigService resourceConfigs;

    public TaskExecutionContextDTO get(Long id) {
        if (id == null || id <= 0)
            throw new BusinessException(
                    ResultCode.PARAM_ERROR, CoreMessage.TASK_ID_MUST_BE_GREATER_THAN_ZERO);
        var task = tasks.selectById(id);
        if (task == null)
            throw new BusinessException(ResultCode.NOT_FOUND, CoreMessage.TASK_DOES_NOT_EXIST);
        String mode = null, engine = null, resourceId = null, resourceName = null;
        String serviceResourceId = null, protocol = null, method = null;
        if (task.getPluginType() == PluginType.API_CALL) {
            var config = ApiCallHeaders.object(task.getPluginConfig());
            try {
                long value = Long.parseLong(config.path("resourceId").asText());
                if (value <= 0) throw new NumberFormatException();
                serviceResourceId = String.valueOf(value);
                var resource = resources.selectById(value);
                if (resource != null && resource.getType() == ResourceType.SERVICE) {
                    resourceName = resource.getName();
                    var view = (ResourceConfigView.Service) resourceConfigs.view(resource);
                    protocol = view.getProtocol();
                }
                method =
                        "WEB_SERVICE".equals(protocol)
                                ? "POST"
                                : config.path("method").asText("POST").toUpperCase(Locale.ROOT);
                if (!Set.of("GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS")
                        .contains(method))
                    throw new IllegalStateException("Invalid API call method");
            } catch (NumberFormatException exception) {
                throw new IllegalStateException("Invalid service resource ID");
            }
        }
        if (task.getPluginType() == PluginType.SCRIPT) {
            try {
                var config = json.readTree(task.getPluginConfig());
                mode = config.path("executionMode").asText("LOCAL");
                engine = config.path("engine").asText();
                if (!Set.of("LOCAL", "SSH").contains(mode)
                        || !Set.of("SHELL", "BAT", "POWERSHELL", "PYTHON").contains(engine))
                    throw new IllegalStateException("Invalid script execution context");
                if ("SSH".equals(mode)) {
                    resourceId = config.path("sshResourceId").asText();
                    long value = Long.parseLong(resourceId);
                    if (value <= 0) throw new IllegalStateException("Invalid SSH resource ID");
                    var resource = resources.selectById(value);
                    resourceName = resource == null ? null : resource.getName();
                }
            } catch (JsonProcessingException | NumberFormatException exception) {
                throw new IllegalStateException("Invalid script execution context", exception);
            }
        }
        return new TaskExecutionContextDTO(
                task.getPluginType().name(),
                mode,
                engine,
                resourceId,
                resourceName,
                String.valueOf(task.getCurrentRevision()),
                serviceResourceId,
                protocol,
                method);
    }
}
