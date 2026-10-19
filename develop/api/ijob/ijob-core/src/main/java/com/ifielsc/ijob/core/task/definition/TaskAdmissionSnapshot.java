// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.core.task.definition;

public record TaskAdmissionSnapshot(
        Long taskId, Long taskDefinitionId, Long revision, TaskExecutionSpec executionSpec) {}
