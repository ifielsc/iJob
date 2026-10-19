// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.plugin.localjar;

import com.ifielsc.ijob.common.exception.StopException;
import com.ifielsc.ijob.core.execute.task.TaskContext;
import com.ifielsc.ijob.spi.localjar.LocalJarContext;
import com.ifielsc.ijob.spi.localjar.LocalJarStoppedException;

import java.util.concurrent.atomic.AtomicInteger;

final class LocalJarContextAdapter implements LocalJarContext {
    private static final int MAX_LOG_MESSAGE_CHARS = 4096;
    private static final int MAX_LOG_TOTAL_CHARS = 256 * 1024;

    private final TaskContext delegate;
    private final AtomicInteger loggedCharacters = new AtomicInteger();

    LocalJarContextAdapter(TaskContext delegate) {
        this.delegate = delegate;
    }

    @Override
    public long instanceId() {
        return delegate.getInstanceId();
    }

    @Override
    public String resolve(String text) {
        return delegate.resolve(text);
    }

    @Override
    public Object resolveObject(Object value) {
        return delegate.resolveObject(value);
    }

    @Override
    public Object variable(String name) {
        return delegate.getVariable(name);
    }

    @Override
    public void putVariable(String name, Object value) {
        delegate.putVariable(name, new LocalJarOutputSanitizer().sanitize(value));
    }

    @Override
    public void removeVariable(String name) {
        delegate.removeVariable(name);
    }

    @Override
    public void checkExecution() {
        try {
            delegate.checkStop();
            delegate.checkPause();
            delegate.checkInterrupted();
        } catch (StopException exception) {
            throw new LocalJarStoppedException();
        }
    }

    @Override
    public void registerResource(AutoCloseable resource) {
        delegate.registerResource(resource);
    }

    @Override
    public void progress(int progress) {
        if (progress < 0 || progress > 100) {
            throw new IllegalArgumentException("progress must be between 0 and 100");
        }
        delegate.progress(progress);
    }

    @Override
    public void logInfo(String message) {
        String safe = safeLog(message);
        if (safe != null) {
            delegate.logInfo(safe);
        }
    }

    @Override
    public void logWarn(String message) {
        String safe = safeLog(message);
        if (safe != null) {
            delegate.logWarn(safe);
        }
    }

    @Override
    public void logError(String message) {
        String safe = safeLog(message);
        if (safe != null) {
            delegate.logError(safe);
        }
    }

    private String safeLog(String message) {
        if (message == null) {
            return null;
        }
        StringBuilder sanitized =
                new StringBuilder(Math.min(message.length(), MAX_LOG_MESSAGE_CHARS));
        for (int index = 0;
                index < message.length() && sanitized.length() < MAX_LOG_MESSAGE_CHARS;
                index++) {
            char value = message.charAt(index);
            sanitized.append(Character.isISOControl(value) && value != '\t' ? ' ' : value);
        }
        int size = sanitized.length();
        int total = loggedCharacters.addAndGet(size);
        if (total > MAX_LOG_TOTAL_CHARS) {
            return null;
        }
        return sanitized.toString();
    }
}
