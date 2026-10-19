// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.plugin.etl;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.concurrent.atomic.AtomicBoolean;

public final class TempDirectoryResource implements AutoCloseable {
    private final Path directory;
    private final AtomicBoolean closed = new AtomicBoolean();

    public TempDirectoryResource(Path directory) {
        this.directory = directory.toAbsolutePath().normalize();
    }

    public Path directory() {
        return directory;
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true) || !Files.exists(directory)) {
            return;
        }
        try (var paths = Files.walk(directory)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                if (!path.toAbsolutePath().normalize().startsWith(directory)) {
                    throw new IllegalStateException("ETL temporary directory boundary changed");
                }
                Files.deleteIfExists(path);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("ETL temporary directory cleanup failed", exception);
        }
    }
}
