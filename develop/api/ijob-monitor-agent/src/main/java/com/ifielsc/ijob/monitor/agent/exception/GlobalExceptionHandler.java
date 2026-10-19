// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.monitor.agent.exception;

import com.ifielsc.ijob.monitor.agent.execution.MonitorCollectionBusyException;
import com.ifielsc.ijob.monitor.agent.execution.MonitorCollectionTimeoutException;

import lombok.extern.slf4j.Slf4j;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler({
        IllegalArgumentException.class,
        org.springframework.http.converter.HttpMessageNotReadableException.class
    })
    public ResponseEntity<ErrorResponse> invalidRequest(Exception exception) {
        log.warn("Rejected invalid monitor request, type={}", exception.getClass().getName());
        return response(HttpStatus.BAD_REQUEST, "Invalid monitor request");
    }

    @ExceptionHandler(MonitorCollectionTimeoutException.class)
    public ResponseEntity<ErrorResponse> timeout(MonitorCollectionTimeoutException exception) {
        log.warn("Monitor collection timed out");
        return response(HttpStatus.GATEWAY_TIMEOUT, "Monitor collection timed out");
    }

    @ExceptionHandler(MonitorCollectionBusyException.class)
    public ResponseEntity<ErrorResponse> busy(MonitorCollectionBusyException exception) {
        log.warn("Monitor collection capacity exhausted");
        return response(HttpStatus.SERVICE_UNAVAILABLE, "Monitor collection is busy");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> error(Exception exception) {
        if (exception instanceof org.springframework.web.ErrorResponse frameworkError) {
            int status = frameworkError.getStatusCode().value();
            return ResponseEntity.status(status)
                    .headers(frameworkError.getHeaders())
                    .body(
                            new ErrorResponse(
                                    "IJOB-AGENT-HTTP-" + status, "Monitor request rejected"));
        }
        log.error(
                "Unexpected monitor request failure, diagnostic={}",
                com.ifielsc.ijob.common.diagnostic.SafeDiagnostic.describe(exception));
        return response(HttpStatus.INTERNAL_SERVER_ERROR, "Monitor collection failed");
    }

    private ResponseEntity<ErrorResponse> response(HttpStatus status, String message) {
        return ResponseEntity.status(status)
                .body(new ErrorResponse("IJOB-AGENT-HTTP-" + status.value(), message));
    }

    public record ErrorResponse(String errorCode, String message) {}
}
