// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.core.job.definition;

public record JobAdmissionSnapshot(
        Long jobId, Long jobDefinitionId, Long revision, String name, Integer timeout) {}
