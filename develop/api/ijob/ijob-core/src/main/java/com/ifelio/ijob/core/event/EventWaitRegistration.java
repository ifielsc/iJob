package com.ifelio.ijob.core.event;

import com.ifelio.ijob.contract.enums.EventWaitType;

import java.util.List;

public record EventWaitRegistration(
        EventWaitType waitType,
        String eventCode,
        String businessKey,
        String correlationKey,
        Integer timeoutSeconds,
        Integer lookbackSeconds,
        List<String> allowedOperatorIds,
        Long preferredEventId) {
    public EventWaitRegistration(
            EventWaitType waitType,
            String eventCode,
            String businessKey,
            String correlationKey,
            Integer timeoutSeconds,
            Integer lookbackSeconds,
            List<String> allowedOperatorIds) {
        this(
                waitType,
                eventCode,
                businessKey,
                correlationKey,
                timeoutSeconds,
                lookbackSeconds,
                allowedOperatorIds,
                null);
    }
}
