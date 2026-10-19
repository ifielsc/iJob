package com.ifielsc.ijob.plugin.filetransfer.archive;

import com.ifielsc.ijob.core.execute.task.TaskContext;
import com.ifielsc.ijob.plugin.filetransfer.CompressType;
import com.ifielsc.ijob.plugin.filetransfer.FileTransferConfig;
import com.ifielsc.ijob.plugin.filetransfer.TransferFile;

import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ArchiveService {
    private final Map<CompressType, ArchiveHandler> handlers;

    public ArchiveService(List<ArchiveHandler> list) {
        this.handlers =
                list.stream()
                        .collect(
                                Collectors.toUnmodifiableMap(
                                        ArchiveHandler::type,
                                        Function.identity(),
                                        (existing, duplicate) -> {
                                            throw new IllegalStateException(
                                                    "Duplicate archive handler type: "
                                                            + existing.type());
                                        }));
    }

    public List<TransferFile> compress(
            List<TransferFile> files, FileTransferConfig config, TaskContext context, Path tempDir)
            throws IOException {
        if (files == null || files.isEmpty()) {
            return files;
        }
        CompressType type = config.getCompressType();
        if (type == null || type == CompressType.NONE) {
            return files;
        }

        ArchiveHandler handler = getHandler(type);
        return handler.compress(files, config, context, tempDir);
    }

    public List<TransferFile> decompress(
            List<TransferFile> files, FileTransferConfig config, TaskContext context, Path tempDir)
            throws IOException {
        if (files == null || files.isEmpty()) {
            return files;
        }
        CompressType type = config.getDecompressType();
        if (type == null || type == CompressType.NONE) {
            return files;
        }

        List<TransferFile> archives = files.stream().filter(file -> isArchive(file, type)).toList();
        if (archives.isEmpty()) {
            return files;
        }
        List<TransferFile> result = new ArrayList<>();
        files.stream().filter(file -> !isArchive(file, type)).forEach(result::add);
        result.addAll(getHandler(type).decompress(archives, config, context, tempDir));
        return List.copyOf(result);
    }

    private boolean isArchive(TransferFile file, CompressType type) {
        if (file == null) {
            return false;
        }
        String path = file.getRelativePath();
        if (path == null || path.isBlank()) {
            path = file.getFileName();
        }
        if (path == null) {
            return false;
        }
        String lower = path.toLowerCase(Locale.ROOT);
        return switch (type) {
            case ZIP -> lower.endsWith(".zip");
            case GZIP -> lower.endsWith(".gz") || lower.endsWith(".gzip");
            case TAR -> lower.endsWith(".tar");
            case TAR_GZ -> lower.endsWith(".tar.gz") || lower.endsWith(".tgz");
            case SEVEN_ZIP -> lower.endsWith(".7z");
            case NONE -> false;
        };
    }

    private ArchiveHandler getHandler(CompressType type) {
        ArchiveHandler handler = handlers.get(type);
        if (handler == null) {
            throw new IllegalArgumentException("Unsupported archive: " + type);
        }
        return handler;
    }
}
