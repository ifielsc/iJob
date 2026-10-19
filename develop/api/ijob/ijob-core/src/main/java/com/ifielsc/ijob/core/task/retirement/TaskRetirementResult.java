// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.core.task.retirement;

import java.time.LocalDateTime;

public record TaskRetirementResult(
        Long taskId, LocalDateTime retiredTime, boolean alreadyRetired) {}
