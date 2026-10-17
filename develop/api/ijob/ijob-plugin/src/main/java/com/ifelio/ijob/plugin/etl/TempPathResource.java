package com.ifelio.ijob.plugin.etl;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;

public final class TempPathResource implements AutoCloseable {
    private final Path path;
    private final AtomicBoolean closed = new AtomicBoolean();

    public TempPathResource(Path path) {
        this.path = path;
    }

    public Path path() {
        return path;
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException exception) {
            throw new IllegalStateException("ETL temporary file cleanup failed", exception);
        }
    }
}
