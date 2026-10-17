package com.ifelio.ijob.core.execute;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.io.InterruptedIOException;
import java.net.SocketTimeoutException;
import java.sql.SQLTimeoutException;
import java.util.concurrent.TimeoutException;

class TimeoutExceptionClassifierTest {
    @Test
    void shouldDistinguishOkHttpCallDeadlineFromCancellationAndOrdinaryInterruption() {
        assertTrue(
                TimeoutExceptionClassifier.isTimeout(
                        new IllegalStateException(new InterruptedIOException("timeout"))));
        assertFalse(
                TimeoutExceptionClassifier.isTimeout(new InterruptedIOException("interrupted")));
        assertFalse(TimeoutExceptionClassifier.isTimeout(new java.io.IOException("Canceled")));
        assertFalse(TimeoutExceptionClassifier.isTimeout(new IllegalStateException("timeout")));
    }

    @Test
    void shouldRecognizeSupportedTimeoutsThroughCauseChain() {
        assertTrue(
                TimeoutExceptionClassifier.isTimeout(
                        new IllegalStateException(new SQLTimeoutException())));
        assertTrue(
                TimeoutExceptionClassifier.isTimeout(
                        new IllegalStateException(new SocketTimeoutException())));
        assertTrue(
                TimeoutExceptionClassifier.isTimeout(
                        new IllegalStateException(new TimeoutException())));
        assertFalse(
                TimeoutExceptionClassifier.isTimeout(
                        new IllegalStateException("ordinary failure")));
    }
}
