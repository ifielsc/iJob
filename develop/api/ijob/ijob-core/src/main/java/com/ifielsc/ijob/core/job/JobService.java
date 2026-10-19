package com.ifielsc.ijob.core.job;

import com.github.pagehelper.PageHelper;
import com.ifielsc.ijob.common.exception.BusinessException;
import com.ifielsc.ijob.common.response.MessageDefinition;
import com.ifielsc.ijob.common.response.PageResult;
import com.ifielsc.ijob.common.response.ResultCode;
import com.ifielsc.ijob.common.routing.ExecutionGroupCodes;
import com.ifielsc.ijob.contract.common.BatchDeleteRequest;
import com.ifielsc.ijob.contract.enums.JobNodeKind;
import com.ifielsc.ijob.contract.enums.TargetType;
import com.ifielsc.ijob.contract.job.JobDetailResponse;
import com.ifielsc.ijob.contract.job.JobEdgeResponse;
import com.ifielsc.ijob.contract.job.JobGatewayResponse;
import com.ifielsc.ijob.contract.job.JobListDTO;
import com.ifielsc.ijob.contract.job.JobNodePolicyResponse;
import com.ifielsc.ijob.contract.job.JobNodeResponse;
import com.ifielsc.ijob.contract.job.JobPageQuery;
import com.ifielsc.ijob.contract.job.JobPublicationDTO;
import com.ifielsc.ijob.contract.job.JobUpsertRequest;
import com.ifielsc.ijob.core.error.CoreMessage;
import com.ifielsc.ijob.core.execute.condition.DagConditionCodec;
import com.ifielsc.ijob.core.job.definition.JobDefinitionPublicationService;
import com.ifielsc.ijob.core.job.definition.JobLayoutCodec;
import com.ifielsc.ijob.core.job.definition.JobPublicationResult;
import com.ifielsc.ijob.core.job.retirement.JobRetirementResult;
import com.ifielsc.ijob.core.job.retirement.JobRetirementService;
import com.ifielsc.ijob.core.management.BatchDeleteValidator;
import com.ifielsc.ijob.core.notify.NotifyBindingService;
import com.ifielsc.ijob.core.query.PageResults;
import com.ifielsc.ijob.core.schedule.ScheduleService;
import com.ifielsc.ijob.infra.entity.job.JobDefinition;
import com.ifielsc.ijob.infra.entity.job.JobDefinitionEdge;
import com.ifielsc.ijob.infra.entity.job.JobDefinitionNode;
import com.ifielsc.ijob.infra.entity.job.JobDefinitionNodePolicy;
import com.ifielsc.ijob.infra.mapper.group.DefinitionGroupRow;
import com.ifielsc.ijob.infra.mapper.job.JobCurrentDefinitionMapper;
import com.ifielsc.ijob.infra.mapper.job.JobCurrentDefinitionRow;
import com.ifielsc.ijob.infra.mapper.job.JobDefinitionEdgeMapper;
import com.ifielsc.ijob.infra.mapper.job.JobDefinitionGroupMapper;
import com.ifielsc.ijob.infra.mapper.job.JobDefinitionMapper;
import com.ifielsc.ijob.infra.mapper.job.JobDefinitionNodeMapper;
import com.ifielsc.ijob.infra.mapper.job.JobDefinitionNodePolicyMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class JobService {
    private static final int MAX_NAME_LENGTH = 128;
    private static final MessageDefinition INCOMPLETE_DEFINITION =
            CoreMessage.JOB_CURRENT_DEFINITION_DATA_IS_INCOMPLETE;

    private final JobCurrentDefinitionMapper currentDefinitionMapper;
    private final JobDefinitionMapper definitionMapper;
    private final JobDefinitionGroupMapper definitionGroupMapper;
    private final JobDefinitionNodeMapper nodeMapper;
    private final JobDefinitionNodePolicyMapper policyMapper;
    private final JobDefinitionEdgeMapper edgeMapper;
    private final ScheduleService scheduleService;
    private final NotifyBindingService notifyBindingService;
    private final JobDefinitionPublicationService publicationService;
    private final JobRetirementService retirementService;

    @Transactional(readOnly = true)
    public PageResult<JobListDTO> queryPage(JobPageQuery query) {
        JobPageQuery resolved = query == null ? new JobPageQuery() : query;
        if (resolved.getId() != null) requirePositiveId(resolved.getId());
        validateQuery(resolved);
        PageHelper.startPage(resolved.getPageNo(), resolved.getPageSize());
        PageResult<JobListDTO> result;
        try {
            List<JobListDTO> jobs = currentDefinitionMapper.selectDtoList(resolved);
            if (jobs == null) {
                throw systemError(CoreMessage.JOB_LIST_QUERY_FAILED);
            }
            result = PageResults.from(jobs);
        } finally {
            PageHelper.clearPage();
        }
        attachGroups(result.getList());
        return result;
    }

    @Transactional(readOnly = true)
    public JobDetailResponse getDetail(Long jobId) {
        requirePositiveId(jobId);
        JobCurrentDefinitionRow identity = currentDefinitionMapper.selectById(jobId);
        validateActiveIdentity(jobId, identity);

        JobDefinition definition = definitionMapper.selectById(identity.getCurrentDefinitionId());
        validateDefinition(identity, definition);
        return readDefinition(identity, definition, true);
    }

    @Transactional(readOnly = true)
    public JobDetailResponse getVersionDetail(Long jobId, Long definitionId) {
        requirePositiveId(jobId);
        JobCurrentDefinitionRow identity = currentDefinitionMapper.selectById(jobId);
        JobDefinition definition = definitionMapper.selectById(definitionId);
        if (identity == null || definition == null || !jobId.equals(definition.getJobId())) {
            throw new BusinessException(ResultCode.NOT_FOUND);
        }
        return readDefinition(identity, definition, false);
    }

    private JobDetailResponse readDefinition(
            JobCurrentDefinitionRow identity,
            JobDefinition definition,
            boolean currentAssociations) {
        Long jobId = identity.getId();
        List<JobDefinitionNode> nodes =
                requireList(nodeMapper.selectListByDefinitionId(definition.getId()));
        List<JobDefinitionNodePolicy> policies =
                requireList(policyMapper.selectListByDefinitionId(definition.getId()));
        List<JobDefinitionEdge> edges =
                requireList(edgeMapper.selectListByDefinitionId(definition.getId()));

        Map<Long, JobDefinitionNode> nodesById = indexNodes(definition.getId(), nodes);
        Map<Long, JobDefinitionNodePolicy> policiesByNodeId =
                indexPolicies(definition.getId(), nodesById.keySet(), policies);
        validateEdges(definition.getId(), nodesById.keySet(), edges);

        JobDetailResponse response = toResponse(identity, definition);
        response.setGroups(
                groupsFor(List.of(definition.getId())).getOrDefault(definition.getId(), List.of()));
        response.setNodes(
                nodes.stream()
                        .map(node -> toNodeResponse(node, policiesByNodeId.get(node.getId())))
                        .toList());
        response.setEdges(edges.stream().map(this::toEdgeResponse).toList());
        response.setLayout(JobLayoutCodec.decode(definition.getLayout()));
        response.setPublishedAt(definition.getCreateTime());
        response.setCurrent(definition.getId().equals(identity.getCurrentDefinitionId()));
        response.setSchedules(
                currentAssociations
                        ? scheduleService.listScheduleDetailsByTargetId(TargetType.JOB, jobId)
                        : List.of());
        response.setNotifyBindings(
                currentAssociations
                        ? notifyBindingService.listDetailsByTarget(TargetType.JOB, jobId)
                        : List.of());
        return response;
    }

    public JobPublicationDTO create(JobUpsertRequest request) {
        JobPublicationResult result = publicationService.create(request);
        log.info(
                "initial job revision published, jobId={}, revision={}",
                result.jobId(),
                result.revision());
        return toDto(result);
    }

    public JobPublicationDTO update(Long jobId, Long expectedRevision, JobUpsertRequest request) {
        JobPublicationResult result = publicationService.publish(jobId, expectedRevision, request);
        log.info(
                "job revision published, jobId={}, revision={}", result.jobId(), result.revision());
        return toDto(result);
    }

    public void delete(Long jobId) {
        JobRetirementResult result = retirementService.retire(jobId);
        log.info(
                "job retired, jobId={}, alreadyRetired={}",
                result.jobId(),
                result.alreadyRetired());
    }

    @Transactional
    public void deleteBatch(BatchDeleteRequest request) {
        List<Long> ids = BatchDeleteValidator.validate(request);
        retirementService.retireBatch(ids);
        log.info("jobs retired, count={}", ids.size());
    }

    private void validateQuery(JobPageQuery query) {
        if (query.getName() != null && query.getName().length() > MAX_NAME_LENGTH) {
            throw new BusinessException(
                    ResultCode.PARAM_ERROR, CoreMessage.JOB_NAME_MUST_NOT_EXCEED_128_CHARACTERS);
        }
        if (query.getGroup() != null) {
            try {
                query.setGroup(ExecutionGroupCodes.normalize(List.of(query.getGroup())).get(0));
            } catch (IllegalArgumentException exception) {
                throw new BusinessException(
                        ResultCode.PARAM_ERROR,
                        CoreMessage.EXECUTION_GROUPS_ARE_INVALID,
                        exception.getMessage());
            }
        }
    }

    private void attachGroups(List<JobListDTO> jobs) {
        Map<Long, List<String>> groups =
                groupsFor(jobs.stream().map(JobListDTO::getCurrentDefinitionId).toList());
        jobs.forEach(
                job -> job.setGroups(groups.getOrDefault(job.getCurrentDefinitionId(), List.of())));
    }

    private Map<Long, List<String>> groupsFor(List<Long> definitionIds) {
        if (definitionIds.isEmpty()) {
            return Map.of();
        }
        List<DefinitionGroupRow> rows = definitionGroupMapper.selectByDefinitionIds(definitionIds);
        if (rows == null) {
            throw systemError(CoreMessage.JOB_LIST_QUERY_FAILED);
        }
        return rows.stream()
                .collect(
                        Collectors.groupingBy(
                                DefinitionGroupRow::definitionId,
                                Collectors.mapping(
                                        DefinitionGroupRow::groupCode,
                                        Collectors.toUnmodifiableList())));
    }

    private void validateActiveIdentity(Long jobId, JobCurrentDefinitionRow identity) {
        if (identity == null
                || !jobId.equals(identity.getId())
                || Boolean.TRUE.equals(identity.getRetired())) {
            throw new BusinessException(ResultCode.NOT_FOUND, CoreMessage.JOB_DOES_NOT_EXIST);
        }
        if (identity.getRetired() == null
                || identity.getRetiredTime() != null
                || identity.getCurrentDefinitionId() == null
                || identity.getCurrentDefinitionId() <= 0
                || identity.getCurrentRevision() == null
                || identity.getCurrentRevision() <= 0
                || identity.getCreateTime() == null
                || identity.getUpdateTime() == null) {
            throw incompleteDefinition();
        }
    }

    private void validateDefinition(JobCurrentDefinitionRow identity, JobDefinition definition) {
        if (definition == null
                || !identity.getCurrentDefinitionId().equals(definition.getId())
                || !identity.getId().equals(definition.getJobId())
                || !identity.getCurrentRevision().equals(definition.getRevision())
                || definition.getName() == null
                || definition.getName().isBlank()
                || definition.getTimeout() != null && definition.getTimeout() <= 0
                || definition.getEnabled() == null
                || definition.getCreateTime() == null) {
            throw incompleteDefinition();
        }
    }

    private Map<Long, JobDefinitionNode> indexNodes(
            Long definitionId, List<JobDefinitionNode> nodes) {
        if (nodes.isEmpty()) {
            throw incompleteDefinition();
        }
        Map<Long, JobDefinitionNode> result = new HashMap<>();
        Set<String> nodeKeys = new HashSet<>();
        for (JobDefinitionNode node : nodes) {
            if (node == null
                    || node.getId() == null
                    || node.getId() <= 0
                    || !definitionId.equals(node.getDefinitionId())
                    || node.getNodeKey() == null
                    || node.getNodeKey().isBlank()
                    || !nodeKeys.add(node.getNodeKey())
                    || node.getName() == null
                    || node.getName().isBlank()
                    || !validNode(node)
                    || result.putIfAbsent(node.getId(), node) != null) {
                throw incompleteDefinition();
            }
        }
        return result;
    }

    private boolean validNode(JobDefinitionNode node) {
        JobNodeKind kind = node.getNodeKind() == null ? JobNodeKind.EXECUTION : node.getNodeKind();
        if (kind == JobNodeKind.GATEWAY) {
            return node.getGatewayMode() != null
                    && node.getNodeType() == null
                    && node.getRefId() == null
                    && node.getRefDefinitionId() == null
                    && node.getRefTaskDefinitionId() == null;
        }
        return node.getGatewayMode() == null
                && node.getNodeType() != null
                && node.getRefId() != null
                && node.getRefId() > 0;
    }

    private Map<Long, JobDefinitionNodePolicy> indexPolicies(
            Long definitionId, Set<Long> nodeIds, List<JobDefinitionNodePolicy> policies) {
        Map<Long, JobDefinitionNodePolicy> result = new HashMap<>();
        for (JobDefinitionNodePolicy policy : policies) {
            if (policy == null
                    || policy.getId() == null
                    || policy.getId() <= 0
                    || !definitionId.equals(policy.getDefinitionId())
                    || policy.getNodeId() == null
                    || !nodeIds.contains(policy.getNodeId())
                    || policy.getFailStrategy() == null
                    || policy.getExecuteCondition() == null
                    || result.putIfAbsent(policy.getNodeId(), policy) != null) {
                throw incompleteDefinition();
            }
        }
        if (!result.keySet().equals(nodeIds)) {
            throw incompleteDefinition();
        }
        return result;
    }

    private void validateEdges(
            Long definitionId, Set<Long> nodeIds, List<JobDefinitionEdge> edges) {
        Set<EdgeKey> paths = new HashSet<>();
        for (JobDefinitionEdge edge : edges) {
            if (edge == null
                    || edge.getId() == null
                    || edge.getId() <= 0
                    || !definitionId.equals(edge.getDefinitionId())
                    || !nodeIds.contains(edge.getFromNodeId())
                    || !nodeIds.contains(edge.getToNodeId())
                    || edge.getFromNodeId().equals(edge.getToNodeId())
                    || !paths.add(new EdgeKey(edge.getFromNodeId(), edge.getToNodeId()))) {
                throw incompleteDefinition();
            }
        }
    }

    private JobDetailResponse toResponse(
            JobCurrentDefinitionRow identity, JobDefinition definition) {
        JobDetailResponse response = new JobDetailResponse();
        response.setId(identity.getId());
        response.setName(definition.getName());
        response.setTimeout(definition.getTimeout());
        response.setEnabled(definition.getEnabled());
        response.setDescription(definition.getDescription());
        response.setCurrentDefinitionId(definition.getId());
        response.setCurrentRevision(definition.getRevision());
        response.setCreateTime(identity.getCreateTime());
        response.setUpdateTime(identity.getUpdateTime());
        return response;
    }

    private JobNodeResponse toNodeResponse(JobDefinitionNode node, JobDefinitionNodePolicy policy) {
        JobNodePolicyResponse policyResponse = new JobNodePolicyResponse();
        policyResponse.setId(policy.getId());
        policyResponse.setFailStrategy(policy.getFailStrategy());
        policyResponse.setExecuteCondition(policy.getExecuteCondition());

        JobNodeResponse response = new JobNodeResponse();
        response.setId(node.getId());
        response.setNodeKey(node.getNodeKey());
        response.setName(node.getName());
        JobNodeKind kind = node.getNodeKind() == null ? JobNodeKind.EXECUTION : node.getNodeKind();
        response.setKind(kind);
        response.setNodeType(node.getNodeType());
        response.setRefId(node.getRefId());
        response.setRefDefinitionId(
                node.getNodeType() == TargetType.JOB
                        ? node.getRefDefinitionId()
                        : node.getRefTaskDefinitionId());
        if (kind == JobNodeKind.GATEWAY) {
            JobGatewayResponse gateway = new JobGatewayResponse();
            gateway.setMode(node.getGatewayMode());
            response.setGateway(gateway);
        } else {
            response.setPolicy(policyResponse);
        }
        return response;
    }

    private JobEdgeResponse toEdgeResponse(JobDefinitionEdge edge) {
        JobEdgeResponse response = new JobEdgeResponse();
        response.setId(edge.getId());
        response.setFromNodeId(edge.getFromNodeId());
        response.setToNodeId(edge.getToNodeId());
        response.setRouteKey(edge.getRouteKey());
        response.setPriority(edge.getRoutePriority());
        response.setDefaultRoute(Boolean.TRUE.equals(edge.getDefaultRoute()));
        response.setConditionVersion(edge.getConditionVersion());
        response.setCondition(
                DagConditionCodec.decode(edge.getConditionVersion(), edge.getConditionPayload()));
        return response;
    }

    private JobPublicationDTO toDto(JobPublicationResult result) {
        return new JobPublicationDTO(result.jobId(), result.definitionId(), result.revision());
    }

    private void requirePositiveId(Long jobId) {
        if (jobId == null || jobId <= 0) {
            throw new BusinessException(
                    ResultCode.PARAM_ERROR, CoreMessage.JOB_ID_MUST_BE_GREATER_THAN_ZERO);
        }
    }

    private <T> List<T> requireList(List<T> values) {
        if (values == null) {
            throw incompleteDefinition();
        }
        return values;
    }

    private BusinessException incompleteDefinition() {
        return systemError(INCOMPLETE_DEFINITION);
    }

    private BusinessException systemError(MessageDefinition message, Object... arguments) {
        return new BusinessException(ResultCode.SYSTEM_ERROR, message, arguments);
    }

    private record EdgeKey(Long fromNodeId, Long toNodeId) {}
}
