package com.ifelio.ijob.core.event;

import com.ifelio.ijob.contract.enums.EventWaitStatus;

public record EventWaitRegistrationResult(EventWaitStatus status, Long matchedEventId) {}
