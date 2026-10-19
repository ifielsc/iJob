// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.core.event;

import com.ifielsc.ijob.contract.enums.EventWaitStatus;

public record EventWaitRegistrationResult(EventWaitStatus status, Long matchedEventId) {}
