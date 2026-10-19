package com.ifielsc.ijob.common.response;

import lombok.Getter;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

@Getter
public class ApiResponse<T> {
    private final boolean success;
    private final Integer code;
    private final String errorCode;
    private final String messageKey;
    private final List<Object> messageArgs;
    private final String message;
    private final String traceId;
    private final T data;

    private ApiResponse(
            boolean success,
            ErrorDefinition error,
            String message,
            Object[] messageArgs,
            String traceId,
            T data) {
        this.success = success;
        ErrorDefinition required = Objects.requireNonNull(error, "error must not be null");
        this.code = required.resultCode().code();
        this.errorCode = required.errorCode();
        this.messageKey = required.messageKey();
        this.messageArgs =
                messageArgs == null
                        ? List.of()
                        : Collections.unmodifiableList(Arrays.asList(messageArgs.clone()));
        this.message = message;
        this.traceId = traceId;
        this.data = data;
    }

    public static ApiResponse<Void> success() {
        return success(null);
    }

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(
                true, ResultCode.SUCCESS, ResultCode.SUCCESS.message(), null, null, data);
    }

    public static ApiResponse<Void> fail(ResultCode code) {
        return fail(code, null);
    }

    public static <T> ApiResponse<T> fail(ResultCode code, String message) {
        ResultCode requiredCode = Objects.requireNonNull(code, "code must not be null");
        String responseMessage =
                message == null || message.isBlank() ? requiredCode.message() : message;
        return new ApiResponse<>(false, requiredCode, responseMessage, null, null, null);
    }

    public static <T> ApiResponse<T> fail(
            ErrorDefinition error, String message, Object[] messageArgs, String traceId) {
        ErrorDefinition required = Objects.requireNonNull(error, "error must not be null");
        String responseMessage =
                message == null || message.isBlank() ? required.defaultMessage() : message;
        return new ApiResponse<>(false, required, responseMessage, messageArgs, traceId, null);
    }
}
