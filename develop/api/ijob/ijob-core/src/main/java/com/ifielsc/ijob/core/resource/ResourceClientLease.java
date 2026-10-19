package com.ifielsc.ijob.core.resource;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ResourceClientLease<T> implements AutoCloseable {
    private final T client;
    private final AutoCloseable release;
    private final AtomicBoolean closed = new AtomicBoolean();

    public ResourceClientLease(T client, AutoCloseable release) {
        this.client = Objects.requireNonNull(client, "client");
        this.release = Objects.requireNonNull(release, "release");
    }

    public T getClient() {
        return client;
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            try {
                release.close();
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new IllegalStateException("Resource client release failed", e);
            }
        }
    }
}
