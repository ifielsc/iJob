package com.ifielsc.ijob.core.auth;

/** Command-local acceptance facts. Never propagated to background workers. */
public final class AuditOutcome implements AutoCloseable {
    private static final ThreadLocal<AuditOutcome> CURRENT = new ThreadLocal<>();
    private boolean replayed;

    private AuditOutcome() {}

    public static AuditOutcome open() {
        AuditOutcome outcome = new AuditOutcome();
        CURRENT.set(outcome);
        return outcome;
    }

    public static void accepted(boolean replayed) {
        AuditOutcome outcome = CURRENT.get();
        if (outcome != null) outcome.replayed = replayed;
    }

    public boolean replayed() {
        return replayed;
    }

    @Override
    public void close() {
        CURRENT.remove();
    }
}
