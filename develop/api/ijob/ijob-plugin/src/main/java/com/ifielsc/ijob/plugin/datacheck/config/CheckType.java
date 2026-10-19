// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.plugin.datacheck.config;

public enum CheckType {
    /** Compares the query result with the configured expectation. */
    NORMAL,

    COUNT,

    EXISTS,

    EMPTY
}
