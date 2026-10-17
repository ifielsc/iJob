package com.ifelio.ijob.api.advice;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import cn.dev33.satoken.exception.NotRoleException;

import com.ifelio.ijob.common.exception.BusinessException;
import com.ifelio.ijob.common.exception.StopException;
import com.ifelio.ijob.common.response.ApiResponse;
import com.ifelio.ijob.common.response.ErrorDefinition;
import com.ifelio.ijob.common.response.ErrorDescriptor;
import com.ifelio.ijob.common.response.ResultCode;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private final MessageSource messageSource;

    public GlobalExceptionHandler() {
        this(defaultMessageSource());
    }

    @Autowired
    public GlobalExceptionHandler(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException exception) {
        if (exception.getCause() != null) {
            log.warn(
                    "Business exception caused by an internal failure, code={}, diagnostic={}",
                    exception.getCode(),
                    com.ifelio.ijob.common.diagnostic.SafeDiagnostic.describe(exception));
        }
        ErrorDefinition error =
                new ErrorDescriptor(
                        exception.getCode(),
                        exception.getErrorCode(),
                        exception.getMessageKey(),
                        exception.getMessage());
        String message =
                exception.isCustomMessage()
                        ? exception.getMessage()
                        : resolve(error, exception.getMessageArgs());
        return errorResponse(error, message, exception.getMessageArgs());
    }

    @ExceptionHandler(NotLoginException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotLoginException() {
        return errorResponse(ResultCode.UNAUTHORIZED);
    }

    @ExceptionHandler({NotPermissionException.class, NotRoleException.class})
    public ResponseEntity<ApiResponse<Void>> handleForbiddenException() {
        return errorResponse(ResultCode.FORBIDDEN);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodArgumentNotValidException(
            MethodArgumentNotValidException exception) {
        return validationError(exception.getBindingResult());
    }

    @ExceptionHandler(BindException.class)
    public ResponseEntity<ApiResponse<Void>> handleBindException(BindException exception) {
        return validationError(exception.getBindingResult());
    }

    @ExceptionHandler({
        HttpMessageNotReadableException.class,
        HandlerMethodValidationException.class,
        MethodArgumentTypeMismatchException.class,
        MissingServletRequestParameterException.class
    })
    public ResponseEntity<ApiResponse<Void>> handleRequestBindingException(Exception exception) {
        log.debug("Request parameter binding failed, type={}", exception.getClass().getName());
        return errorResponse(ApiError.REQUEST_BINDING_FAILED);
    }

    @ExceptionHandler(StopException.class)
    public ResponseEntity<ApiResponse<Void>> handleStopException(StopException exception) {
        log.warn(
                "Execution control exception reached the web boundary: {}", exception.getMessage());
        return errorResponse(ApiError.EXECUTION_STOPPED);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpectedException(Exception exception) {
        if (exception instanceof org.springframework.web.ErrorResponse frameworkError) {
            ResultCode code =
                    java.util.Arrays.stream(ResultCode.values())
                            .filter(value -> value.code() == frameworkError.getStatusCode().value())
                            .findFirst()
                            .orElse(ResultCode.PARAM_ERROR);
            ResponseEntity<ApiResponse<Void>> result = errorResponse(code);
            return ResponseEntity.status(frameworkError.getStatusCode())
                    .headers(frameworkError.getHeaders())
                    .body(result.getBody());
        }
        log.error(
                "Unhandled exception reached the web boundary, diagnostic={}",
                com.ifelio.ijob.common.diagnostic.SafeDiagnostic.describe(exception));
        return errorResponse(ResultCode.SYSTEM_ERROR);
    }

    @ExceptionHandler(org.springframework.dao.DuplicateKeyException.class)
    public ResponseEntity<ApiResponse<Void>> handleDuplicateKey() {
        return errorResponse(ResultCode.CONFLICT);
    }

    private ResponseEntity<ApiResponse<Void>> validationError(BindingResult bindingResult) {
        Object[] arguments = firstValidationArguments(bindingResult);
        return errorResponse(
                ApiError.VALIDATION_FAILED,
                resolve(ApiError.VALIDATION_FAILED, arguments),
                arguments);
    }

    private ResponseEntity<ApiResponse<Void>> errorResponse(ErrorDefinition error) {
        return errorResponse(error, resolve(error, new Object[0]), new Object[0]);
    }

    private ResponseEntity<ApiResponse<Void>> errorResponse(
            ErrorDefinition error, String message, Object[] arguments) {
        ApiResponse<Void> body = ApiResponse.fail(error, message, arguments, MDC.get("traceId"));
        return ResponseEntity.status(error.resultCode().code()).body(body);
    }

    private Object[] firstValidationArguments(BindingResult bindingResult) {
        return bindingResult.getAllErrors().stream()
                .findFirst()
                .map(
                        error -> {
                            String message = error.getDefaultMessage();
                            String resolvedMessage =
                                    message == null || message.isBlank()
                                            ? resolve(ResultCode.PARAM_ERROR, new Object[0])
                                            : message;
                            if (error instanceof FieldError fieldError) {
                                return new Object[] {fieldError.getField(), resolvedMessage};
                            }
                            return new Object[] {"request", resolvedMessage};
                        })
                .orElseGet(
                        () ->
                                new Object[] {
                                    "request", resolve(ResultCode.PARAM_ERROR, new Object[0])
                                });
    }

    private String resolve(ErrorDefinition error, Object[] arguments) {
        return messageSource.getMessage(
                error.messageKey(),
                arguments,
                error.defaultMessage(),
                LocaleContextHolder.getLocale());
    }

    private static MessageSource defaultMessageSource() {
        ResourceBundleMessageSource source = new ResourceBundleMessageSource();
        source.setBasename("messages");
        source.setDefaultEncoding("UTF-8");
        source.setFallbackToSystemLocale(false);
        return source;
    }
}
