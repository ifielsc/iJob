package com.ifelio.ijob.core.job.definition;

import com.ifelio.ijob.common.exception.BusinessException;
import com.ifelio.ijob.common.response.MessageDefinition;
import com.ifelio.ijob.common.response.ResultCode;
import com.ifelio.ijob.contract.enums.ExecuteCondition;
import com.ifelio.ijob.contract.enums.FailStrategy;
import com.ifelio.ijob.contract.enums.JobNodeKind;
import com.ifelio.ijob.contract.enums.TargetType;
import com.ifelio.ijob.contract.job.JobEdgeRequest;
import com.ifelio.ijob.contract.job.JobNodePolicyRequest;
import com.ifelio.ijob.contract.job.JobNodeRequest;
import com.ifelio.ijob.contract.job.JobUpsertRequest;
import com.ifelio.ijob.core.error.CoreMessage;
import com.ifelio.ijob.core.execute.condition.DagConditionCodec;
import com.ifelio.ijob.infra.entity.job.Job;
import com.ifelio.ijob.infra.entity.job.JobDefinition;
import com.ifelio.ijob.infra.entity.job.JobDefinitionEdge;
import com.ifelio.ijob.infra.entity.job.JobDefinitionNode;
import com.ifelio.ijob.infra.entity.job.JobDefinitionNodePolicy;
import com.ifelio.ijob.infra.mapper.job.JobCurrentDefinitionAdvance;
import com.ifelio.ijob.infra.mapper.job.JobDefinitionEdgeMapper;
import com.ifelio.ijob.infra.mapper.job.JobDefinitionGroupMapper;
import com.ifelio.ijob.infra.mapper.job.JobDefinitionMapper;
import com.ifelio.ijob.infra.mapper.job.JobDefinitionNodeMapper;
import com.ifelio.ijob.infra.mapper.job.JobDefinitionNodePolicyMapper;
import com.ifelio.ijob.infra.mapper.job.JobDefinitionReferenceSnapshotMapper;
import com.ifelio.ijob.infra.mapper.job.JobPublicationIdentityRow;
import com.ifelio.ijob.infra.mapper.job.JobPublicationMapper;
import com.ifelio.ijob.infra.mapper.job.JobReferenceRow;
import com.ifelio.ijob.infra.mapper.task.TaskReferenceLockMapper;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

@Service
@RequiredArgsConstructor
public class JobDefinitionPublicationService {
    private static final long INITIAL_REVISION = 1L;

    private final JobDefinitionValidator definitionValidator;
    private final NestedJobReferenceValidator nestedReferenceValidator;
    private final JobPublicationMapper publicationMapper;
    private final JobDefinitionReferenceSnapshotMapper snapshotMapper;
    private final TaskReferenceLockMapper taskReferenceLockMapper;
    private final JobDefinitionMapper definitionMapper;
    private final JobDefinitionGroupMapper definitionGroupMapper;
    private final JobDefinitionNodeMapper nodeMapper;
    private final JobDefinitionNodePolicyMapper policyMapper;
    private final JobDefinitionEdgeMapper edgeMapper;

    @Transactional
    public JobPublicationResult create(JobUpsertRequest request) {
        definitionValidator.validate(request);

        Set<Long> allJobIds = lockPublicationBarrier();
        validateReferences(null, request.getNodes(), allJobIds);

        LocalDateTime now = LocalDateTime.now();
        Job identity = toIdentity(request, now);
        requireInserted(
                publicationMapper.insertIdentity(identity),
                identity.getId(),
                CoreMessage.JOB_IDENTITY_CREATION_FAILED);

        JobDefinition definition =
                writeDefinition(identity.getId(), INITIAL_REVISION, request, now);
        requireAffected(
                publicationMapper.initializeCurrentDefinition(
                        identity.getId(), definition.getId(), now),
                CoreMessage.INITIAL_JOB_DEFINITION_ACTIVATION_FAILED);
        return new JobPublicationResult(
                identity.getId(), definition.getId(), definition.getRevision());
    }

    @Transactional
    public JobPublicationResult publish(
            Long jobId, Long expectedRevision, JobUpsertRequest request) {
        requirePositiveJobId(jobId);
        requirePositiveExpectedRevision(expectedRevision);
        definitionValidator.validate(request);

        Set<Long> allJobIds = lockPublicationBarrier();
        JobPublicationIdentityRow identity = publicationMapper.selectIdentityForUpdate(jobId);
        validateCurrentIdentity(jobId, identity, expectedRevision);
        validateCurrentDefinition(identity);
        long nextRevision = nextRevision(expectedRevision);
        validateReferences(jobId, request.getNodes(), allJobIds);

        LocalDateTime now = LocalDateTime.now();
        JobDefinition definition = writeDefinition(jobId, nextRevision, request, now);
        requireAffected(
                publicationMapper.advanceCurrentDefinition(
                        toAdvance(jobId, identity, definition, request, now)),
                CoreMessage.JOB_CURRENT_DEFINITION_ACTIVATION_FAILED);
        return new JobPublicationResult(jobId, definition.getId(), definition.getRevision());
    }

    private Set<Long> lockPublicationBarrier() {
        List<Long> lockedIds = publicationMapper.lockAllJobIdsForPublication();
        if (lockedIds == null) {
            throw systemError(CoreMessage.JOB_PUBLICATION_BARRIER_RETURNED_NULL);
        }
        Set<Long> result = new LinkedHashSet<>();
        for (Long jobId : lockedIds) {
            if (jobId == null || jobId <= 0 || !result.add(jobId)) {
                throw systemError(CoreMessage.JOB_PUBLICATION_BARRIER_DATA_IS_INCOMPLETE);
            }
        }
        return Collections.unmodifiableSet(result);
    }

    private void validateReferences(
            Long currentJobId, List<JobNodeRequest> nodes, Set<Long> allJobIds) {
        ReferenceIds references = collectReferenceIds(nodes);
        lockTaskReferences(references.taskIds());
        Map<Long, Set<Long>> snapshot = loadCurrentSnapshot(allJobIds);
        if (currentJobId != null && !snapshot.containsKey(currentJobId)) {
            throw systemError(CoreMessage.TARGET_JOB_IS_MISSING_ITS_CURRENT_DEFINITION_SNAPSHOT);
        }
        requireReferencedJobs(references.jobIds(), allJobIds, snapshot.keySet());
        nestedReferenceValidator.validate(currentJobId, nodes, snapshot);
    }

    private ReferenceIds collectReferenceIds(List<JobNodeRequest> nodes) {
        Set<Long> taskIds = new TreeSet<>();
        Set<Long> jobIds = new TreeSet<>();
        for (JobNodeRequest node : nodes) {
            if (node.getKind() == JobNodeKind.GATEWAY) {
                continue;
            }
            if (node.getNodeType() == TargetType.TASK) {
                taskIds.add(node.getRefId());
            } else if (node.getNodeType() == TargetType.JOB) {
                jobIds.add(node.getRefId());
            }
        }
        return new ReferenceIds(List.copyOf(taskIds), Collections.unmodifiableSet(jobIds));
    }

    private void lockTaskReferences(List<Long> requiredIds) {
        if (requiredIds.isEmpty()) {
            return;
        }
        List<Long> lockedIds = taskReferenceLockMapper.lockExistingIds(requiredIds);
        if (lockedIds == null || !requiredIds.equals(lockedIds)) {
            throw new BusinessException(
                    ResultCode.NOT_FOUND,
                    CoreMessage.REFERENCED_TASK_DOES_NOT_EXIST_OR_HAS_CHANGED);
        }
    }

    private Map<Long, Set<Long>> loadCurrentSnapshot(Set<Long> allJobIds) {
        List<Long> currentJobIds = snapshotMapper.selectAllCurrentJobIds();
        List<JobReferenceRow> references = snapshotMapper.selectCurrentJobReferences();
        if (currentJobIds == null || references == null) {
            throw systemError(CoreMessage.CURRENT_DEFINITION_REFERENCE_SNAPSHOT_RETURNED_NULL);
        }

        Map<Long, Set<Long>> result = new LinkedHashMap<>();
        for (Long currentJobId : currentJobIds) {
            if (currentJobId == null
                    || !allJobIds.contains(currentJobId)
                    || result.put(currentJobId, new LinkedHashSet<>()) != null) {
                throw systemError(CoreMessage.CURRENT_JOB_DEFINITION_SNAPSHOT_IS_INCOMPLETE);
            }
        }
        for (JobReferenceRow reference : references) {
            if (reference == null
                    || reference.jobId() == null
                    || reference.referencedJobId() == null) {
                throw systemError(CoreMessage.CURRENT_DEFINITION_REFERENCE_SNAPSHOT_IS_INCOMPLETE);
            }
            Set<Long> children = result.get(reference.jobId());
            if (children == null || !result.containsKey(reference.referencedJobId())) {
                throw systemError(CoreMessage.CURRENT_DEFINITION_REFERENCE_TARGET_IS_INCOMPLETE);
            }
            children.add(reference.referencedJobId());
        }

        Map<Long, Set<Long>> immutable = new LinkedHashMap<>();
        result.forEach(
                (jobId, children) ->
                        immutable.put(
                                jobId, Collections.unmodifiableSet(new LinkedHashSet<>(children))));
        return Collections.unmodifiableMap(immutable);
    }

    private void requireReferencedJobs(
            Set<Long> requiredIds, Set<Long> allJobIds, Set<Long> currentJobIds) {
        for (Long requiredId : requiredIds) {
            if (!allJobIds.contains(requiredId)) {
                throw new BusinessException(
                        ResultCode.NOT_FOUND,
                        CoreMessage.REFERENCED_JOB_DOES_NOT_EXIST_VALUE,
                        requiredId);
            }
            if (!currentJobIds.contains(requiredId)) {
                throw new BusinessException(
                        ResultCode.CONFLICT,
                        CoreMessage.REFERENCED_JOB_HAS_NO_CURRENT_DEFINITION_VALUE,
                        requiredId);
            }
        }
    }

    private void validateCurrentIdentity(
            Long jobId, JobPublicationIdentityRow identity, Long expectedRevision) {
        if (identity == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, CoreMessage.JOB_DOES_NOT_EXIST);
        }
        if (!jobId.equals(identity.getId())) {
            throw systemError(CoreMessage.JOB_IDENTITY_LOCK_RESULT_IS_INCONSISTENT);
        }
        validateRetiredState(identity);
        if (identity.getRetired()) {
            throw new BusinessException(ResultCode.CONFLICT, CoreMessage.JOB_IS_RETIRED);
        }
        Long definitionId = identity.getCurrentDefinitionId();
        Long revision = identity.getCurrentRevision();
        if (definitionId == null && revision == null) {
            throw new BusinessException(
                    ResultCode.CONFLICT, CoreMessage.JOB_HAS_NO_BACKFILLED_CURRENT_DEFINITION);
        }
        if (definitionId == null || revision == null || definitionId <= 0 || revision <= 0) {
            throw systemError(CoreMessage.JOB_CURRENT_POINTER_DATA_IS_INCOMPLETE);
        }
        if (!revision.equals(expectedRevision)) {
            throw new BusinessException(ResultCode.CONFLICT, CoreMessage.JOB_REVISION_HAS_CHANGED);
        }
    }

    private void validateRetiredState(JobPublicationIdentityRow identity) {
        Boolean retired = identity.getRetired();
        LocalDateTime retiredTime = identity.getRetiredTime();
        if (retired == null
                || (retired && retiredTime == null)
                || (!retired && retiredTime != null)) {
            throw systemError(CoreMessage.JOB_RETIRED_STATE_DATA_IS_INCOMPLETE);
        }
    }

    private void validateCurrentDefinition(JobPublicationIdentityRow identity) {
        JobDefinition definition = definitionMapper.selectById(identity.getCurrentDefinitionId());
        if (definition == null
                || !identity.getCurrentDefinitionId().equals(definition.getId())
                || !identity.getId().equals(definition.getJobId())
                || !identity.getCurrentRevision().equals(definition.getRevision())) {
            throw systemError(CoreMessage.JOB_CURRENT_DEFINITION_OWNERSHIP_IS_INCONSISTENT);
        }
    }

    private long nextRevision(Long expectedRevision) {
        try {
            return Math.addExact(expectedRevision, 1L);
        } catch (ArithmeticException exception) {
            throw new BusinessException(
                    ResultCode.CONFLICT, CoreMessage.JOB_REVISION_HAS_REACHED_ITS_LIMIT, exception);
        }
    }

    private JobDefinition writeDefinition(
            Long jobId, long revision, JobUpsertRequest request, LocalDateTime now) {
        JobDefinition definition = toDefinition(jobId, revision, request, now);
        requireInserted(
                definitionMapper.insert(definition),
                definition.getId(),
                CoreMessage.JOB_DEFINITION_CREATION_FAILED);
        for (String group : request.getGroups()) {
            requireAffected(
                    definitionGroupMapper.insert(definition.getId(), group, now),
                    CoreMessage.JOB_DEFINITION_CREATION_FAILED);
        }

        Map<String, Long> nodeIds = new LinkedHashMap<>();
        for (JobNodeRequest requestNode : request.getNodes()) {
            JobDefinitionNode node = toNode(definition.getId(), requestNode, now);
            requireInserted(
                    nodeMapper.insert(node),
                    node.getId(),
                    CoreMessage.JOB_DEFINITION_NODE_CREATION_FAILED);
            nodeIds.put(requestNode.getNodeId(), node.getId());

            JobDefinitionNodePolicy policy =
                    toPolicy(definition.getId(), node.getId(), requestNode.getPolicy(), now);
            requireInserted(
                    policyMapper.insert(policy),
                    policy.getId(),
                    CoreMessage.JOB_DEFINITION_NODE_POLICY_CREATION_FAILED);
        }
        writeEdges(definition.getId(), request.getEdges(), nodeIds, now);
        return definition;
    }

    private void writeEdges(
            Long definitionId,
            List<JobEdgeRequest> requests,
            Map<String, Long> nodeIds,
            LocalDateTime now) {
        if (requests == null) {
            return;
        }
        for (JobEdgeRequest request : requests) {
            JobDefinitionEdge edge = new JobDefinitionEdge();
            edge.setDefinitionId(definitionId);
            edge.setFromNodeId(nodeIds.get(request.getFromNodeId()));
            edge.setToNodeId(nodeIds.get(request.getToNodeId()));
            edge.setRouteKey(request.getRouteKey());
            edge.setRoutePriority(request.getPriority());
            edge.setDefaultRoute(Boolean.TRUE.equals(request.getDefaultRoute()));
            edge.setConditionVersion(
                    request.getCondition() == null ? null : DagConditionCodec.CONDITION_VERSION);
            edge.setConditionPayload(DagConditionCodec.encode(request.getCondition()));
            edge.setCreateTime(now);
            requireInserted(
                    edgeMapper.insert(edge),
                    edge.getId(),
                    CoreMessage.JOB_DEFINITION_EDGE_CREATION_FAILED);
        }
    }

    private Job toIdentity(JobUpsertRequest request, LocalDateTime now) {
        Job identity = new Job();
        identity.setName(request.getName());
        identity.setTimeout(request.getTimeout());
        identity.setEnabled(resolveEnabled(request));
        identity.setDescription(request.getDescription());
        identity.setCreateTime(now);
        identity.setUpdateTime(now);
        return identity;
    }

    private JobDefinition toDefinition(
            Long jobId, long revision, JobUpsertRequest request, LocalDateTime now) {
        JobDefinition definition = new JobDefinition();
        definition.setJobId(jobId);
        definition.setRevision(revision);
        definition.setName(request.getName());
        definition.setTimeout(request.getTimeout());
        definition.setEnabled(resolveEnabled(request));
        definition.setDescription(request.getDescription());
        definition.setLayout(JobLayoutCodec.encode(request.getLayout()));
        definition.setCreateTime(now);
        return definition;
    }

    private JobDefinitionNode toNode(Long definitionId, JobNodeRequest request, LocalDateTime now) {
        JobDefinitionNode node = new JobDefinitionNode();
        node.setDefinitionId(definitionId);
        node.setNodeKey(request.getNodeId());
        node.setName(request.getName());
        node.setNodeKind(request.getKind());
        node.setGatewayMode(request.getGateway() == null ? null : request.getGateway().getMode());
        node.setNodeType(request.getNodeType());
        node.setRefId(request.getRefId());
        if (request.getKind() == JobNodeKind.GATEWAY) {
            node.setCreateTime(now);
            return node;
        } else if (request.getNodeType() == TargetType.JOB) {
            node.setRefDefinitionId(
                    snapshotMapper.selectCurrentJobDefinitionId(request.getRefId()));
            requireSnapshot(node.getRefDefinitionId());
        } else {
            node.setRefTaskDefinitionId(
                    taskReferenceLockMapper.selectCurrentTaskDefinitionId(request.getRefId()));
            requireSnapshot(node.getRefTaskDefinitionId());
        }
        node.setCreateTime(now);
        return node;
    }

    private void requireSnapshot(Long definitionId) {
        if (definitionId == null || definitionId <= 0) {
            throw new BusinessException(
                    ResultCode.CONFLICT,
                    CoreMessage.REFERENCED_TARGET_DEFINITION_SNAPSHOT_IS_MISSING);
        }
    }

    private JobDefinitionNodePolicy toPolicy(
            Long definitionId, Long nodeId, JobNodePolicyRequest request, LocalDateTime now) {
        JobDefinitionNodePolicy policy = new JobDefinitionNodePolicy();
        policy.setDefinitionId(definitionId);
        policy.setNodeId(nodeId);
        policy.setFailStrategy(request == null ? FailStrategy.STOP : request.getFailStrategy());
        policy.setExecuteCondition(
                request == null ? ExecuteCondition.ALWAYS : request.getExecuteCondition());
        policy.setCreateTime(now);
        return policy;
    }

    private JobCurrentDefinitionAdvance toAdvance(
            Long jobId,
            JobPublicationIdentityRow identity,
            JobDefinition definition,
            JobUpsertRequest request,
            LocalDateTime now) {
        JobCurrentDefinitionAdvance advance = new JobCurrentDefinitionAdvance();
        advance.setJobId(jobId);
        advance.setExpectedDefinitionId(identity.getCurrentDefinitionId());
        advance.setExpectedRevision(identity.getCurrentRevision());
        advance.setDefinitionId(definition.getId());
        advance.setNextRevision(definition.getRevision());
        advance.setName(request.getName());
        advance.setTimeout(request.getTimeout());
        advance.setEnabled(resolveEnabled(request));
        advance.setDescription(request.getDescription());
        advance.setUpdateTime(now);
        return advance;
    }

    private boolean resolveEnabled(JobUpsertRequest request) {
        return request.getEnabled() == null
                ? JobDefinitionValidator.DEFAULT_ENABLED
                : request.getEnabled();
    }

    private void requirePositiveJobId(Long jobId) {
        if (jobId == null || jobId <= 0) {
            throw new BusinessException(
                    ResultCode.PARAM_ERROR, CoreMessage.JOB_ID_MUST_BE_GREATER_THAN_ZERO);
        }
    }

    private void requirePositiveExpectedRevision(Long expectedRevision) {
        if (expectedRevision == null || expectedRevision <= 0) {
            throw new BusinessException(
                    ResultCode.PARAM_ERROR,
                    CoreMessage.EXPECTED_REVISION_MUST_BE_GREATER_THAN_ZERO);
        }
    }

    private void requireInserted(int affectedRows, Long generatedId, MessageDefinition message) {
        if (affectedRows != 1 || generatedId == null) {
            throw systemError(message);
        }
    }

    private void requireAffected(int affectedRows, MessageDefinition message) {
        if (affectedRows != 1) {
            throw systemError(message);
        }
    }

    private BusinessException systemError(MessageDefinition message, Object... arguments) {
        return new BusinessException(ResultCode.SYSTEM_ERROR, message, arguments);
    }

    private record ReferenceIds(List<Long> taskIds, Set<Long> jobIds) {}
}
