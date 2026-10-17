package com.ifelio.ijob.plugin.localjar;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

final class LocalJarClassLoader extends URLClassLoader {
    private static final String[] PARENT_FIRST = {
        "java.",
        "javax.",
        "jdk.",
        "sun.",
        "com.ifelio.ijob.",
        "com.ifeel.ijob.",
        "org.springframework."
    };

    private final Path executionCopy;

    private LocalJarClassLoader(Path executionCopy, ClassLoader parent) throws IOException {
        super(new URL[] {executionCopy.toUri().toURL()}, parent);
        this.executionCopy = executionCopy;
    }

    static LocalJarClassLoader open(Path artifact, String expectedSha256, ClassLoader parent)
            throws IOException {
        Path copy = Files.createTempFile("ijob-local-jar-", ".jar");
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = new DigestInputStream(Files.newInputStream(artifact), digest);
                    OutputStream output = Files.newOutputStream(copy)) {
                input.transferTo(output);
            }
            String actual = HexFormat.of().formatHex(digest.digest());
            if (!actual.equals(expectedSha256)) {
                throw new IOException("Local JAR changed before class loader creation");
            }
            return new LocalJarClassLoader(copy, parent);
        } catch (IOException | NoSuchAlgorithmException | RuntimeException exception) {
            Files.deleteIfExists(copy);
            if (exception instanceof IOException ioException) {
                throw ioException;
            }
            throw new IOException("Local JAR execution copy could not be created", exception);
        }
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        synchronized (getClassLoadingLock(name)) {
            Class<?> loaded = findLoadedClass(name);
            if (loaded == null) {
                if (parentFirst(name)) {
                    loaded = super.loadClass(name, false);
                } else {
                    try {
                        loaded = findClass(name);
                    } catch (ClassNotFoundException ignored) {
                        loaded = super.loadClass(name, false);
                    }
                }
            }
            if (resolve) {
                resolveClass(loaded);
            }
            return loaded;
        }
    }

    static boolean parentFirst(String name) {
        for (String prefix : PARENT_FIRST) {
            if (name.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void close() throws IOException {
        IOException failure = null;
        try {
            super.close();
        } catch (IOException exception) {
            failure = exception;
        }
        try {
            Files.deleteIfExists(executionCopy);
        } catch (IOException exception) {
            if (failure == null) {
                failure = exception;
            } else {
                failure.addSuppressed(exception);
            }
        }
        if (failure != null) {
            throw failure;
        }
    }
}
