package com.ifelio.ijob.infra.coordination.work;

public class ExecutionOwnershipLostException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public ExecutionOwnershipLostException(Long triggerId) {
        super("Execution ownership is unavailable for trigger " + triggerId);
    }
}
