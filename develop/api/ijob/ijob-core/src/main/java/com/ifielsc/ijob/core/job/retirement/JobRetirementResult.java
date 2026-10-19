// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.core.job.retirement;

import java.time.LocalDateTime;

public record JobRetirementResult(Long jobId, LocalDateTime retiredTime, boolean alreadyRetired) {}
