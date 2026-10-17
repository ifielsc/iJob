package com.ifelio.ijob.contract.diagnostics;

/** Observed database conditions, not inferred root causes or worker health. */
public enum DiagnosticCategory {
    LEASE_EXPIRED,
    QUEUE_DELAYED,
    ROOT_DEADLINE_EXCEEDED,
    COMMAND_EXPIRED
}
