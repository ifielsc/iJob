package com.ifielsc.ijob.core.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.pagehelper.PageHelper;
import com.ifielsc.ijob.common.exception.BusinessException;
import com.ifielsc.ijob.common.response.PageResult;
import com.ifielsc.ijob.common.response.ResultCode;
import com.ifielsc.ijob.contract.enums.EventWaitType;
import com.ifielsc.ijob.contract.event.EventWaitDTO;
import com.ifielsc.ijob.contract.event.EventWaitPageQuery;
import com.ifielsc.ijob.core.query.PageResults;
import com.ifielsc.ijob.infra.entity.runtime.EventWait;
import com.ifielsc.ijob.infra.mapper.runtime.EventWaitMapper;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class EventWaitQueryService {
    private final EventWaitMapper eventWaitMapper;
    private final ObjectMapper objectMapper;

    public EventWaitDTO get(Long id, String operatorId) {
        EventWait wait = eventWaitMapper.selectById(id);
        if (wait == null) {
            throw BusinessException.localized(ResultCode.NOT_FOUND, "Event wait not found");
        }
        requireApprovalAccess(wait, operatorId);
        return toDto(wait);
    }

    public EventWaitDTO getByInstance(Long instanceId, String operatorId) {
        if (instanceId == null || instanceId <= 0) {
            throw BusinessException.localized(ResultCode.PARAM_ERROR, "Instance ID is invalid");
        }
        EventWait wait = eventWaitMapper.selectLatestByInstance(instanceId);
        if (wait == null) {
            throw BusinessException.localized(ResultCode.NOT_FOUND, "Event wait not found");
        }
        requireApprovalAccess(wait, operatorId);
        return toDto(wait);
    }

    public PageResult<EventWaitDTO> queryApprovals(EventWaitPageQuery query, String operatorId) {
        requireOperator(operatorId);
        EventWaitPageQuery resolved = query == null ? new EventWaitPageQuery() : query;
        if (resolved.getTaskName() != null && resolved.getTaskName().length() > 128) {
            throw BusinessException.localized(
                    ResultCode.PARAM_ERROR, "Task name must not exceed 128 characters");
        }
        validateOptionalId(resolved.getInstanceId(), "Instance ID");
        validateOptionalId(resolved.getRootInstanceId(), "Root instance ID");
        PageHelper.startPage(resolved.getPageNo(), resolved.getPageSize());
        try {
            return PageResults.from(eventWaitMapper.selectApprovalList(resolved, operatorId));
        } finally {
            PageHelper.clearPage();
        }
    }

    public void requireApprovalAccess(EventWait wait, String operatorId) {
        if (wait.getWaitType() != EventWaitType.APPROVAL) {
            return;
        }
        requireOperator(operatorId);
        if (wait.getAllowedOperatorIds() == null || wait.getAllowedOperatorIds().isBlank()) {
            throw BusinessException.localized(
                    ResultCode.SYSTEM_ERROR, "Approval operator snapshot is missing");
        }
        try {
            List<String> legacyOperators =
                    objectMapper.readValue(wait.getAllowedOperatorIds(), new TypeReference<>() {});
            Set<String> legacySet = validatedOperators(legacyOperators);
            Set<String> relationSet =
                    validatedOperators(eventWaitMapper.selectOperatorIds(wait.getId()));
            if (!legacySet.equals(relationSet)) {
                throw BusinessException.localized(
                        ResultCode.SYSTEM_ERROR, "Approval operator snapshots are inconsistent");
            }
            if (!relationSet.contains(operatorId)) {
                throw BusinessException.localized(
                        ResultCode.FORBIDDEN, "Operator is not allowed to access this approval");
            }
        } catch (JsonProcessingException exception) {
            throw BusinessException.localized(
                    ResultCode.SYSTEM_ERROR, "Approval operator snapshot is invalid", exception);
        }
    }

    private Set<String> validatedOperators(List<String> operators) {
        if (operators == null
                || operators.isEmpty()
                || operators.stream()
                        .anyMatch(
                                operator ->
                                        operator == null
                                                || operator.isBlank()
                                                || operator.length() > 128)) {
            throw BusinessException.localized(
                    ResultCode.SYSTEM_ERROR, "Approval operator snapshot is invalid");
        }
        return new LinkedHashSet<>(operators);
    }

    public EventWaitDTO toDto(EventWait wait) {
        EventWaitDTO dto = new EventWaitDTO();
        EventWaitDTO context = eventWaitMapper.selectContext(wait.getId());
        if (context != null) {
            dto.setTaskName(context.getTaskName());
            dto.setRootTargetName(context.getRootTargetName());
            dto.setTaskId(context.getTaskId());
            dto.setTaskDefinitionId(context.getTaskDefinitionId());
        }
        dto.setAttemptNo(wait.getAttemptNo());
        dto.setId(wait.getId());
        dto.setInstanceId(wait.getInstanceId());
        dto.setRootInstanceId(wait.getRootInstanceId());
        dto.setWaitType(wait.getWaitType());
        dto.setStatus(wait.getStatus());
        dto.setEventCode(wait.getEventCode());
        dto.setBusinessKey(wait.getBusinessKey());
        dto.setCorrelationKey(wait.getCorrelationKey());
        dto.setAcceptAfter(wait.getAcceptAfter());
        dto.setTimeoutAt(wait.getTimeoutAt());
        dto.setMatchedEventId(wait.getMatchedEventId());
        dto.setDecisionBy(wait.getDecisionBy());
        dto.setDecisionComment(wait.getDecisionComment());
        dto.setResolvedAt(wait.getResolvedAt());
        dto.setCreateTime(wait.getCreateTime());
        dto.setUpdateTime(wait.getUpdateTime());
        return dto;
    }

    private void requireOperator(String operatorId) {
        if (operatorId == null || operatorId.isBlank()) {
            throw BusinessException.localized(ResultCode.UNAUTHORIZED, "Operator is required");
        }
    }

    private void validateOptionalId(Long id, String field) {
        if (id != null && id <= 0) {
            throw BusinessException.localized(ResultCode.PARAM_ERROR, field + " is invalid");
        }
    }
}
