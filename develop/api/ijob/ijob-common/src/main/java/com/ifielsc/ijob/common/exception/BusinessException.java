// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.common.exception;

import com.ifielsc.ijob.common.response.ErrorDefinition;
import com.ifielsc.ijob.common.response.ErrorDescriptor;
import com.ifielsc.ijob.common.response.MessageDefinition;
import com.ifielsc.ijob.common.response.ResultCode;

import lombok.Getter;

import java.io.Serial;
import java.text.MessageFormat;
import java.util.Locale;
import java.util.Objects;

@Getter
public class BusinessException extends RuntimeException {
    /**
     * Resolve audited call-site text; unknown diagnostics fail closed to the safe status message.
     */
    public static BusinessException localized(ResultCode code, String text) {
        return localized(code, text, null);
    }

    public static BusinessException localized(ResultCode code, String text, Throwable cause) {
        var message = com.ifielsc.ijob.common.response.BusinessMessage.find(text);
        if (message != null)
            return new BusinessException(descriptor(code, message), new Object[0], cause);
        String[][] patterns = {
            {
                "Nested job runtime depth exceeds ",
                " levels",
                "nesting-depth",
                "Nested job runtime depth exceeds {0} levels"
            },
            {
                "service response exceeds maxResponseBytes=",
                "",
                "response-limit",
                "Service response exceeds maxResponseBytes={0}"
            },
            {"", " must be greater than zero", "positive-field", "{0} must be greater than zero"},
            {
                "",
                " must be between 1 and 10000",
                "concurrency-range",
                "{0} must be between 1 and 10000"
            },
            {"", " is invalid", "invalid-field", "{0} is invalid"},
            {"", " snapshot is missing", "missing-snapshot", "{0} snapshot is missing"},
            {"required variable missing:", "", "missing-variable", "required variable missing:{0}"}
        };
        if (text != null)
            for (String[] pattern : patterns) {
                if (text.startsWith(pattern[0]) && text.endsWith(pattern[1])) {
                    String field =
                            text.substring(
                                    pattern[0].length(), text.length() - pattern[1].length());
                    if (field.matches("[A-Za-z0-9_ .-]{1,128}")) {
                        return new BusinessException(
                                new ErrorDescriptor(
                                        code,
                                        "IJOB-DETAIL-" + pattern[2].toUpperCase(Locale.ROOT),
                                        "error.detail." + pattern[2],
                                        pattern[3]),
                                new Object[] {field},
                                cause);
                    }
                }
            }
        return new BusinessException(code, new Object[0], cause);
    }

    @Serial private static final long serialVersionUID = 1L;

    private final ResultCode code;
    private final String errorCode;
    private final String messageKey;
    // Arguments travel through the JSON response, not Java exception serialization.
    private final transient Object[] messageArgs;
    private final boolean customMessage;

    public BusinessException(ResultCode errorCode) {
        this(errorCode, null, null, false, null);
    }

    public BusinessException(ResultCode errorCode, String message) {
        this(errorCode, message, null);
    }

    public BusinessException(ResultCode errorCode, String message, Throwable cause) {
        this(errorCode, message, null, true, cause);
    }

    public BusinessException(ErrorDefinition definition, Object... messageArgs) {
        this(definition, messageArgs, null);
    }

    public BusinessException(ResultCode code, MessageDefinition message, Object... arguments) {
        this(descriptor(code, message), arguments, null);
    }

    public BusinessException(ResultCode code, MessageDefinition message, Throwable cause) {
        this(descriptor(code, message), new Object[0], cause);
    }

    private static ErrorDefinition descriptor(ResultCode code, MessageDefinition message) {
        Objects.requireNonNull(message, "message must not be null");
        return new ErrorDescriptor(
                code, message.errorCode(), message.messageKey(), message.defaultMessage());
    }

    public BusinessException(ErrorDefinition definition, Object[] messageArgs, Throwable cause) {
        super(formatMessage(required(definition), messageArgs), cause);
        ErrorDefinition required = required(definition);
        this.code = required.resultCode();
        this.errorCode = required.errorCode();
        this.messageKey = required.messageKey();
        this.messageArgs = messageArgs == null ? new Object[0] : messageArgs.clone();
        this.customMessage = false;
    }

    private BusinessException(
            ResultCode resultCode,
            String message,
            Object[] messageArgs,
            boolean customMessage,
            Throwable cause) {
        super(resolveMessage(resultCode, message), cause);
        ResultCode required = Objects.requireNonNull(resultCode, "errorCode must not be null");
        this.code = required;
        this.errorCode = required.errorCode();
        this.messageKey = required.messageKey();
        this.messageArgs = messageArgs == null ? new Object[0] : messageArgs.clone();
        this.customMessage = customMessage && message != null && !message.isBlank();
    }

    private static String resolveMessage(ResultCode errorCode, String message) {
        ResultCode requiredCode = Objects.requireNonNull(errorCode, "errorCode must not be null");
        return message == null || message.isBlank() ? requiredCode.message() : message;
    }

    private static ErrorDefinition required(ErrorDefinition definition) {
        return Objects.requireNonNull(definition, "definition must not be null");
    }

    private static String formatMessage(ErrorDefinition definition, Object[] arguments) {
        return new MessageFormat(definition.defaultMessage(), Locale.ENGLISH)
                .format(arguments == null ? new Object[0] : arguments);
    }

    public Object[] getMessageArgs() {
        return messageArgs == null ? new Object[0] : messageArgs.clone();
    }
}
