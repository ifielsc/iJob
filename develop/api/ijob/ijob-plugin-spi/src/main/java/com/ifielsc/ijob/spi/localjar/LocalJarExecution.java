// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.spi.localjar;

/**
 * Immutable execution input. The configuration is the userConfig JSON from the task definition
 * snapshot.
 */
public record LocalJarExecution(String taskName, String configuration) {}
