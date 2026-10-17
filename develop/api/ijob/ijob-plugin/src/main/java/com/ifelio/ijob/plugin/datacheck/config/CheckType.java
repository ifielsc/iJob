package com.ifelio.ijob.plugin.datacheck.config;

public enum CheckType {
    /** Compares the query result with the configured expectation. */
    NORMAL,

    COUNT,

    EXISTS,

    EMPTY
}
