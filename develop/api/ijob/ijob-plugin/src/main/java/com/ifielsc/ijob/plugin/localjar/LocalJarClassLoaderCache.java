// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.plugin.localjar;

import com.ifielsc.ijob.spi.localjar.LocalJarTask;

import jakarta.annotation.PreDestroy;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
@RequiredArgsConstructor
final class LocalJarClassLoaderCache {
    private final LocalJarPluginProperties properties;
    private final Map<String, Entry> entries = new HashMap<>();
    private final Map<String, Object> loadingLocks = new ConcurrentHashMap<>();
    private boolean closed;

    Lease acquire(LocalJarArtifact artifact) {
        String fingerprint = artifact.sha256();
        Entry cached = retain(fingerprint);
        if (cached != null) {
            return lease(fingerprint, cached);
        }
        Object loadingLock = loadingLocks.computeIfAbsent(fingerprint, ignored -> new Object());
        try {
            synchronized (loadingLock) {
                cached = retain(fingerprint);
                if (cached != null) {
                    return lease(fingerprint, cached);
                }
                Entry created = createEntry(artifact);
                synchronized (this) {
                    if (closed) {
                        closeQuietly(created.loader);
                        throw new IllegalStateException("Local JAR class loader cache is closed");
                    }
                    entries.put(fingerprint, created);
                    created.references++;
                    created.lastAccess = now();
                    evictIdle();
                }
                return lease(fingerprint, created);
            }
        } finally {
            loadingLocks.remove(fingerprint, loadingLock);
        }
    }

    private synchronized Entry retain(String fingerprint) {
        if (closed) {
            throw new IllegalStateException("Local JAR class loader cache is closed");
        }
        Entry entry = entries.get(fingerprint);
        if (entry == null) {
            return null;
        }
        entry.references++;
        entry.lastAccess = now();
        return entry;
    }

    private Entry createEntry(LocalJarArtifact artifact) {
        try {
            return new Entry(
                    LocalJarClassLoader.open(
                            artifact.path(),
                            artifact.sha256(),
                            LocalJarTask.class.getClassLoader()));
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Local JAR class loader could not be created", exception);
        }
    }

    private Lease lease(String fingerprint, Entry entry) {
        return new Lease(entry.loader, () -> release(fingerprint, entry));
    }

    private synchronized void release(String fingerprint, Entry expected) {
        Entry entry = entries.get(fingerprint);
        if (entry != expected || entry.references < 1) {
            return;
        }
        entry.references--;
        entry.lastAccess = now();
        evictIdle();
    }

    private void evictIdle() {
        while (entries.size() > properties.getMaxCachedLoaders()) {
            Map.Entry<String, Entry> candidate =
                    entries.entrySet().stream()
                            .filter(item -> item.getValue().references == 0)
                            .min(Comparator.comparingLong(item -> item.getValue().lastAccess))
                            .orElse(null);
            if (candidate == null) {
                return;
            }
            entries.remove(candidate.getKey());
            closeQuietly(candidate.getValue().loader);
        }
    }

    @PreDestroy
    synchronized void close() {
        closed = true;
        entries.values().forEach(entry -> closeQuietly(entry.loader));
        entries.clear();
    }

    private long now() {
        return Instant.now().toEpochMilli();
    }

    private void closeQuietly(LocalJarClassLoader loader) {
        try {
            loader.close();
        } catch (IOException ignored) {
            // Application shutdown or cache eviction remains best effort.
        }
    }

    private static final class Entry {
        private final LocalJarClassLoader loader;
        private int references;
        private long lastAccess;

        private Entry(LocalJarClassLoader loader) {
            this.loader = loader;
            this.lastAccess = Instant.now().toEpochMilli();
        }
    }

    static final class Lease implements AutoCloseable {
        private final LocalJarClassLoader loader;
        private final Runnable release;
        private final AtomicBoolean closed = new AtomicBoolean();

        private Lease(LocalJarClassLoader loader, Runnable release) {
            this.loader = loader;
            this.release = release;
        }

        LocalJarClassLoader loader() {
            return loader;
        }

        @Override
        public void close() {
            if (closed.compareAndSet(false, true)) {
                release.run();
            }
        }
    }
}
