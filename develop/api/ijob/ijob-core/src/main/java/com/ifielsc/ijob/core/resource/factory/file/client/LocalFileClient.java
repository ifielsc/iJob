// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.core.resource.factory.file.client;

import com.ifielsc.ijob.core.resource.factory.file.FileResourceConfig;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

public class LocalFileClient extends AbstractFileClient {
    private final Path root;

    public LocalFileClient(FileResourceConfig config) {
        super("/");
        try {
            Path configured = Paths.get(config.getBasePath());
            if (!configured.isAbsolute() || !Files.isDirectory(configured)) {
                throw new IllegalArgumentException(
                        "Local file root must be an existing absolute directory");
            }
            this.root = configured.toRealPath();
        } catch (IOException | InvalidPathException e) {
            throw new IllegalArgumentException("Local file root is invalid", e);
        }
    }

    @Override
    public List<FileInfo> list(String path) throws IOException {
        return list(path, 100_000);
    }

    @Override
    public List<FileInfo> list(String path, int maxEntries) throws IOException {
        Path target = resolvePath(path);
        if (!Files.exists(target)) {
            return Collections.emptyList();
        }
        if (!Files.isDirectory(target)) {
            throw new IOException("Not directory:" + path);
        }

        List<FileInfo> result = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(target)) {
            for (Path file : stream) {
                if (result.size() >= maxEntries)
                    throw new IOException("Directory entry quota exceeded");
                rejectSymbolicLink(file);
                BasicFileAttributes attrs =
                        Files.readAttributes(
                                file, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);

                result.add(
                        FileInfo.builder()
                                .name(file.getFileName().toString())
                                .directory(attrs.isDirectory())
                                .size(attrs.size())
                                .lastModified(
                                        LocalDateTime.ofInstant(
                                                attrs.lastModifiedTime().toInstant(),
                                                ZoneId.systemDefault()))
                                .build());
            }
        }
        return result;
    }

    @Override
    public InputStream download(String path) throws IOException {
        Path target = resolvePath(path);
        if (!Files.exists(target)) {
            throw new FileNotFoundException(path);
        }
        if (!Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Not regular file:" + path);
        }

        return new BufferedInputStream(Files.newInputStream(target));
    }

    @Override
    public void upload(String path, InputStream input) throws IOException {
        Path target = resolvePath(path);
        if (target.equals(root)) {
            throw new IOException("Cannot upload to resource root");
        }
        Path parent = target.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        target = resolvePath(path);

        Path temp = Files.createTempFile(parent, ".ijob-", ".part");
        try {
            try (OutputStream out =
                    new BufferedOutputStream(
                            Files.newOutputStream(temp, StandardOpenOption.TRUNCATE_EXISTING))) {
                byte[] buffer = new byte[8192];
                int len;
                while ((len = input.read(buffer)) != -1) {
                    out.write(buffer, 0, len);
                }
                out.flush();
            }
            moveReplacing(temp, target);
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    @Override
    public void mkdirs(String path) throws IOException {
        Path target = resolvePath(path);
        Files.createDirectories(target);
        resolvePath(path);
    }

    @Override
    public boolean exists(String path) throws IOException {
        return Files.exists(resolvePath(path));
    }

    @Override
    public void delete(String path) throws IOException {
        Path target = resolvePath(path);
        if (!Files.exists(target)) {
            return;
        }

        if (Files.isDirectory(target)) {
            deleteDirectory(target);
        } else {
            Files.deleteIfExists(target);
        }
    }

    /** Deletes descendants before their parent directory. */
    private void deleteDirectory(Path dir) throws IOException {
        try (Stream<Path> stream = Files.walk(dir)) {
            stream.sorted(Comparator.reverseOrder())
                    .forEach(
                            item -> {
                                try {
                                    Files.deleteIfExists(item);
                                } catch (IOException e) {
                                    throw new UncheckedIOException(e);
                                }
                            });
        }
    }

    private Path resolvePath(String path) {
        if (path == null || path.isBlank()) {
            return root;
        }
        String portable = path.replace('\\', '/');
        if (portable.indexOf('\0') >= 0
                || portable.matches("^[A-Za-z]:.*")
                || portable.startsWith("//")) {
            throw new SecurityException("Illegal path");
        }
        while (portable.startsWith("/")) {
            portable = portable.substring(1);
        }
        Path relative = Paths.get(portable);
        if (relative.isAbsolute()) {
            throw new SecurityException("Illegal path");
        }
        Path result = root.resolve(relative).normalize();
        if (!result.startsWith(root)) {
            throw new SecurityException("Illegal path");
        }
        Path current = root;
        for (Path segment : root.relativize(result)) {
            current = current.resolve(segment);
            rejectSymbolicLink(current);
        }
        return result;
    }

    private void rejectSymbolicLink(Path path) {
        if (Files.isSymbolicLink(path)) {
            throw new SecurityException("Symbolic links are not allowed");
        }
        // Windows junctions are reparse points but are not reported as symbolic
        // links. Resolve each existing component before any operation follows it.
        if (Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
            try {
                if (!path.toRealPath().equals(path.toAbsolutePath().normalize())) {
                    throw new SecurityException("Filesystem links are not allowed");
                }
            } catch (IOException e) {
                throw new SecurityException("Cannot verify resource path", e);
            }
        }
    }

    private void moveReplacing(Path source, Path target) throws IOException {
        try {
            Files.move(
                    source,
                    target,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
