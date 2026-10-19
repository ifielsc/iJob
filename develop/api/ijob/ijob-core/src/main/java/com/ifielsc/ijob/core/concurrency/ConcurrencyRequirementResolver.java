package com.ifielsc.ijob.core.concurrency;

import com.ifielsc.ijob.common.exception.BusinessException;
import com.ifielsc.ijob.common.response.ResultCode;
import com.ifielsc.ijob.contract.enums.PluginType;
import com.ifielsc.ijob.infra.coordination.RuntimeMutationGuard;
import com.ifielsc.ijob.infra.entity.concurrency.ConcurrencyResource;
import com.ifielsc.ijob.infra.entity.concurrency.PluginConcurrencyPolicy;
import com.ifielsc.ijob.infra.entity.concurrency.TaskConcurrencyPolicy;
import com.ifielsc.ijob.infra.mapper.concurrency.PluginConcurrencyPolicyMapper;
import com.ifielsc.ijob.infra.mapper.concurrency.TaskConcurrencyPolicyMapper;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class ConcurrencyRequirementResolver {
    private final PluginConcurrencyPolicyMapper pluginPolicyMapper;
    private final TaskConcurrencyPolicyMapper taskPolicyMapper;
    private final RuntimeMutationGuard guard;

    @Autowired
    public ConcurrencyRequirementResolver(
            PluginConcurrencyPolicyMapper pluginPolicyMapper,
            TaskConcurrencyPolicyMapper taskPolicyMapper,
            RuntimeMutationGuard guard) {
        this.pluginPolicyMapper = pluginPolicyMapper;
        this.taskPolicyMapper = taskPolicyMapper;
        this.guard = guard;
    }

    public ConcurrencyRequirementResolver(
            PluginConcurrencyPolicyMapper pluginPolicyMapper,
            TaskConcurrencyPolicyMapper taskPolicyMapper) {
        this(pluginPolicyMapper, taskPolicyMapper, null);
    }

    @Transactional
    public List<ConcurrencyRequirement> resolve(Long taskId, PluginType pluginType) {
        long epoch = guard == null ? 0 : guard.lock(RuntimeMutationGuard.CONCURRENCY);
        if (taskId == null || taskId <= 0 || pluginType == null) {
            throw BusinessException.localized(
                    ResultCode.SYSTEM_ERROR, "Task concurrency identity is invalid");
        }
        List<ConcurrencyRequirement> result = new ArrayList<>();
        PluginConcurrencyPolicy plugin = pluginPolicyMapper.selectByPluginType(pluginType);
        if (plugin != null) {
            result.add(
                    requirement(ConcurrencyScopes.plugin(pluginType), plugin.getMaxConcurrency()));
        }
        TaskConcurrencyPolicy task = taskPolicyMapper.selectByTaskId(taskId);
        if (task != null && task.getTaskMaxConcurrency() != null) {
            result.add(requirement(ConcurrencyScopes.task(taskId), task.getTaskMaxConcurrency()));
        }
        List<ConcurrencyResource> resources = taskPolicyMapper.selectResourcesByTaskId(taskId);
        if (resources == null) {
            throw BusinessException.localized(
                    ResultCode.SYSTEM_ERROR, "Task concurrency resources cannot be read");
        }
        for (ConcurrencyResource resource : resources) {
            if (resource == null
                    || resource.getId() == null
                    || !Boolean.TRUE.equals(resource.getEnabled())) {
                throw BusinessException.localized(
                        ResultCode.CONFLICT,
                        "Task references a disabled or invalid concurrency resource");
            }
            result.add(
                    requirement(
                            ConcurrencyScopes.resource(resource.getId()),
                            resource.getMaxConcurrency()));
        }
        return result.stream()
                .map(r -> new ConcurrencyRequirement(r.scopeKey(), r.maxConcurrency(), epoch))
                .distinct()
                .sorted()
                .toList();
    }

    private ConcurrencyRequirement requirement(String key, Integer max) {
        try {
            return new ConcurrencyRequirement(key, max == null ? 0 : max);
        } catch (IllegalArgumentException exception) {
            throw BusinessException.localized(
                    ResultCode.SYSTEM_ERROR, "Concurrency policy data is invalid");
        }
    }
}
