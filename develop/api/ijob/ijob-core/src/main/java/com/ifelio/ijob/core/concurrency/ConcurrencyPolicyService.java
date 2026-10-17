package com.ifelio.ijob.core.concurrency;

import com.github.pagehelper.PageHelper;
import com.ifelio.ijob.common.exception.BusinessException;
import com.ifelio.ijob.common.response.PageResult;
import com.ifelio.ijob.common.response.ResultCode;
import com.ifelio.ijob.contract.concurrency.ConcurrencyMetadataDTO;
import com.ifelio.ijob.contract.concurrency.ConcurrencyResourceDTO;
import com.ifelio.ijob.contract.concurrency.ConcurrencyResourcePageQuery;
import com.ifelio.ijob.contract.concurrency.ConcurrencyResourceUpsertRequest;
import com.ifelio.ijob.contract.concurrency.PluginActivityDTO;
import com.ifelio.ijob.contract.concurrency.PluginConcurrencyPolicyDTO;
import com.ifelio.ijob.contract.concurrency.PluginConcurrencyPolicyUpdateRequest;
import com.ifelio.ijob.contract.concurrency.TaskConcurrencyPolicyDTO;
import com.ifelio.ijob.contract.concurrency.TaskConcurrencyPolicyUpdateRequest;
import com.ifelio.ijob.contract.enums.PluginType;
import com.ifelio.ijob.core.query.PageResults;
import com.ifelio.ijob.infra.entity.concurrency.ConcurrencyResource;
import com.ifelio.ijob.infra.entity.concurrency.PluginConcurrencyPolicy;
import com.ifelio.ijob.infra.entity.concurrency.TaskConcurrencyPolicy;
import com.ifelio.ijob.infra.mapper.concurrency.ConcurrencyContextMapper;
import com.ifelio.ijob.infra.mapper.concurrency.ConcurrencyResourceMapper;
import com.ifelio.ijob.infra.mapper.concurrency.PluginConcurrencyPolicyMapper;
import com.ifelio.ijob.infra.mapper.concurrency.TaskConcurrencyPolicyMapper;
import com.ifelio.ijob.infra.mapper.coordination.ConcurrencyPermitMapper;
import com.ifelio.ijob.infra.mapper.runtime.InstanceMapper;
import com.ifelio.ijob.infra.mapper.task.TaskMapper;
import com.ifelio.ijob.infra.mapper.task.TaskPublicationIdentityRow;
import com.ifelio.ijob.infra.mapper.task.TaskPublicationMapper;

import lombok.RequiredArgsConstructor;

import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class ConcurrencyPolicyService {
    private static final int MAX_CONCURRENCY = 10_000;
    private static final Pattern CODE = Pattern.compile("[A-Za-z0-9._-]+");
    private final ConcurrencyResourceMapper resourceMapper;
    private final PluginConcurrencyPolicyMapper pluginPolicyMapper;
    private final TaskConcurrencyPolicyMapper taskPolicyMapper;
    private final TaskPublicationMapper taskPublicationMapper;
    private final TaskMapper taskMapper;
    private final InstanceMapper instanceMapper;
    private final ConcurrencyPermitMapper permitMapper;
    private final LockService lockService;
    private final com.ifelio.ijob.infra.coordination.RuntimeMutationGuard guard;
    private final ConcurrencyContextMapper contextMapper;
    private final com.ifelio.ijob.common.config.AppProperties appProperties;

    public PageResult<ConcurrencyResourceDTO> queryResources(ConcurrencyResourcePageQuery query) {
        var input = query == null ? new ConcurrencyResourcePageQuery() : query;
        com.ifelio.ijob.core.auth.AuditService.page(input.getPageNo(), input.getPageSize());
        var filter = new ConcurrencyResourcePageQuery();
        filter.setKeyword(searchPattern(input.getKeyword()));
        filter.setCode(searchPattern(input.getCode()));
        filter.setName(searchPattern(input.getName()));
        filter.setEnabled(input.getEnabled());
        PageResult<ConcurrencyResource> rows;
        try (var ignored = PageHelper.startPage(input.getPageNo(), input.getPageSize())) {
            rows = PageResults.from(resourceMapper.selectPage(filter));
        }
        var result = new PageResult<ConcurrencyResourceDTO>();
        BeanUtils.copyProperties(rows, result, "list");
        result.setList(resourceDtos(rows.getList()));
        return result;
    }

    public ConcurrencyResourceDTO getResourceByCode(String code) {
        if (code == null || code.length() > 128 || !CODE.matcher(code.trim()).matches())
            throw new BusinessException(ResultCode.PARAM_ERROR);
        var resource = resourceMapper.selectByCode(code.trim());
        if (resource == null)
            throw BusinessException.localized(
                    ResultCode.NOT_FOUND, "Concurrency resource does not exist");
        return resourceDtos(List.of(resource)).getFirst();
    }

    public List<ConcurrencyResourceDTO> listResourcesByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return List.of();
        if (ids.size() > 500 || ids.stream().anyMatch(id -> id == null || id <= 0))
            throw new BusinessException(ResultCode.PARAM_ERROR);
        return resourceDtos(resourceMapper.selectByIds(ids.stream().distinct().toList()));
    }

    private String searchPattern(String value) {
        if (value == null || value.isBlank()) return null;
        if (value.length() > 128) throw new BusinessException(ResultCode.PARAM_ERROR);
        return "%"
                + value.trim()
                        .toLowerCase(java.util.Locale.ROOT)
                        .replace("!", "!!")
                        .replace("%", "!%")
                        .replace("_", "!_")
                        .replace("[", "![")
                + "%";
    }

    public ConcurrencyResourceDTO getResource(Long id) {
        return resourceDtos(List.of(requireResource(id))).getFirst();
    }

    @Transactional
    public Long createResource(ConcurrencyResourceUpsertRequest request) {
        guard.lock(com.ifelio.ijob.infra.coordination.RuntimeMutationGuard.CONCURRENCY);
        validateResourceRequest(request);
        String code = request.getCode().trim();
        if (resourceMapper.selectByCode(code) != null) {
            throw conflict("Concurrency resource code already exists");
        }
        LocalDateTime now = LocalDateTime.now();
        ConcurrencyResource resource = toResource(request, now);
        resource.setCreateTime(now);
        resource.setVersion(1L);
        if (resourceMapper.insert(resource) != 1 || resource.getId() == null) {
            throw system("Concurrency resource creation failed");
        }
        return resource.getId();
    }

    @Transactional
    public void updateResource(Long id, ConcurrencyResourceUpsertRequest request) {
        guard.lock(com.ifelio.ijob.infra.coordination.RuntimeMutationGuard.CONCURRENCY);
        validateResourceRequest(request);
        ConcurrencyResource old = requireResource(id);
        requireResourceVersion(old, request.getExpectedVersion());
        ConcurrencyResource duplicate = resourceMapper.selectByCode(request.getCode().trim());
        if (duplicate != null && !id.equals(duplicate.getId())) {
            throw conflict("Concurrency resource code already exists");
        }
        boolean restrictive =
                request.getMaxConcurrency() < old.getMaxConcurrency()
                        || Boolean.TRUE.equals(old.getEnabled())
                                && Boolean.FALSE.equals(request.getEnabled());
        if (restrictive) {
            requireNoActiveResourceInstances(id);
            requireNoActivePermits(ConcurrencyScopes.resource(id));
        }
        ConcurrencyResource resource = toResource(request, LocalDateTime.now());
        resource.setId(id);
        resource.setVersion(old.getVersion());
        if (resourceMapper.update(resource) != 1) {
            throw versionConflict();
        }
    }

    @Transactional
    public void deleteResource(Long id, Long expectedVersion) {
        guard.lock(com.ifelio.ijob.infra.coordination.RuntimeMutationGuard.CONCURRENCY);
        requireResourceVersion(requireResource(id), expectedVersion);
        if (resourceMapper.countTaskReferences(id) != 0) {
            throw conflict("Concurrency resource is still referenced by tasks");
        }
        requireNoActivePermits(ConcurrencyScopes.resource(id));
        if (resourceMapper.deleteById(id, expectedVersion) != 1) {
            throw versionConflict();
        }
    }

    public List<PluginConcurrencyPolicyDTO> listPluginPolicies() {
        var activity = pluginActivity();
        return pluginPolicyMapper.selectAll().stream()
                .map(
                        value -> {
                            var dto = toPluginDto(value);
                            dto.setActiveInstanceCount(
                                    activity.getOrDefault(value.getPluginType(), 0));
                            return dto;
                        })
                .toList();
    }

    public PluginConcurrencyPolicyDTO getPluginPolicy(PluginType type) {
        requirePluginType(type);
        PluginConcurrencyPolicy policy = pluginPolicyMapper.selectByPluginType(type);
        if (policy == null) return null;
        var dto = toPluginDto(policy);
        dto.setActiveInstanceCount(instanceMapper.countActiveByPluginType(type));
        return dto;
    }

    @Transactional
    public PluginConcurrencyPolicyDTO putPluginPolicy(
            PluginType type, PluginConcurrencyPolicyUpdateRequest request) {
        long revision =
                guard.lock(com.ifelio.ijob.infra.coordination.RuntimeMutationGuard.CONCURRENCY);
        requirePluginType(type);
        if (request == null
                || request.getExpectedVersion() == null
                || request.getExpectedVersion() < 0) {
            throw param("expectedVersion must be zero or greater");
        }
        validateMax(request.getMaxConcurrency(), "maxConcurrency");
        PluginConcurrencyPolicy old = pluginPolicyMapper.selectByPluginTypeForUpdate(type);
        if (instanceMapper.countActiveByPluginType(type) != 0) {
            throw conflict(
                    "Plugin concurrency policy cannot change while matching task instances are"
                            + " active");
        }
        LocalDateTime now = LocalDateTime.now();
        if (old == null) {
            if (request.getExpectedVersion() != 0)
                throw conflict("Plugin concurrency policy version changed");
            PluginConcurrencyPolicy created = new PluginConcurrencyPolicy();
            created.setPluginType(type);
            created.setMaxConcurrency(request.getMaxConcurrency());
            created.setVersion(revision);
            created.setCreateTime(now);
            created.setUpdateTime(now);
            if (pluginPolicyMapper.insert(created) != 1)
                throw conflict("Plugin concurrency policy creation conflicted");
            return toPluginDto(created);
        }
        if (!old.getVersion().equals(request.getExpectedVersion()))
            throw conflict("Plugin concurrency policy version changed");
        if (request.getMaxConcurrency() < old.getMaxConcurrency()) {
            requireNoActivePermits(ConcurrencyScopes.plugin(type));
        }
        if (pluginPolicyMapper.update(
                        type, request.getMaxConcurrency(), old.getVersion(), revision, now)
                != 1) {
            throw conflict("Plugin concurrency policy version changed");
        }
        old.setMaxConcurrency(request.getMaxConcurrency());
        old.setVersion(revision);
        old.setUpdateTime(now);
        return toPluginDto(old);
    }

    @Transactional
    public void deletePluginPolicy(PluginType type, Long expectedVersion) {
        guard.lock(com.ifelio.ijob.infra.coordination.RuntimeMutationGuard.CONCURRENCY);
        requirePluginType(type);
        if (expectedVersion == null || expectedVersion <= 0)
            throw param("expectedVersion must be greater than zero");
        if (instanceMapper.countActiveByPluginType(type) != 0) {
            throw conflict(
                    "Plugin concurrency policy cannot change while matching task instances are"
                            + " active");
        }
        PluginConcurrencyPolicy old = pluginPolicyMapper.selectByPluginTypeForUpdate(type);
        if (old == null) return;
        if (!old.getVersion().equals(expectedVersion))
            throw conflict("Plugin concurrency policy version changed");
        requireNoActivePermits(ConcurrencyScopes.plugin(type));
        if (pluginPolicyMapper.delete(type, expectedVersion) != 1)
            throw conflict("Plugin concurrency policy deletion conflicted");
    }

    public TaskConcurrencyPolicyDTO getTaskPolicy(Long taskId) {
        requirePositive(taskId, "Task ID");
        var task = taskMapper.selectById(taskId);
        if (task == null)
            throw BusinessException.localized(ResultCode.NOT_FOUND, "Task does not exist");
        if (Boolean.TRUE.equals(task.getRetired())) throw conflict("Task is retired");
        TaskConcurrencyPolicy policy = taskPolicyMapper.selectByTaskId(taskId);
        List<ConcurrencyResource> resources = taskPolicyMapper.selectResourcesByTaskId(taskId);
        return toTaskDto(taskId, policy, resources);
    }

    @Transactional
    public TaskConcurrencyPolicyDTO putTaskPolicy(
            Long taskId, TaskConcurrencyPolicyUpdateRequest request) {
        guard.lock(com.ifelio.ijob.infra.coordination.RuntimeMutationGuard.CONCURRENCY);
        requirePositive(taskId, "Task ID");
        if (request == null
                || request.getExpectedVersion() == null
                || request.getExpectedVersion() < 0) {
            throw param("expectedVersion must be zero or greater");
        }
        if (request.getTaskMaxConcurrency() != null)
            validateMax(request.getTaskMaxConcurrency(), "taskMaxConcurrency");
        TaskPublicationIdentityRow identity = taskPublicationMapper.selectIdentityForUpdate(taskId);
        validateTaskIdentity(identity);
        if (instanceMapper.countActiveByTaskId(taskId) != 0) {
            throw conflict("Task concurrency policy cannot change while task instances are active");
        }
        requireNoActivePermits(ConcurrencyScopes.task(taskId));
        List<Long> resourceIds = normalizeIds(request.getResourceIds());
        List<ConcurrencyResource> resources = resourceMapper.selectByIds(resourceIds);
        if (resources.size() != resourceIds.size()
                || resources.stream().anyMatch(r -> !Boolean.TRUE.equals(r.getEnabled()))) {
            throw conflict("Some concurrency resources do not exist or are disabled");
        }
        TaskConcurrencyPolicy old = taskPolicyMapper.selectByTaskId(taskId);
        LocalDateTime now = LocalDateTime.now();
        if (old == null) {
            if (request.getExpectedVersion() != 0)
                throw conflict("Task concurrency policy version changed");
            old = new TaskConcurrencyPolicy();
            old.setTaskId(taskId);
            old.setTaskMaxConcurrency(request.getTaskMaxConcurrency());
            old.setVersion(1L);
            old.setCreateTime(now);
            old.setUpdateTime(now);
            if (taskPolicyMapper.insert(old) != 1)
                throw conflict("Task concurrency policy creation conflicted");
        } else {
            if (!old.getVersion().equals(request.getExpectedVersion()))
                throw conflict("Task concurrency policy version changed");
            if (taskPolicyMapper.update(
                            taskId, request.getTaskMaxConcurrency(), old.getVersion(), now)
                    != 1) {
                throw conflict("Task concurrency policy version changed");
            }
            old.setTaskMaxConcurrency(request.getTaskMaxConcurrency());
            old.setVersion(old.getVersion() + 1);
            old.setUpdateTime(now);
        }
        taskPolicyMapper.deleteBindings(taskId);
        for (Long resourceId : resourceIds) {
            if (taskPolicyMapper.insertBinding(taskId, resourceId, now) != 1)
                throw system("Task concurrency resource binding failed");
        }
        return toTaskDto(taskId, old, resources);
    }

    private List<Long> normalizeIds(List<Long> values) {
        if (values == null) return List.of();
        Set<Long> ids = new LinkedHashSet<>();
        for (Long value : values) {
            requirePositive(value, "Concurrency resource ID");
            ids.add(value);
        }
        List<Long> result = new ArrayList<>(ids);
        result.sort(Long::compareTo);
        return result;
    }

    private void validateResourceRequest(ConcurrencyResourceUpsertRequest request) {
        if (request == null) throw param("Request body is required");
        if (request.getCode() == null
                || !CODE.matcher(request.getCode().trim()).matches()
                || request.getCode().trim().length() > 128) {
            throw param("Concurrency resource code is invalid");
        }
        if (request.getName() == null
                || request.getName().isBlank()
                || request.getName().trim().length() > 128) {
            throw param("Concurrency resource name is invalid");
        }
        validateMax(request.getMaxConcurrency(), "maxConcurrency");
        if (request.getDescription() != null && request.getDescription().length() > 500)
            throw param("Description must not exceed 500 characters");
    }

    private ConcurrencyResource toResource(
            ConcurrencyResourceUpsertRequest request, LocalDateTime now) {
        ConcurrencyResource resource = new ConcurrencyResource();
        resource.setCode(request.getCode().trim());
        resource.setName(request.getName().trim());
        resource.setMaxConcurrency(request.getMaxConcurrency());
        resource.setEnabled(request.getEnabled() == null ? Boolean.TRUE : request.getEnabled());
        resource.setDescription(request.getDescription());
        resource.setUpdateTime(now);
        return resource;
    }

    private ConcurrencyResource requireResource(Long id) {
        requirePositive(id, "Concurrency resource ID");
        ConcurrencyResource resource = resourceMapper.selectById(id);
        if (resource == null)
            throw BusinessException.localized(
                    ResultCode.NOT_FOUND, "Concurrency resource does not exist");
        return resource;
    }

    private void validateTaskIdentity(TaskPublicationIdentityRow identity) {
        if (identity == null)
            throw BusinessException.localized(ResultCode.NOT_FOUND, "Task does not exist");
        if (Boolean.TRUE.equals(identity.getRetired())) throw conflict("Task is retired");
    }

    private void requirePluginType(PluginType type) {
        if (type == null) throw param("Plugin type is required");
    }

    private void validateMax(Integer value, String field) {
        if (value == null || value <= 0 || value > MAX_CONCURRENCY)
            throw param(field + " must be between 1 and 10000");
    }

    private void requirePositive(Long value, String field) {
        if (value == null || value <= 0) throw param(field + " must be greater than zero");
    }

    private void requireNoActivePermits(String scope) {
        if (lockService.hasActiveLocalPermits(scope)
                || permitMapper.countActiveByScope(scope) != 0) {
            throw conflict("Concurrency policy cannot change while permits are active");
        }
    }

    private void requireNoActiveResourceInstances(Long resourceId) {
        if (instanceMapper.countActiveByConcurrencyResourceId(resourceId) != 0) {
            throw conflict(
                    "Concurrency resource cannot change while matching task instances are active");
        }
    }

    private ConcurrencyResourceDTO toResourceDto(ConcurrencyResource value) {
        ConcurrencyResourceDTO dto = new ConcurrencyResourceDTO();
        BeanUtils.copyProperties(value, dto);
        return dto;
    }

    private PluginConcurrencyPolicyDTO toPluginDto(PluginConcurrencyPolicy value) {
        PluginConcurrencyPolicyDTO dto = new PluginConcurrencyPolicyDTO();
        BeanUtils.copyProperties(value, dto);
        dto.setActiveInstanceCount(0);
        return dto;
    }

    private TaskConcurrencyPolicyDTO toTaskDto(
            Long taskId, TaskConcurrencyPolicy policy, List<ConcurrencyResource> resources) {
        TaskConcurrencyPolicyDTO dto = new TaskConcurrencyPolicyDTO();
        dto.setTaskId(taskId);
        dto.setTaskMaxConcurrency(policy == null ? null : policy.getTaskMaxConcurrency());
        dto.setVersion(policy == null ? 0L : policy.getVersion());
        dto.setCreateTime(policy == null ? null : policy.getCreateTime());
        dto.setUpdateTime(policy == null ? null : policy.getUpdateTime());
        dto.setResources(resourceDtos(resources));
        var task = taskMapper.selectById(taskId);
        if (task != null) {
            dto.setTaskName(task.getName());
            dto.setPluginType(task.getPluginType());
        }
        dto.setActiveInstanceCount(instanceMapper.countActiveByTaskId(taskId));
        return dto;
    }

    public ConcurrencyMetadataDTO metadata() {
        var activity = pluginActivity();
        var plugins =
                java.util.Arrays.stream(PluginType.values())
                        .map(
                                type -> {
                                    var item = new PluginActivityDTO();
                                    item.setType(type);
                                    item.setActiveInstanceCount(activity.getOrDefault(type, 0));
                                    return item;
                                })
                        .toList();
        var config = appProperties.getRuntime().getConcurrencyPermit();
        return new ConcurrencyMetadataDTO(
                plugins,
                1,
                MAX_CONCURRENCY,
                config.getWaitTimeout().toMillis(),
                config.getRetryInterval().toMillis(),
                config.getMaxRetryInterval().toMillis(),
                config.getRetryJitter().toMillis(),
                "API_NODE");
    }

    private java.util.Map<PluginType, Integer> pluginActivity() {
        var result = new java.util.EnumMap<PluginType, Integer>(PluginType.class);
        for (var item : contextMapper.selectPluginActivity())
            result.put(item.getType(), item.getActiveInstanceCount());
        return result;
    }

    private List<ConcurrencyResourceDTO> resourceDtos(List<ConcurrencyResource> resources) {
        var counts = new java.util.HashMap<Long, ConcurrencyResourceDTO>();
        var ids = resources.stream().map(ConcurrencyResource::getId).toList();
        for (int start = 0; start < ids.size(); start += 500) {
            var batch = ids.subList(start, Math.min(start + 500, ids.size()));
            for (var item : contextMapper.selectResourceContexts(batch))
                counts.put(item.getId(), item);
        }
        return resources.stream()
                .map(
                        value -> {
                            var dto = toResourceDto(value);
                            var context = counts.get(value.getId());
                            dto.setReferenceCount(
                                    context == null ? 0 : context.getReferenceCount());
                            dto.setActiveInstanceCount(
                                    context == null ? 0 : context.getActiveInstanceCount());
                            return dto;
                        })
                .toList();
    }

    private void requireResourceVersion(ConcurrencyResource resource, Long expectedVersion) {
        if (expectedVersion == null || expectedVersion <= 0)
            throw param("expectedVersion must be greater than zero");
        if (!expectedVersion.equals(resource.getVersion())) throw versionConflict();
    }

    private BusinessException versionConflict() {
        return new BusinessException(
                new com.ifelio.ijob.common.response.ErrorDescriptor(
                        ResultCode.CONFLICT,
                        "IJOB-CONCURRENCY-VERSION",
                        "error.concurrency.version",
                        "Concurrency resource version changed"));
    }

    private BusinessException param(String message) {
        return BusinessException.localized(ResultCode.PARAM_ERROR, message);
    }

    private BusinessException conflict(String message) {
        return BusinessException.localized(ResultCode.CONFLICT, message);
    }

    private BusinessException system(String message) {
        return BusinessException.localized(ResultCode.SYSTEM_ERROR, message);
    }
}
