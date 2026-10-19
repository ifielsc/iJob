package com.ifielsc.ijob.core.execute.task;

import com.ifielsc.ijob.common.response.MessageDefinition;
import com.ifielsc.ijob.contract.enums.InstanceStatus;
import com.ifielsc.ijob.core.runtime.RuntimeErrorMetadata;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.text.MessageFormat;
import java.util.Locale;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskResult {
    public static TaskResult failure(Exception failure, MessageDefinition fallback) {
        if (failure instanceof com.ifielsc.ijob.common.exception.BusinessException business
                && !business.isCustomMessage()) {
            return TaskResult.builder()
                    .status(InstanceStatus.FAILED)
                    .message(business.getMessage())
                    .messageMetadata(RuntimeErrorMetadata.from(business))
                    .build();
        }
        var known = com.ifielsc.ijob.common.response.BusinessMessage.find(failure.getMessage());
        return known == null ? fail(fallback) : fail(known);
    }

    private InstanceStatus status;
    private String message;
    private boolean retryable;
    private RuntimeErrorMetadata messageMetadata;

    public static TaskResult success(MessageDefinition message, Object... args) {
        return described(InstanceStatus.SUCCESS, false, message, args);
    }

    public static TaskResult fail(MessageDefinition message, Object... args) {
        return described(InstanceStatus.FAILED, false, message, args);
    }

    public static TaskResult retryableFail(MessageDefinition message, Object... args) {
        return described(InstanceStatus.FAILED, true, message, args);
    }

    public static TaskResult timeout(MessageDefinition message, Object... args) {
        return described(InstanceStatus.TIMED_OUT, false, message, args);
    }

    public static TaskResult stop(MessageDefinition message, Object... args) {
        return described(InstanceStatus.STOPPED, false, message, args);
    }

    public static TaskResult waitingEvent(MessageDefinition message, Object... args) {
        return described(InstanceStatus.WAITING_EVENT, false, message, args);
    }

    private static TaskResult described(
            InstanceStatus status, boolean retryable, MessageDefinition message, Object[] args) {
        return TaskResult.builder()
                .status(status)
                .retryable(retryable)
                .message(new MessageFormat(message.defaultMessage(), Locale.ENGLISH).format(args))
                .messageMetadata(RuntimeErrorMetadata.from(message, args))
                .build();
    }

    public static TaskResult success() {
        return TaskResult.builder().status(InstanceStatus.SUCCESS).build();
    }

    public static TaskResult success(String msg) {
        return TaskResult.builder().status(InstanceStatus.SUCCESS).message(msg).build();
    }

    public static TaskResult fail(String msg) {
        return TaskResult.builder().status(InstanceStatus.FAILED).message(msg).build();
    }

    public static TaskResult stop() {
        return TaskResult.builder().status(InstanceStatus.STOPPED).build();
    }

    public static TaskResult stop(String msg) {
        return TaskResult.builder().status(InstanceStatus.STOPPED).message(msg).build();
    }

    public static TaskResult retryableFail(String msg) {
        return TaskResult.builder()
                .status(InstanceStatus.FAILED)
                .message(msg)
                .retryable(true)
                .build();
    }

    public static TaskResult timeout(String msg) {
        return TaskResult.builder().status(InstanceStatus.TIMED_OUT).message(msg).build();
    }

    public static TaskResult waitingEvent(String msg) {
        return TaskResult.builder().status(InstanceStatus.WAITING_EVENT).message(msg).build();
    }

    public boolean isSuccess() {
        return status == InstanceStatus.SUCCESS;
    }

    public boolean isRetryableFailure() {
        return status == InstanceStatus.FAILED && retryable;
    }
}
