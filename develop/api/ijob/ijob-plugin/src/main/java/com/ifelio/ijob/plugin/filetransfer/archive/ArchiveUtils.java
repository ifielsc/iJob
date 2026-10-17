package com.ifelio.ijob.plugin.filetransfer.archive;

import com.ifelio.ijob.core.execute.task.TaskContext;
import com.ifelio.ijob.plugin.filetransfer.TransferFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.UUID;

public final class ArchiveUtils {
    private static final int BUFFER = 8192;

    private ArchiveUtils() {}

    public static void copy(InputStream in, OutputStream out, TaskContext context)
            throws IOException {
        byte[] buffer = new byte[BUFFER];
        int len;
        while (true) {
            context.checkExecution();
            len = in.read(buffer);
            if (len < 0) {
                return;
            }
            out.write(buffer, 0, len);
        }
    }

    public static void copy(
            InputStream in,
            OutputStream out,
            TaskContext context,
            ArchiveExtractionQuota quota,
            ArchiveExtractionQuota.Entry entry)
            throws IOException {
        byte[] buffer = new byte[BUFFER];
        int len;
        while (true) {
            context.checkExecution();
            len = in.read(buffer);
            if (len < 0) {
                return;
            }
            quota.record(entry, len);
            out.write(buffer, 0, len);
        }
    }

    public static Path safeResolve(Path root, String name) throws IOException {
        if (name == null || name.isBlank()) {
            throw new IOException("Illegal archive entry");
        }
        String normalizedName = name.replace('\\', '/');
        if (normalizedName.indexOf('\0') >= 0
                || normalizedName.startsWith("/")
                || normalizedName.startsWith("//")
                || normalizedName.matches("^[A-Za-z]:.*")
                || hasTrailingDotOrSpace(normalizedName)
                || containsReservedDeviceName(normalizedName)
                || containsNonPortableCharacter(normalizedName)) {
            throw new IOException("Illegal archive entry");
        }
        try {
            Path normalizedRoot = root.toAbsolutePath().normalize();
            Path path = normalizedRoot.resolve(normalizedName).normalize();
            if (!path.startsWith(normalizedRoot)) {
                throw new IOException("Illegal archive entry");
            }
            return path;
        } catch (InvalidPathException exception) {
            throw new IOException("Illegal archive entry", exception);
        }
    }

    public static String safeEntryName(Path root, String name) throws IOException {
        Path normalizedRoot = root.toAbsolutePath().normalize();
        Path path = safeResolve(normalizedRoot, name);
        String relative = normalizedRoot.relativize(path).toString().replace('\\', '/');
        if (relative.isBlank()) {
            throw new IOException("Illegal archive entry");
        }
        return relative;
    }

    public static Path safeArchivePath(Path root, String name, String suffix) throws IOException {
        String resolved = name;
        if (resolved == null || resolved.isBlank()) {
            resolved = UUID.randomUUID().toString();
        }
        if (!resolved.toLowerCase().endsWith(suffix.toLowerCase())) {
            resolved += suffix;
        }
        if (resolved.indexOf('/') >= 0
                || resolved.indexOf('\\') >= 0
                || resolved.equals(".")
                || resolved.equals("..")) {
            throw new IOException("Illegal archive name");
        }
        Path path = safeResolve(root, resolved);
        Path normalizedRoot = root.toAbsolutePath().normalize();
        if (!normalizedRoot.equals(path.getParent())) {
            throw new IOException("Illegal archive name");
        }
        return path;
    }

    public static TransferFile buildArchiveFile(Path path) {
        return TransferFile.builder()
                .fileName(path.getFileName().toString())
                .relativePath(path.getFileName().toString())
                .localFile(path.toFile())
                .size(path.toFile().length())
                .build();
    }

    public static TransferFile buildExtractFile(Path path, String relativePath) {
        return TransferFile.builder()
                .fileName(path.getFileName().toString())
                .relativePath(relativePath.replace('\\', '/'))
                .localFile(path.toFile())
                .size(path.toFile().length())
                .build();
    }

    private static boolean containsNonPortableCharacter(String name) {
        for (int index = 0; index < name.length(); index++) {
            char value = name.charAt(index);
            if (value < 32 || "<>:\"|?*".indexOf(value) >= 0) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasTrailingDotOrSpace(String name) {
        for (String segment : name.split("/")) {
            if (segment.endsWith(".") || segment.endsWith(" ")) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsReservedDeviceName(String name) {
        for (String segment : name.split("/")) {
            String base = segment.split("\\.", 2)[0].toUpperCase(java.util.Locale.ROOT);
            if (base.equals("CON")
                    || base.equals("PRN")
                    || base.equals("AUX")
                    || base.equals("NUL")
                    || base.matches("COM[1-9]")
                    || base.matches("LPT[1-9]")) {
                return true;
            }
        }
        return false;
    }
}
