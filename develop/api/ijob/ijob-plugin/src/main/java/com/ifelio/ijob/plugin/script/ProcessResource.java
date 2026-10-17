package com.ifelio.ijob.plugin.script;

import java.io.Closeable;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class ProcessResource implements AutoCloseable {
    private static final long TERMINATION_WAIT_MILLIS = 1000L;

    private final Process process;

    public ProcessResource(Process process) {
        this.process = process;
    }

    @Override
    public void close() {
        List<ProcessHandle> descendants =
                process.isAlive() ? process.toHandle().descendants().toList() : List.of();
        for (ProcessHandle descendant : descendants.reversed()) {
            destroy(descendant);
        }
        if (process.isAlive()) {
            try {
                process.destroyForcibly();
                awaitTermination();
            } catch (RuntimeException ignored) {
            }
        }
        closeQuietly(process.getOutputStream());
        closeQuietly(process.getInputStream());
        closeQuietly(process.getErrorStream());
    }

    private void awaitTermination() {
        boolean interrupted = Thread.interrupted();
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(TERMINATION_WAIT_MILLIS);
        try {
            while (process.isAlive()) {
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0) {
                    return;
                }
                try {
                    process.waitFor(remaining, TimeUnit.NANOSECONDS);
                } catch (InterruptedException exception) {
                    interrupted = true;
                }
            }
        } finally {
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void destroy(ProcessHandle processHandle) {
        try {
            if (processHandle.isAlive()) {
                processHandle.destroyForcibly();
            }
        } catch (RuntimeException ignored) {
        }
    }

    private void closeQuietly(Closeable closeable) {
        try {
            closeable.close();
        } catch (IOException ignored) {
        }
    }
}
