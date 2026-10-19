package com.ifielsc.ijob.framework.database.lock;

import java.io.IOException;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;

public final class SqliteDatabasePath {
    private static final String PREFIX = "jdbc:sqlite:";
    private static final Set<String> NETWORK_FILE_SYSTEMS =
            Set.of("nfs", "nfs4", "cifs", "smbfs", "sshfs", "fuse.sshfs", "9p");

    private SqliteDatabasePath() {}

    public static Path fromJdbcUrl(String jdbcUrl) {
        if (jdbcUrl == null || !jdbcUrl.startsWith(PREFIX)) {
            throw invalid();
        }
        String location = jdbcUrl.substring(PREFIX.length());
        int query = location.indexOf('?');
        if (query >= 0) {
            location = location.substring(0, query);
        }
        String normalized = location.trim().toLowerCase(Locale.ROOT);
        if (location.isBlank()
                || normalized.equals(":memory:")
                || normalized.startsWith("file:")
                || location.startsWith("\\\\")) {
            throw invalid();
        }
        Path configured;
        try {
            configured = Path.of(location).normalize();
        } catch (RuntimeException exception) {
            throw new IllegalStateException(
                    "SQLite JDBC URL contains an invalid file path", exception);
        }
        if (!configured.isAbsolute() || configured.getFileName() == null) {
            throw invalid();
        }
        try {
            Path parent = configured.getParent().toRealPath();
            FileStore store = Files.getFileStore(parent);
            String type = store.type().toLowerCase(Locale.ROOT);
            if (NETWORK_FILE_SYSTEMS.stream().anyMatch(type::contains)) {
                throw new IllegalStateException(
                        "SQLite platform database must use a local filesystem");
            }
            Path database = parent.resolve(configured.getFileName()).normalize();
            return Files.exists(database) ? database.toRealPath() : database;
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "SQLite platform database parent directory must exist and be accessible",
                    exception);
        }
    }

    public static Path lockFile(Path database) {
        return Path.of(database.toString() + ".ijob.lock");
    }

    private static IllegalStateException invalid() {
        return new IllegalStateException(
                "SQLite platform database requires an absolute local file JDBC URL");
    }
}
