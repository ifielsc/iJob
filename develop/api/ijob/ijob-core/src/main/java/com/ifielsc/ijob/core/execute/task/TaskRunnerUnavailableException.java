// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.core.execute.task;

public class TaskRunnerUnavailableException extends IllegalArgumentException {
    private static final long serialVersionUID = 1L;

    public TaskRunnerUnavailableException(String message) {
        super(message);
    }
}
