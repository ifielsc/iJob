package com.ifielsc.ijob.spi.localjar;

public record LocalJarResult(Status status, String message) {
    public enum Status {
        SUCCESS,
        FAILED,
        STOPPED,
        TIMED_OUT,
        SKIPPED
    }

    public LocalJarResult {
        if (status == null) {
            throw new IllegalArgumentException("status is required");
        }
    }

    public static LocalJarResult success() {
        return new LocalJarResult(Status.SUCCESS, null);
    }

    public static LocalJarResult success(String message) {
        return new LocalJarResult(Status.SUCCESS, message);
    }

    public static LocalJarResult failed(String message) {
        return new LocalJarResult(Status.FAILED, message);
    }

    public static LocalJarResult stopped(String message) {
        return new LocalJarResult(Status.STOPPED, message);
    }

    public static LocalJarResult timedOut(String message) {
        return new LocalJarResult(Status.TIMED_OUT, message);
    }

    public static LocalJarResult skipped(String message) {
        return new LocalJarResult(Status.SKIPPED, message);
    }
}
