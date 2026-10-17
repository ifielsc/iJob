package com.ifelio.ijob.core.runtime;

import cn.hutool.json.JSONUtil;

import com.ifelio.ijob.common.response.MessageDefinition;
import com.ifelio.ijob.contract.enums.InstanceStatus;
import com.ifelio.ijob.contract.enums.TriggerStatus;

public record RuntimeErrorMetadata(String errorCode, String messageKey, String messageArgs) {
    private static final RuntimeErrorMetadata NONE = new RuntimeErrorMetadata(null, null, null);

    public static RuntimeErrorMetadata from(
            com.ifelio.ijob.common.exception.BusinessException exception) {
        return new RuntimeErrorMetadata(
                exception.getErrorCode(),
                exception.getMessageKey(),
                JSONUtil.toJsonStr(exception.getMessageArgs()));
    }

    public static RuntimeErrorMetadata from(MessageDefinition message, Object[] args) {
        return new RuntimeErrorMetadata(
                message.errorCode(),
                message.messageKey(),
                JSONUtil.toJsonStr(args == null ? new Object[0] : args));
    }

    public static RuntimeErrorMetadata forStatus(InstanceStatus status) {
        if (status == null) {
            return NONE;
        }
        return switch (status) {
            case FAILED -> failed();
            case TIMED_OUT -> timedOut();
            case STOPPED -> stopped();
            case SKIPPED -> skipped();
            default -> NONE;
        };
    }

    public static RuntimeErrorMetadata forStatus(TriggerStatus status) {
        if (status == null) {
            return NONE;
        }
        return switch (status) {
            case FAILED -> failed();
            case TIMED_OUT -> timedOut();
            case STOPPED -> stopped();
            case SKIPPED -> skipped();
            default -> NONE;
        };
    }

    private static RuntimeErrorMetadata failed() {
        return new RuntimeErrorMetadata("IJOB-RUNTIME-0500", "runtime.execution.failed", null);
    }

    private static RuntimeErrorMetadata timedOut() {
        return new RuntimeErrorMetadata("IJOB-RUNTIME-0504", "runtime.execution.timed-out", null);
    }

    private static RuntimeErrorMetadata stopped() {
        return new RuntimeErrorMetadata("IJOB-RUNTIME-0409", "runtime.execution.stopped", null);
    }

    private static RuntimeErrorMetadata skipped() {
        return new RuntimeErrorMetadata("IJOB-RUNTIME-0001", "runtime.execution.skipped", null);
    }

    public static RuntimeErrorMetadata branchNotSelected() {
        return new RuntimeErrorMetadata(
                "IJOB-DAG-0001", "runtime.execution.branch-not-selected", null);
    }

    public static RuntimeErrorMetadata permitWaitTimeout() {
        return new RuntimeErrorMetadata(
                "IJOB-CONCURRENCY-0504", "runtime.concurrency.permit-wait-timeout", null);
    }
}
