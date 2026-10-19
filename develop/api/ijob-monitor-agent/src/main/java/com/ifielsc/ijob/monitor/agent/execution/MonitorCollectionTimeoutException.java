// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.monitor.agent.execution;

public class MonitorCollectionTimeoutException extends RuntimeException {

    public MonitorCollectionTimeoutException() {
        super("monitor collection timed out");
    }
}
