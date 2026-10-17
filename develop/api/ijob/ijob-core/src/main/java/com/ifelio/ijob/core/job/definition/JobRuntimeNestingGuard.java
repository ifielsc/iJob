package com.ifelio.ijob.core.job.definition;

import com.ifelio.ijob.common.exception.BusinessException;
import com.ifelio.ijob.common.response.ResultCode;
import com.ifelio.ijob.contract.enums.TargetType;
import com.ifelio.ijob.infra.entity.runtime.Instance;
import com.ifelio.ijob.infra.mapper.runtime.InstanceMapper;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class JobRuntimeNestingGuard {
    private final InstanceMapper instanceMapper;

    public void validateInstance(Instance instance) {
        validateChain(instance);
    }

    public void validateChild(Instance parent, Long childJobId) {
        RuntimeChain chain = validateChain(parent);
        if (chain.depth() >= JobNestingPolicy.MAX_DEPTH) {
            throw depthConflict();
        }
        if (childJobId == null || childJobId <= 0) {
            throw systemError();
        }
        if (chain.jobIds().contains(childJobId)) {
            throw BusinessException.localized(
                    ResultCode.CONFLICT, "Nested job runtime cycle detected");
        }
    }

    private RuntimeChain validateChain(Instance leaf) {
        if (leaf == null || leaf.getRootInstanceId() == null || leaf.getRootInstanceId() <= 0) {
            throw systemError();
        }

        Long expectedRootId = leaf.getRootInstanceId();
        Set<Long> visitedInstanceIds = new HashSet<>();
        Set<Long> jobIds = new HashSet<>();
        int depth = 0;
        Instance current = leaf;
        while (true) {
            validateJobInstance(current, expectedRootId);
            if (!visitedInstanceIds.add(current.getId())) {
                throw systemError();
            }
            depth++;
            if (depth > JobNestingPolicy.MAX_DEPTH) {
                throw depthConflict();
            }
            if (!jobIds.add(current.getTargetId())) {
                throw BusinessException.localized(
                        ResultCode.CONFLICT, "Nested job runtime cycle detected");
            }

            Long parentId = current.getParentInstanceId();
            if (parentId == null) {
                if (!current.getId().equals(expectedRootId)) {
                    throw systemError();
                }
                break;
            }
            if (parentId <= 0) {
                throw systemError();
            }
            current = instanceMapper.selectById(parentId);
            if (current == null) {
                throw systemError();
            }
        }
        return new RuntimeChain(depth, Set.copyOf(jobIds));
    }

    private void validateJobInstance(Instance instance, Long expectedRootId) {
        if (instance.getId() == null
                || instance.getId() <= 0
                || instance.getTargetType() != TargetType.JOB
                || instance.getTargetId() == null
                || instance.getTargetId() <= 0
                || instance.getDefinitionId() == null
                || instance.getDefinitionId() <= 0
                || !expectedRootId.equals(instance.getRootInstanceId())) {
            throw systemError();
        }
    }

    private BusinessException depthConflict() {
        return BusinessException.localized(
                ResultCode.CONFLICT,
                "Nested job runtime depth exceeds " + JobNestingPolicy.MAX_DEPTH + " levels");
    }

    private BusinessException systemError() {
        return BusinessException.localized(
                ResultCode.SYSTEM_ERROR, "Job runtime instance ancestry is invalid");
    }

    private record RuntimeChain(int depth, Set<Long> jobIds) {}
}
