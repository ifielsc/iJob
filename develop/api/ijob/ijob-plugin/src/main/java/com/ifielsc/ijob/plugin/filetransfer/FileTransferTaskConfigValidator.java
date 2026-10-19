package com.ifielsc.ijob.plugin.filetransfer;

import org.springframework.stereotype.Component;

import java.nio.file.FileSystems;

@Component
public class FileTransferTaskConfigValidator {
    private static final int MAX_PATH_LENGTH = 2048;
    private static final int MAX_PATTERN_LENGTH = 512;
    private static final int MAX_ARCHIVE_NAME_LENGTH = 255;

    public void validate(FileTransferConfig config) {
        if (config == null) {
            throw invalid("File transfer configuration is required");
        }
        if (config.getMode() == null || config.getRemotePublishMode() == null) {
            throw invalid("File transfer mode is required");
        }
        if (config.getMode() == TransferMode.WRITE_ONLY) {
            throw invalid("File transfer WRITE_ONLY mode is not supported");
        }
        requirePositive(
                config.getSourceResourceId(), "File transfer sourceResourceId must be positive");
        if (config.getMode() == TransferMode.TRANSFER) {
            requirePositive(
                    config.getTargetResourceId(),
                    "File transfer targetResourceId must be positive");
        }
        validatePath(config.getSourcePath(), "sourcePath");
        validatePath(config.getTargetPath(), "targetPath");
        validatePattern(config.getFilePattern());
        if (config.getCompressType() == null
                || config.getDecompressType() == null
                || config.getCompressionLevel() == null) {
            throw invalid("File transfer archive configuration is required");
        }
        validateArchiveName(config.getArchiveName());
        validateExtractionLimits(config);
        TransferQuota.validate(config);
        if (config.getArchiveName() != null
                && !config.getArchiveName().isBlank()
                && config.getCompressType() != CompressType.ZIP
                && config.getCompressType() != CompressType.TAR
                && config.getCompressType() != CompressType.TAR_GZ
                && config.getCompressType() != CompressType.SEVEN_ZIP) {
            throw invalid("archiveName requires a bundled compression type");
        }
        if (config.getCompressionLevel() != CompressionLevel.DEFAULT
                && config.getCompressType() != CompressType.ZIP) {
            throw invalid("compressionLevel is configurable only for ZIP");
        }
        if (config.isDeleteSource() && config.getMode() != TransferMode.TRANSFER) {
            throw invalid("deleteSource requires TRANSFER mode");
        }
        if (config.isDeleteSource()
                && config.getSourceResourceId().equals(config.getTargetResourceId())) {
            throw invalid("deleteSource requires different source and target resources");
        }
    }

    private void validateExtractionLimits(FileTransferConfig config) {
        if (config.getMaxArchiveEntries() < 1
                || config.getMaxArchiveEntries() > 100_000
                || config.getMaxExtractedEntryBytes() < 1024
                || config.getMaxExtractedEntryBytes() > 1024L * 1024 * 1024
                || config.getMaxExtractedTotalBytes() < config.getMaxExtractedEntryBytes()
                || config.getMaxExtractedTotalBytes() > 10L * 1024 * 1024 * 1024
                || config.getMaxCompressionRatio() < 1
                || config.getMaxCompressionRatio() > 1000) {
            throw invalid("File transfer extraction limits are invalid");
        }
    }

    private void validatePath(String path, String field) {
        if (path != null && (path.length() > MAX_PATH_LENGTH || containsControl(path))) {
            throw invalid("File transfer " + field + " is invalid");
        }
    }

    private void validatePattern(String pattern) {
        if (pattern == null
                || pattern.isBlank()
                || pattern.length() > MAX_PATTERN_LENGTH
                || pattern.indexOf('/') >= 0
                || pattern.indexOf('\\') >= 0) {
            throw invalid("File transfer filePattern is invalid");
        }
        try {
            FileSystems.getDefault().getPathMatcher("glob:" + pattern);
        } catch (RuntimeException e) {
            throw invalid("File transfer filePattern is invalid");
        }
    }

    private void validateArchiveName(String archiveName) {
        if (archiveName != null
                && (archiveName.length() > MAX_ARCHIVE_NAME_LENGTH
                        || archiveName.indexOf('/') >= 0
                        || archiveName.indexOf('\\') >= 0
                        || containsControl(archiveName))) {
            throw invalid("File transfer archiveName is invalid");
        }
    }

    private void requirePositive(Long value, String message) {
        if (value == null || value <= 0) {
            throw invalid(message);
        }
    }

    private boolean containsControl(String value) {
        return value.indexOf('\0') >= 0 || value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0;
    }

    private IllegalArgumentException invalid(String message) {
        return new IllegalArgumentException(message);
    }
}
