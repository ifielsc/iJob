// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.common.observability;

import java.time.Duration;

/**
 * Stable scanner observation boundary. Core scanners do not depend directly on a concrete metrics
 * backend.
 */
public interface ScannerObserver {
    ScannerObserver NOOP = new ScannerObserver() {};

    default void recordBatch(String scanner, String queue, int batchSize, int batchLimit) {}

    default void recordItem(String scanner, String queue, boolean success) {}

    default void recordRun(String scanner, boolean success, Duration duration) {}
}
