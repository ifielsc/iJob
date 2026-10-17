package com.ifelio.ijob.core.job.definition;

import com.ifelio.ijob.common.exception.BusinessException;
import com.ifelio.ijob.common.response.MessageDefinition;
import com.ifelio.ijob.common.response.ResultCode;
import com.ifelio.ijob.contract.enums.TargetType;
import com.ifelio.ijob.contract.job.JobNodeRequest;
import com.ifelio.ijob.core.error.CoreMessage;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class NestedJobReferenceValidator {
    public static final int MAX_NESTING_DEPTH = JobNestingPolicy.MAX_DEPTH;
    private static final long CREATE_ROOT_ID = 0L;

    public void validate(
            Long currentJobId,
            List<JobNodeRequest> proposedNodes,
            Map<Long, Set<Long>> referenceSnapshot) {
        if (currentJobId != null && currentJobId <= 0) {
            throw parameterError(CoreMessage.CURRENT_JOB_ID_MUST_BE_GREATER_THAN_ZERO);
        }
        if (proposedNodes == null) {
            throw parameterError(CoreMessage.JOB_NODE_DEFINITIONS_IS_REQUIRED);
        }
        validateSnapshot(referenceSnapshot);

        Set<Long> proposedChildJobIds = collectChildJobIds(currentJobId, proposedNodes);
        long rootJobId = currentJobId == null ? CREATE_ROOT_ID : currentJobId;
        Map<Long, Set<Long>> mergedReferences = copySnapshot(referenceSnapshot);
        mergedReferences.put(rootJobId, proposedChildJobIds);

        validateReachableReferences(
                rootJobId, mergedReferences, 1, new HashSet<>(), new HashMap<>());
    }

    private void validateSnapshot(Map<Long, Set<Long>> referenceSnapshot) {
        if (referenceSnapshot == null) {
            throw parameterError(CoreMessage.NESTED_JOB_REFERENCE_SNAPSHOT_IS_REQUIRED);
        }
        for (Map.Entry<Long, Set<Long>> entry : referenceSnapshot.entrySet()) {
            requirePositiveReferenceId(entry.getKey());
            if (entry.getValue() == null) {
                throw parameterError(
                        CoreMessage
                                .NESTED_JOB_REFERENCE_SNAPSHOTS_MUST_NOT_CONTAIN_NULL_REFERENCE_SETS);
            }
            for (Long childJobId : entry.getValue()) {
                requirePositiveReferenceId(childJobId);
            }
        }
    }

    private Set<Long> collectChildJobIds(Long currentJobId, List<JobNodeRequest> proposedNodes) {
        Set<Long> childJobIds = new LinkedHashSet<>();
        for (JobNodeRequest node : proposedNodes) {
            if (node == null) {
                throw parameterError(CoreMessage.JOB_NODE_IS_REQUIRED);
            }
            if (node.getNodeType() != TargetType.JOB) {
                continue;
            }

            Long childJobId = node.getRefId();
            requirePositiveReferenceId(childJobId);
            if (currentJobId != null && currentJobId.equals(childJobId)) {
                throw parameterError(CoreMessage.A_JOB_MUST_NOT_REFERENCE_ITSELF);
            }
            childJobIds.add(childJobId);
        }
        return childJobIds;
    }

    private Map<Long, Set<Long>> copySnapshot(Map<Long, Set<Long>> referenceSnapshot) {
        Map<Long, Set<Long>> result = new HashMap<>();
        referenceSnapshot.forEach(
                (jobId, childJobIds) -> result.put(jobId, new LinkedHashSet<>(childJobIds)));
        return result;
    }

    private void validateReachableReferences(
            Long jobId,
            Map<Long, Set<Long>> references,
            int depth,
            Set<Long> visiting,
            Map<Long, Integer> maximumVisitedDepth) {
        if (depth > MAX_NESTING_DEPTH) {
            throw parameterError(
                    CoreMessage.NESTED_JOB_DEPTH_MUST_NOT_EXCEED_VALUE, MAX_NESTING_DEPTH);
        }
        if (!visiting.add(jobId)) {
            throw parameterError(CoreMessage.NESTED_JOB_REFERENCES_MUST_NOT_CONTAIN_CYCLES);
        }

        Integer visitedDepth = maximumVisitedDepth.get(jobId);
        if (visitedDepth != null && visitedDepth >= depth) {
            visiting.remove(jobId);
            return;
        }
        maximumVisitedDepth.put(jobId, depth);

        Set<Long> childJobIds = references.get(jobId);
        if (childJobIds == null) {
            throw parameterError(CoreMessage.NESTED_JOB_REFERENCE_SNAPSHOT_IS_INCOMPLETE);
        }
        for (Long childJobId : childJobIds) {
            validateReachableReferences(
                    childJobId, references, depth + 1, visiting, maximumVisitedDepth);
        }
        visiting.remove(jobId);
    }

    private void requirePositiveReferenceId(Long jobId) {
        if (jobId == null || jobId <= 0) {
            throw parameterError(CoreMessage.JOB_REFERENCE_ID_MUST_BE_GREATER_THAN_ZERO);
        }
    }

    private BusinessException parameterError(MessageDefinition message, Object... arguments) {
        return new BusinessException(ResultCode.PARAM_ERROR, message, arguments);
    }
}
