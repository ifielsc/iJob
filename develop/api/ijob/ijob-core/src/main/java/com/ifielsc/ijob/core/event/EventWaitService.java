package com.ifielsc.ijob.core.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ifielsc.ijob.common.exception.BusinessException;
import com.ifielsc.ijob.common.response.ResultCode;
import com.ifielsc.ijob.contract.enums.EventWaitStatus;
import com.ifielsc.ijob.contract.enums.EventWaitType;
import com.ifielsc.ijob.contract.enums.InstanceStatus;
import com.ifielsc.ijob.core.instance.InstanceService;
import com.ifielsc.ijob.infra.entity.runtime.EventRecord;
import com.ifielsc.ijob.infra.entity.runtime.EventWait;
import com.ifielsc.ijob.infra.entity.runtime.Instance;
import com.ifielsc.ijob.infra.mapper.runtime.EventRecordMapper;
import com.ifielsc.ijob.infra.mapper.runtime.EventWaitMapper;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class EventWaitService {
    private final EventWaitMapper eventWaitMapper;
    private final EventRecordMapper eventRecordMapper;
    private final InstanceService instanceService;
    private final ObjectMapper objectMapper;

    @Transactional
    public EventWaitRegistrationResult register(
            Long instanceId, EventWaitRegistration registration) {
        Instance instance = instanceService.getInstance(instanceId);
        if (instance == null) {
            throw BusinessException.localized(ResultCode.NOT_FOUND, "Instance not found");
        }
        int attempt = instance.getRetryCount() == null ? 0 : instance.getRetryCount();
        EventWait existing = eventWaitMapper.selectByInstanceAndAttempt(instanceId, attempt);
        if (existing != null) {
            return result(existing);
        }
        if (instance.getStatus() != InstanceStatus.RUNNING) {
            throw BusinessException.localized(ResultCode.CONFLICT, "Instance is not running");
        }

        LocalDateTime now = LocalDateTime.now();
        int lookback = registration.lookbackSeconds() == null ? 0 : registration.lookbackSeconds();
        LocalDateTime acceptAfter = now.minusSeconds(lookback);
        String businessKey = trimToNull(registration.businessKey());
        String correlationKey = trimToNull(registration.correlationKey());
        EventRecord early =
                registration.waitType() == EventWaitType.EVENT
                        ? preferredEvent(registration, businessKey, correlationKey)
                        : null;
        if (early == null && registration.waitType() == EventWaitType.EVENT) {
            early =
                    eventRecordMapper.selectFirstMatching(
                            registration.eventCode(), businessKey, correlationKey, acceptAfter);
        }

        EventWait wait = new EventWait();
        List<String> operators = operators(registration);
        wait.setInstanceId(instanceId);
        wait.setRootInstanceId(instance.getRootInstanceId());
        wait.setAttemptNo(attempt);
        wait.setWaitType(registration.waitType());
        wait.setStatus(early == null ? EventWaitStatus.WAITING : EventWaitStatus.MATCHED);
        wait.setEventCode(registration.eventCode());
        wait.setBusinessKey(businessKey);
        wait.setCorrelationKey(correlationKey);
        wait.setAcceptAfter(acceptAfter);
        wait.setTimeoutAt(
                registration.timeoutSeconds() == null
                        ? null
                        : now.plusSeconds(registration.timeoutSeconds()));
        wait.setMatchedEventId(early == null ? null : early.getId());
        wait.setAllowedOperatorIds(writeOperators(operators));
        wait.setResolvedAt(early == null ? null : now);
        if (eventWaitMapper.insert(wait) != 1) {
            throw BusinessException.localized(
                    ResultCode.SYSTEM_ERROR, "Event wait creation failed");
        }
        for (String operator : operators) {
            if (eventWaitMapper.insertOperator(wait.getId(), operator) != 1) {
                throw BusinessException.localized(
                        ResultCode.SYSTEM_ERROR, "Approval operator snapshot creation failed");
            }
        }
        if (early != null) {
            return result(wait);
        }
        instanceService.waitingEvent(instanceId);
        return result(wait);
    }

    private EventRecord preferredEvent(
            EventWaitRegistration registration, String businessKey, String correlationKey) {
        if (registration.preferredEventId() == null) {
            return null;
        }
        EventRecord event = eventRecordMapper.selectById(registration.preferredEventId());
        if (event == null
                || !Objects.equals(event.getEventCode(), registration.eventCode())
                || businessKey != null && !Objects.equals(event.getBusinessKey(), businessKey)
                || correlationKey != null
                        && !Objects.equals(event.getCorrelationKey(), correlationKey)) {
            return null;
        }
        return event;
    }

    private EventWaitRegistrationResult result(EventWait wait) {
        return new EventWaitRegistrationResult(wait.getStatus(), wait.getMatchedEventId());
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String writeOperators(List<String> operators) {
        if (operators.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(operators);
        } catch (JsonProcessingException exception) {
            throw BusinessException.localized(
                    ResultCode.PARAM_ERROR, "Allowed operators are invalid", exception);
        }
    }

    private List<String> operators(EventWaitRegistration registration) {
        List<String> configured = registration.allowedOperatorIds();
        if (registration.waitType() != EventWaitType.APPROVAL) {
            return List.of();
        }
        if (configured == null
                || configured.isEmpty()
                || configured.stream()
                        .anyMatch(
                                operator ->
                                        operator == null
                                                || operator.isBlank()
                                                || operator.length() > 128)) {
            throw BusinessException.localized(
                    ResultCode.PARAM_ERROR, "Allowed operators are invalid");
        }
        return List.copyOf(new LinkedHashSet<>(configured));
    }
}
