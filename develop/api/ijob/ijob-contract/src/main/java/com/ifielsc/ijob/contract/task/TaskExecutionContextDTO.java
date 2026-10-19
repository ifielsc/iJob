// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.contract.task;

/** Safe confirmation metadata; never includes script contents or credentials. */
public record TaskExecutionContextDTO(
        String pluginType,
        String executionMode,
        String engine,
        String sshResourceId,
        String resourceName,
        String revision,
        String serviceResourceId,
        String protocol,
        String method) {}
