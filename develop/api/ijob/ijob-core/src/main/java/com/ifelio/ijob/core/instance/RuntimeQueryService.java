package com.ifelio.ijob.core.instance;

import com.github.pagehelper.PageHelper;
import com.ifelio.ijob.common.exception.BusinessException;
import com.ifelio.ijob.common.response.PageResult;
import com.ifelio.ijob.common.response.ResultCode;
import com.ifelio.ijob.contract.enums.TargetType;
import com.ifelio.ijob.contract.runtime.GatewayDecisionDTO;
import com.ifelio.ijob.contract.runtime.InstanceDTO;
import com.ifelio.ijob.contract.runtime.InstanceLogDTO;
import com.ifelio.ijob.contract.runtime.InstanceLogSnapshotDTO;
import com.ifelio.ijob.contract.runtime.InstancePageQuery;
import com.ifelio.ijob.contract.runtime.RunTreeDTO;
import com.ifelio.ijob.contract.runtime.TriggerDTO;
import com.ifelio.ijob.contract.runtime.TriggerPageQuery;
import com.ifelio.ijob.core.query.PageResults;
import com.ifelio.ijob.infra.entity.runtime.DagGatewayDecision;
import com.ifelio.ijob.infra.entity.runtime.Instance;
import com.ifelio.ijob.infra.entity.runtime.InstanceLog;
import com.ifelio.ijob.infra.entity.runtime.Trigger;
import com.ifelio.ijob.infra.mapper.runtime.DagGatewayDecisionMapper;
import com.ifelio.ijob.infra.mapper.runtime.InstanceLogMapper;
import com.ifelio.ijob.infra.mapper.runtime.InstanceMapper;
import com.ifelio.ijob.infra.mapper.runtime.TriggerMapper;

import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RuntimeQueryService {
    private static final int DEFAULT_LOG_LIMIT = 200;
    private static final int MAX_LOG_LIMIT = 500;
    private static final int MAX_LOG_SNAPSHOT_LIMIT = 5000;
    static final int MAX_TREE_NODES = 2000;
    static final int MAX_VARIABLE_TREE_NODES = 250;
    static final int MAX_GATEWAY_DECISIONS = 2000;
    static final int ID_BATCH_SIZE = 500;

    private final TriggerMapper triggerMapper;
    private final InstanceMapper instanceMapper;
    private final InstanceLogMapper instanceLogMapper;
    private final DagGatewayDecisionMapper gatewayDecisionMapper;

    @Autowired
    public RuntimeQueryService(
            TriggerMapper triggerMapper,
            InstanceMapper instanceMapper,
            InstanceLogMapper instanceLogMapper,
            DagGatewayDecisionMapper gatewayDecisionMapper) {
        this.triggerMapper = triggerMapper;
        this.instanceMapper = instanceMapper;
        this.instanceLogMapper = instanceLogMapper;
        this.gatewayDecisionMapper =
                java.util.Objects.requireNonNull(gatewayDecisionMapper, "gatewayDecisionMapper");
    }

    public PageResult<TriggerDTO> queryTriggers(TriggerPageQuery query) {
        TriggerPageQuery resolved = query == null ? new TriggerPageQuery() : query;
        validateOptionalPositiveId(resolved.getTargetId(), "Target ID");
        validateOptionalPositiveId(resolved.getScheduleId(), "Schedule ID");
        validateRange(resolved.getFrom(), resolved.getTo());
        resolved.setName(searchPattern(resolved.getName()));
        PageHelper.startPage(resolved.getPageNo(), resolved.getPageSize());
        try {
            return PageResults.from(triggerMapper.selectDtoList(resolved));
        } finally {
            PageHelper.clearPage();
        }
    }

    public TriggerDTO getTrigger(Long id) {
        requirePositiveId(id, "Trigger ID");
        Trigger trigger = triggerMapper.selectById(id);
        if (trigger == null) {
            throw notFound("Trigger not found");
        }

        TriggerDTO dto = new TriggerDTO();
        BeanUtils.copyProperties(trigger, dto);
        dto.setJobDefinitionId(trigger.getDefinitionId());
        dto.setRootInstanceId(instanceMapper.selectRootIdByTriggerId(id));
        return dto;
    }

    public PageResult<InstanceDTO> queryInstances(InstancePageQuery query) {
        InstancePageQuery resolved = query == null ? new InstancePageQuery() : query;
        validateOptionalPositiveId(resolved.getTargetId(), "Target ID");
        validateOptionalPositiveId(resolved.getTriggerId(), "Trigger ID");
        validateOptionalPositiveId(resolved.getRootInstanceId(), "Root instance ID");
        validateRange(resolved.getFrom(), resolved.getTo());
        resolved.setName(searchPattern(resolved.getName()));

        PageHelper.startPage(resolved.getPageNo(), resolved.getPageSize());
        try {
            List<InstanceDTO> items = instanceMapper.selectDtoList(resolved);
            items.forEach(
                    item -> {
                        item.setInputVariables(null);
                        item.setOutputVariables(null);
                    });
            return PageResults.from(items);
        } finally {
            PageHelper.clearPage();
        }
    }

    public InstanceDTO getInstance(Long id) {
        requirePositiveId(id, "Instance ID");
        Instance instance = instanceMapper.selectSummaryById(id);
        if (instance == null) {
            throw notFound("Instance not found");
        }
        return toInstanceDTO(instance, false);
    }

    public RunTreeDTO getRunTree(Long id, boolean includeVariables) {
        requirePositiveId(id, "Instance ID");
        Instance selected = instanceMapper.selectSummaryById(id);
        if (selected == null) {
            throw notFound("Instance not found");
        }
        Long rootId =
                selected.getRootInstanceId() == null
                        ? selected.getId()
                        : selected.getRootInstanceId();
        int maxNodes = includeVariables ? MAX_VARIABLE_TREE_NODES : MAX_TREE_NODES;
        List<Instance> loaded;
        PageHelper.startPage(1, maxNodes + 1, false);
        try {
            loaded =
                    includeVariables
                            ? instanceMapper.selectBoundedVariableTreeByRootId(rootId)
                            : instanceMapper.selectSummaryTreeByRootId(rootId);
        } finally {
            PageHelper.clearPage();
        }
        List<Instance> instances = loaded.subList(0, Math.min(maxNodes, loaded.size()));
        RunTreeDTO result = new RunTreeDTO();
        result.setMaxNodes(maxNodes);
        result.setTruncated(loaded.size() > maxNodes);
        result.setRootInstanceId(rootId);
        result.setNodes(
                instances.stream().map(value -> toInstanceDTO(value, includeVariables)).toList());
        List<Long> jobInstanceIds =
                instances.stream()
                        .filter(value -> value.getTargetType() == TargetType.JOB)
                        .map(Instance::getId)
                        .toList();
        List<DagGatewayDecision> decisions = new java.util.ArrayList<>();
        for (int offset = 0; offset < jobInstanceIds.size(); offset += ID_BATCH_SIZE) {
            int remaining = MAX_GATEWAY_DECISIONS - decisions.size();
            PageHelper.startPage(1, remaining + 1, false);
            List<DagGatewayDecision> batch;
            try {
                batch =
                        gatewayDecisionMapper.selectByJobInstanceIds(
                                jobInstanceIds.subList(
                                        offset,
                                        Math.min(offset + ID_BATCH_SIZE, jobInstanceIds.size())));
            } finally {
                PageHelper.clearPage();
            }
            decisions.addAll(batch.subList(0, Math.min(remaining, batch.size())));
            if (batch.size() > remaining) {
                result.setGatewayDecisionsTruncated(true);
                break;
            }
        }
        result.setGatewayDecisions(decisions.stream().map(this::toGatewayDecisionDTO).toList());
        return result;
    }

    public List<InstanceLogDTO> listLogs(Long instanceId, Long afterId, Integer limit) {
        requirePositiveId(instanceId, "Instance ID");
        if (instanceMapper.selectSummaryById(instanceId) == null) {
            throw notFound("Instance not found");
        }
        long resolvedAfterId = afterId == null ? 0L : afterId;
        if (resolvedAfterId < 0) {
            throw paramError("afterId cannot be negative");
        }
        int resolvedLimit = limit == null ? DEFAULT_LOG_LIMIT : limit;
        if (resolvedLimit <= 0) {
            throw paramError("limit must be greater than zero");
        }
        resolvedLimit = Math.min(resolvedLimit, MAX_LOG_LIMIT);

        return instanceLogMapper.selectAfterId(instanceId, resolvedAfterId, resolvedLimit).stream()
                .map(this::toLogDTO)
                .toList();
    }

    public List<InstanceLogDTO> listLogs(
            Long instanceId, Long afterId, Integer limit, String scope) {
        if (scope == null || "self".equals(scope)) return listLogs(instanceId, afterId, limit);
        if (!"run".equals(scope)) throw paramError("Invalid log scope");
        InstanceDTO selected = getInstance(instanceId);
        long cursor = afterId == null ? 0L : afterId;
        int size = limit == null ? DEFAULT_LOG_LIMIT : limit;
        if (cursor < 0 || size <= 0) throw paramError("Invalid log cursor or limit");
        Long root =
                selected.getRootInstanceId() == null ? instanceId : selected.getRootInstanceId();
        return instanceLogMapper
                .selectRunAfterId(root, cursor, Math.min(size, MAX_LOG_LIMIT))
                .stream()
                .map(this::toLogDTO)
                .toList();
    }

    /** Refreshable window; numeric primary keys are not commit-order cursors. */
    public InstanceLogSnapshotDTO getLogSnapshot(Long instanceId, Integer limit, String scope) {
        if (!"self".equals(scope) && !"run".equals(scope)) {
            throw paramError("Invalid log scope");
        }
        int size = limit == null ? DEFAULT_LOG_LIMIT : limit;
        if (size <= 0) {
            throw paramError("Invalid log limit");
        }
        size = Math.min(size, MAX_LOG_SNAPSHOT_LIMIT);
        InstanceDTO selected = getInstance(instanceId);
        boolean runScope = "run".equals(scope);
        Long target =
                runScope && selected.getRootInstanceId() != null
                        ? selected.getRootInstanceId()
                        : instanceId;
        List<InstanceLog> latest = instanceLogMapper.selectLatest(target, runScope, size + 1);
        boolean truncated = latest.size() > size;
        List<InstanceLogDTO> rows =
                latest.subList(0, Math.min(size, latest.size())).reversed().stream()
                        .map(this::toLogDTO)
                        .toList();
        return new InstanceLogSnapshotDTO(rows, truncated);
    }

    private InstanceLogDTO toLogDTO(InstanceLog log) {
        InstanceLogDTO dto = new InstanceLogDTO();
        BeanUtils.copyProperties(log, dto);
        return dto;
    }

    private InstanceDTO toInstanceDTO(Instance instance, boolean includeVariables) {
        InstanceDTO dto = new InstanceDTO();
        BeanUtils.copyProperties(instance, dto);
        dto.setJobDefinitionId(instance.getDefinitionId());
        if (!includeVariables) {
            dto.setInputVariables(null);
            dto.setOutputVariables(null);
        } else {
            var input = RuntimeVariablePreview.render(dto.getInputVariables());
            var output = RuntimeVariablePreview.render(dto.getOutputVariables());
            dto.setInputVariables(input.value());
            dto.setOutputVariables(output.value());
            dto.setVariablesRedacted(input.redacted() || output.redacted());
            dto.setVariablesTruncated(
                    dto.isVariablesTruncated() || input.omitted() || output.omitted());
        }
        return dto;
    }

    private GatewayDecisionDTO toGatewayDecisionDTO(DagGatewayDecision decision) {
        GatewayDecisionDTO dto = new GatewayDecisionDTO();
        BeanUtils.copyProperties(decision, dto);
        return dto;
    }

    private void validateRange(java.time.LocalDateTime from, java.time.LocalDateTime to) {
        if (from != null && to != null && from.isAfter(to)) {
            throw paramError("from cannot be after to");
        }
    }

    private String searchPattern(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        String value = name.trim();
        if (value.length() > 128) {
            throw paramError("name cannot exceed 128 characters");
        }
        return "%" + value + "%";
    }

    private void validateOptionalPositiveId(Long id, String field) {
        if (id != null) {
            requirePositiveId(id, field);
        }
    }

    private void requirePositiveId(Long id, String field) {
        if (id == null || id <= 0) {
            throw paramError(field + " must be greater than zero");
        }
    }

    private BusinessException paramError(String message) {
        return BusinessException.localized(ResultCode.PARAM_ERROR, message);
    }

    private BusinessException notFound(String message) {
        return BusinessException.localized(ResultCode.NOT_FOUND, message);
    }
}
