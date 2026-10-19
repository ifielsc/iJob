// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.plugin.localjar;

import com.ifielsc.ijob.spi.localjar.LocalJarTask;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Component
@RequiredArgsConstructor
final class LocalJarArtifactValidator {
    static final String SPI_VERSION = "1";
    private static final String API_VERSION_ATTRIBUTE = "IJob-Plugin-Api-Version";
    private static final String ENTRY_CLASS_ATTRIBUTE = "IJob-Plugin-Class";
    private static final Pattern FILE_NAME =
            Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,250}\\.jar", Pattern.CASE_INSENSITIVE);
    private static final Pattern SHA_256 = Pattern.compile("[0-9a-f]{64}");
    private static final Pattern CLASS_NAME =
            Pattern.compile("[A-Za-z_$][A-Za-z0-9_$]*(\\.[A-Za-z_$][A-Za-z0-9_$]*)+");
    private static final String[] FORBIDDEN_CLASS_PREFIXES = {
        "java/",
        "javax/",
        "jdk/",
        "sun/",
        "com/ifielsc/ijob/",
        "com/ifelio/ijob/",
        "com/ifeel/ijob/",
        "org/springframework/"
    };

    private final LocalJarPluginProperties properties;

    LocalJarArtifact validate(String fileName, String expectedSha256, String expectedEntryClass) {
        return validate(fileName, expectedSha256, expectedEntryClass, () -> {});
    }

    LocalJarArtifact validate(
            String fileName,
            String expectedSha256,
            String expectedEntryClass,
            Runnable controlCheck) {
        controlCheck.run();
        validateProperties();
        validateFileName(fileName);
        String expectedSha = requireSha(expectedSha256);
        String expectedEntry = requireEntryClass(expectedEntryClass);
        Path path = resolveArtifact(fileName);
        BasicFileAttributes before = attributes(path);
        long size = before.size();
        if (size < 1 || size > properties.getMaxJarBytes()) {
            throw invalid("Local JAR size is invalid");
        }
        ValidationCopy validationCopy = copyForValidation(path, expectedSha, controlCheck);
        try {
            BasicFileAttributes after = attributes(path);
            if (after.size() != size
                    || !before.lastModifiedTime().equals(after.lastModifiedTime())) {
                throw invalid("Local JAR changed during validation");
            }
            controlCheck.run();
            inspectArchive(validationCopy.path(), size, controlCheck);
            controlCheck.run();
            ManifestDescriptor descriptor = manifest(validationCopy.path());
            if (!SPI_VERSION.equals(descriptor.spiVersion())) {
                throw invalid("Local JAR SPI version is unsupported");
            }
            if (!expectedEntry.equals(descriptor.entryClass())) {
                throw invalid("Local JAR entry class does not match manifest");
            }
            controlCheck.run();
            verifyEntryClass(validationCopy.path(), expectedEntry, validationCopy.sha256());
            return new LocalJarArtifact(
                    path,
                    fileName,
                    size,
                    validationCopy.sha256(),
                    descriptor.implementationVersion(),
                    descriptor.spiVersion(),
                    descriptor.entryClass());
        } finally {
            deleteQuietly(validationCopy.path());
        }
    }

    private void validateProperties() {
        if (!properties.isEnabled()) {
            throw invalid("Local JAR plugin is disabled");
        }
        if (properties.getRootDirectory() == null
                || properties.getRootDirectory().toString().isBlank()
                || properties.getMaxJarBytes() < 1
                || properties.getMaxJarEntries() < 1
                || properties.getMaxExpandedBytes() < 1
                || properties.getMaxCompressionRatio() < 1
                || properties.getMaxCachedLoaders() < 1) {
            throw invalid("Local JAR plugin configuration is invalid");
        }
    }

    private void validateFileName(String fileName) {
        if (fileName == null
                || !FILE_NAME.matcher(fileName).matches()
                || fileName.equals(".")
                || fileName.equals("..")) {
            throw invalid("Local JAR file name is invalid");
        }
    }

    private String requireSha(String value) {
        String normalized = value == null ? "" : value.toLowerCase(Locale.ROOT);
        if (!SHA_256.matcher(normalized).matches()) {
            throw invalid("Local JAR SHA-256 is invalid");
        }
        return normalized;
    }

    private String requireEntryClass(String value) {
        if (value == null
                || value.length() > 255
                || !CLASS_NAME.matcher(value).matches()
                || value.startsWith("com.ifielsc.ijob.")
                || value.startsWith("com.ifelio.ijob.")
                || value.startsWith("com.ifeel.ijob.")) {
            throw invalid("Local JAR entry class is invalid");
        }
        return value;
    }

    private Path resolveArtifact(String fileName) {
        try {
            Path configuredRoot = properties.getRootDirectory().toAbsolutePath().normalize();
            if (Files.isSymbolicLink(configuredRoot)
                    || !Files.isDirectory(configuredRoot, LinkOption.NOFOLLOW_LINKS)) {
                throw invalid("Local JAR root directory is invalid");
            }
            Path root = configuredRoot.toRealPath();
            Path candidate = root.resolve(fileName).normalize();
            if (!candidate.startsWith(root)
                    || Files.isSymbolicLink(candidate)
                    || !Files.isRegularFile(candidate, LinkOption.NOFOLLOW_LINKS)) {
                throw invalid("Local JAR file is unavailable");
            }
            Path real = candidate.toRealPath();
            if (!real.startsWith(root) || !root.equals(real.getParent())) {
                throw invalid("Local JAR file escapes the configured root");
            }
            return real;
        } catch (IOException exception) {
            throw invalid("Local JAR file is unavailable", exception);
        }
    }

    private BasicFileAttributes attributes(Path path) {
        try {
            return Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        } catch (IOException exception) {
            throw invalid("Local JAR attributes are unavailable", exception);
        }
    }

    private ValidationCopy copyForValidation(Path path, String expectedSha, Runnable controlCheck) {
        Path copy = null;
        try {
            copy = Files.createTempFile("ijob-local-jar-validation-", ".jar");
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input =
                            new DigestInputStream(
                                    new BufferedInputStream(Files.newInputStream(path)), digest);
                    OutputStream output = Files.newOutputStream(copy)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) != -1) {
                    controlCheck.run();
                    if (read > 0) {
                        output.write(buffer, 0, read);
                    }
                }
            }
            String actualSha = HexFormat.of().formatHex(digest.digest());
            if (!actualSha.equals(expectedSha)) {
                throw invalid("Local JAR SHA-256 does not match");
            }
            return new ValidationCopy(copy, actualSha);
        } catch (RuntimeException exception) {
            deleteQuietly(copy);
            throw exception;
        } catch (IOException | NoSuchAlgorithmException exception) {
            deleteQuietly(copy);
            throw invalid("Local JAR validation copy could not be created", exception);
        }
    }

    private void deleteQuietly(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // Temporary validation copies are best-effort cleanup.
        }
    }

    private void inspectArchive(Path path, long compressedBytes, Runnable controlCheck) {
        long expanded = 0;
        int entries = 0;
        byte[] buffer = new byte[8192];
        try (ZipInputStream zip =
                new ZipInputStream(new BufferedInputStream(Files.newInputStream(path)))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                controlCheck.run();
                entries++;
                if (entries > properties.getMaxJarEntries()) {
                    throw invalid("Local JAR has too many entries");
                }
                validateEntryName(entry.getName());
                if (!entry.isDirectory()) {
                    int read;
                    while ((read = zip.read(buffer)) != -1) {
                        controlCheck.run();
                        if (read == 0) {
                            continue;
                        }
                        expanded = saturatedAdd(expanded, read);
                        if (expanded > properties.getMaxExpandedBytes()
                                || expanded / Math.max(1, compressedBytes)
                                        > properties.getMaxCompressionRatio()) {
                            throw invalid("Local JAR expanded size exceeds limit");
                        }
                    }
                }
                zip.closeEntry();
            }
        } catch (IOException | SecurityException exception) {
            throw invalid("Local JAR archive is invalid", exception);
        }
        if (entries == 0) {
            throw invalid("Local JAR archive is empty");
        }
    }

    private void validateEntryName(String entryName) {
        if (entryName == null
                || entryName.isBlank()
                || entryName.indexOf('\0') >= 0
                || entryName.indexOf('\\') >= 0
                || entryName.startsWith("/")
                || entryName.contains("../")
                || entryName.toLowerCase(Locale.ROOT).endsWith(".jar")) {
            throw invalid("Local JAR contains an unsafe entry");
        }
        String logicalName = entryName;
        if (entryName.startsWith("META-INF/versions/")) {
            String remainder = entryName.substring("META-INF/versions/".length());
            int separator = remainder.indexOf('/');
            logicalName = separator < 0 ? remainder : remainder.substring(separator + 1);
        }
        if (!logicalName.endsWith(".class")) {
            return;
        }
        for (String prefix : FORBIDDEN_CLASS_PREFIXES) {
            if (logicalName.startsWith(prefix)) {
                throw invalid("Local JAR contains a reserved class package");
            }
        }
    }

    private ManifestDescriptor manifest(Path path) {
        try (JarFile jar = new JarFile(path.toFile(), true)) {
            Manifest manifest = jar.getManifest();
            if (manifest == null) {
                throw invalid("Local JAR manifest is required");
            }
            Attributes attributes = manifest.getMainAttributes();
            return new ManifestDescriptor(
                    requiredAttribute(attributes, API_VERSION_ATTRIBUTE, 16),
                    requiredAttribute(
                            attributes, Attributes.Name.IMPLEMENTATION_VERSION.toString(), 64),
                    requiredAttribute(attributes, ENTRY_CLASS_ATTRIBUTE, 255));
        } catch (IOException | SecurityException exception) {
            throw invalid("Local JAR manifest is invalid", exception);
        }
    }

    private String requiredAttribute(Attributes attributes, String name, int maxLength) {
        String value = attributes.getValue(name);
        if (value == null || value.isBlank() || value.length() > maxLength) {
            throw invalid("Local JAR manifest attribute is invalid: " + name);
        }
        return value.trim();
    }

    private void verifyEntryClass(Path path, String entryClass, String expectedSha256) {
        try (LocalJarClassLoader loader =
                LocalJarClassLoader.open(
                        path, expectedSha256, LocalJarTask.class.getClassLoader())) {
            Class<?> type = Class.forName(entryClass, false, loader);
            if (type.getClassLoader() != loader
                    || !LocalJarTask.class.isAssignableFrom(type)
                    || !Modifier.isPublic(type.getModifiers())
                    || Modifier.isAbstract(type.getModifiers())) {
                throw invalid("Local JAR entry class is incompatible");
            }
            type.getConstructor();
        } catch (ReflectiveOperationException
                | IOException
                | LinkageError
                | SecurityException exception) {
            throw invalid("Local JAR entry class could not be loaded", exception);
        }
    }

    private long saturatedAdd(long current, int increment) {
        return current > Long.MAX_VALUE - increment ? Long.MAX_VALUE : current + increment;
    }

    private IllegalArgumentException invalid(String message) {
        return new IllegalArgumentException(message);
    }

    private IllegalArgumentException invalid(String message, Throwable cause) {
        return new IllegalArgumentException(message, cause);
    }

    private record ManifestDescriptor(
            String spiVersion, String implementationVersion, String entryClass) {}

    private record ValidationCopy(Path path, String sha256) {}
}
