package com.ifelio.ijob.plugin.filetransfer;

import com.ifelio.ijob.common.file.FileUtils;
import com.ifelio.ijob.core.execute.task.TaskContext;
import com.ifelio.ijob.core.resource.ResourceClientLease;
import com.ifelio.ijob.core.resource.ResourceResolver;
import com.ifelio.ijob.core.resource.factory.file.client.FileClient;
import com.ifelio.ijob.core.resource.factory.file.client.FileInfo;
import com.ifelio.ijob.plugin.filetransfer.archive.ArchiveService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class FileTransferService {
    private final ResourceResolver resourceResolver;
    private final ArchiveService archiveService;

    public FileTransferResult transfer(FileTransferConfig config, TaskContext context)
            throws Exception {
        context.checkStop();
        context.progress(5);
        context.logInfo(
                "File transfer started, mode="
                        + config.getMode()
                        + ", sourceResourceId="
                        + config.getSourceResourceId()
                        + ", targetResourceId="
                        + config.getTargetResourceId());
        Path tempDir = Files.createTempDirectory("transfer-" + context.getInstanceId() + "-");
        Exception primaryFailure = null;
        try (var budget = StagingBudget.open(config.getMaxStagingBytes())) {
            try (ResourceClientLease<FileClient> sourceLease =
                    resourceResolver.leaseFileClient(config.getSourceResourceId())) {
                context.registerResource(sourceLease);
                context.checkStop();
                FileClient sourceClient = sourceLease.getClient();
                // Keep every stage physically separate: output names may match unread inputs.
                Path sourceDir = Files.createDirectory(tempDir.resolve("source"));
                List<TransferFile> sourceFiles = read(sourceClient, config, context, sourceDir);
                context.logInfo("File transfer source read completed, files=" + sourceFiles.size());
                context.progress(35);

                context.checkStop();
                Path extractionDir = tempDir.resolve("extracted");
                Files.createDirectories(extractionDir);
                List<TransferFile> files =
                        archiveService.decompress(sourceFiles, config, context, extractionDir);
                ensureUniqueRelativePaths(files);
                context.progress(50);
                context.logInfo(
                        "File transfer decompression stage completed, files=" + files.size());

                context.checkStop();
                Path compressedDir = Files.createDirectory(tempDir.resolve("compressed"));
                files = archiveService.compress(files, config, context, compressedDir);
                ensureUniqueRelativePaths(files);
                context.progress(60);
                context.logInfo("File transfer compression stage completed, files=" + files.size());

                context.checkStop();
                WriteResult writeResult = WriteResult.none();
                if (config.getMode() == TransferMode.TRANSFER) {
                    writeResult = write(config, files, context);
                    context.logInfo(
                            "File transfer upload completed, uploaded="
                                    + writeResult.uploaded()
                                    + ", skipped="
                                    + writeResult.skipped());
                } else {
                    context.progress(90);
                }

                int deletedSourceFiles = 0;
                if (config.isDeleteSource()
                        && writeResult.allUploaded()
                        && (!files.isEmpty() || sourceFiles.isEmpty())) {
                    deletedSourceFiles = delete(sourceClient, sourceFiles, context);
                    context.logInfo(
                            "File transfer source cleanup completed, deleted="
                                    + deletedSourceFiles);
                }
                context.progress(95);
                return new FileTransferResult(
                        sourceFiles.size(),
                        files.size(),
                        writeResult.uploaded(),
                        writeResult.skipped(),
                        deletedSourceFiles);
            }
        } catch (Exception e) {
            primaryFailure = e;
            throw e;
        } finally {
            FileUtils.deleteQuietly(tempDir);
            if (Files.exists(tempDir)) {
                IOException cleanupFailure =
                        new IOException("File transfer temporary directory cleanup failed");
                if (primaryFailure != null) {
                    primaryFailure.addSuppressed(cleanupFailure);
                } else {
                    throw cleanupFailure;
                }
            }
        }
    }

    private List<TransferFile> read(
            FileClient client, FileTransferConfig config, TaskContext context, Path tempDir)
            throws Exception {
        String sourcePath = context.resolve(config.getSourcePath());
        List<TransferFile> result = new ArrayList<>();
        scanDirectory(
                client,
                sourcePath,
                "",
                config,
                tempDir,
                context,
                result,
                new TransferQuota(config),
                0);
        return result;
    }

    private WriteResult write(
            FileTransferConfig config, List<TransferFile> files, TaskContext context)
            throws Exception {
        if (files.isEmpty()) {
            context.progress(90);
            return WriteResult.none();
        }
        try (ResourceClientLease<FileClient> targetLease =
                resourceResolver.leaseFileClient(config.getTargetResourceId())) {
            context.registerResource(targetLease);
            context.checkStop();
            FileClient client = targetLease.getClient();
            String targetRoot = context.resolve(config.getTargetPath());
            int uploaded = 0;
            int skipped = 0;
            int index = 0;
            for (TransferFile file : files) {
                context.checkStop();
                if (upload(client, file, targetRoot, config, context)) {
                    uploaded++;
                } else {
                    skipped++;
                }
                index++;
                context.progress(60 + 30 * index / files.size());
            }
            return new WriteResult(uploaded, skipped);
        }
    }

    private void scanDirectory(
            FileClient client,
            String currentPath,
            String relativePath,
            FileTransferConfig config,
            Path tempDir,
            TaskContext context,
            List<TransferFile> result,
            TransferQuota quota,
            int depth)
            throws Exception {
        context.checkStop();
        quota.depth(depth);
        List<FileInfo> files = client.list(currentPath, quota.remainingEntries());
        if (files == null || files.isEmpty()) {
            return;
        }

        for (FileInfo fileInfo : files) {
            context.checkStop();
            quota.entry();

            if (fileInfo == null
                    || ".".equals(fileInfo.getName())
                    || "..".equals(fileInfo.getName())) {
                continue;
            }
            validateChildName(fileInfo.getName());
            if (com.ifelio.ijob.core.resource.factory.file.client.StagedFileClient.isTemporaryName(
                    fileInfo.getName())) continue;

            String remotePath = buildPath(currentPath, fileInfo.getName());
            String currentRelativePath = buildPath(relativePath, fileInfo.getName());

            if (fileInfo.isDirectory()) {
                if (config.isRecursive()) {
                    scanDirectory(
                            client,
                            remotePath,
                            currentRelativePath,
                            config,
                            tempDir,
                            context,
                            result,
                            quota,
                            depth + 1);
                }
                continue;
            }

            if (!matchPattern(fileInfo.getName(), config.getFilePattern())) {
                continue;
            }

            File localFile =
                    downloadFile(client, remotePath, currentRelativePath, tempDir, context, quota);

            TransferFile transferFile =
                    TransferFile.builder()
                            .sourcePath(remotePath)
                            .relativePath(currentRelativePath)
                            .fileName(fileInfo.getName())
                            .localFile(localFile)
                            .size(localFile.length())
                            .directory(false)
                            .lastModified(fileInfo.getLastModified())
                            .build();
            result.add(transferFile);
        }
    }

    private String buildPath(String parent, String child) {
        if (parent == null || parent.isBlank()) {
            return child;
        }
        if (parent.endsWith("/")) {
            return parent + child;
        }

        return parent + "/" + child;
    }

    private boolean matchPattern(String fileName, String pattern) {
        if (pattern == null || pattern.isBlank() || "*".equals(pattern)) {
            return true;
        }

        PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + pattern);
        return matcher.matches(Paths.get(fileName));
    }

    private File downloadFile(
            FileClient client,
            String remotePath,
            String relativePath,
            Path tempDir,
            TaskContext context,
            TransferQuota quota)
            throws IOException {
        Path localPath = tempDir.resolve(relativePath).normalize();
        /** Reject paths that escape the staging directory after normalization. */
        if (!localPath.startsWith(tempDir)) {
            throw new IOException("illegal file path:" + relativePath);
        }

        Files.createDirectories(localPath.getParent());
        try (InputStream in = client.download(remotePath);
                OutputStream out =
                        StagingBudget.newOutputStream(
                                localPath,
                                StandardOpenOption.CREATE,
                                StandardOpenOption.TRUNCATE_EXISTING)) {
            context.registerResource(in);
            context.registerResource(out);
            copy(in, out, context, quota);
        }

        return localPath.toFile();
    }

    private boolean upload(
            FileClient client,
            TransferFile file,
            String targetRoot,
            FileTransferConfig config,
            TaskContext context)
            throws IOException {
        String targetFile = buildPath(targetRoot, file.getRelativePath());

        String parent = getParentPath(targetFile);
        if (parent != null) {
            client.mkdirs(parent);
        }

        if (!config.isOverwrite() && client.exists(targetFile)) {
            log.info("file exists, skip upload, instanceId={}", context.getInstanceId());
            return false;
        }

        try (InputStream input =
                new BufferedInputStream(new FileInputStream(file.getLocalFile()))) {
            context.registerResource(input);
            context.checkStop();
            if (config.getRemotePublishMode() == RemotePublishMode.TEMP_RENAME) {
                if (!client.uploadPublished(
                        targetFile,
                        input,
                        file.getLocalFile().length(),
                        config.isOverwrite(),
                        context::checkExecution)) return false;
            } else {
                client.upload(targetFile, input, file.getLocalFile().length());
            }
            context.checkStop();
            log.info("upload success, instanceId={}", context.getInstanceId());
            return true;
        } catch (Exception exception) {
            log.error(
                    "upload failed, instanceId={}",
                    context.getInstanceId(),
                    com.ifelio.ijob.common.diagnostic.SafeDiagnostic.sanitized(exception));
            throw exception;
        }
    }

    private String getParentPath(String path) {
        if (path == null) {
            return null;
        }

        int index = path.lastIndexOf('/');
        if (index <= 0) {
            return null;
        }
        return path.substring(0, index);
    }

    private int delete(FileClient client, List<TransferFile> files, TaskContext context)
            throws IOException {
        int deleted = 0;
        for (TransferFile file : files) {
            context.checkStop();
            if (file.getSourcePath() == null || file.getSourcePath().isBlank()) {
                throw new IOException("Source path is missing");
            }
            client.delete(file.getSourcePath());
            deleted++;
        }
        return deleted;
    }

    private void ensureUniqueRelativePaths(List<TransferFile> files) throws IOException {
        Set<String> paths = new HashSet<>();
        for (TransferFile file : files) {
            if (file == null
                    || file.getRelativePath() == null
                    || file.getRelativePath().isBlank()) {
                throw new IOException("Transfer output path is missing");
            }
            String normalized = file.getRelativePath().replace('\\', '/');
            if (!paths.add(normalized)) {
                throw new IOException("Duplicate transfer output path:" + normalized);
            }
        }
    }

    private void validateChildName(String name) throws IOException {
        if (name == null
                || name.isBlank()
                || name.indexOf('/') >= 0
                || name.indexOf('\\') >= 0
                || name.endsWith(".")
                || name.endsWith(" ")
                || isReservedDeviceName(name)
                || containsNonPortableCharacter(name)) {
            throw new IOException("Invalid file listing entry");
        }
    }

    private boolean containsNonPortableCharacter(String name) {
        for (int index = 0; index < name.length(); index++) {
            char value = name.charAt(index);
            if (value < 32 || "<>:\"|?*".indexOf(value) >= 0) {
                return true;
            }
        }
        return false;
    }

    private boolean isReservedDeviceName(String name) {
        String base = name.split("\\.", 2)[0].toUpperCase(java.util.Locale.ROOT);
        return base.equals("CON")
                || base.equals("PRN")
                || base.equals("AUX")
                || base.equals("NUL")
                || base.matches("COM[1-9]")
                || base.matches("LPT[1-9]");
    }

    private record WriteResult(int uploaded, int skipped) {
        private static WriteResult none() {
            return new WriteResult(0, 0);
        }

        private boolean allUploaded() {
            return skipped == 0;
        }
    }

    private void copy(
            InputStream input, OutputStream output, TaskContext context, TransferQuota quota)
            throws IOException {
        byte[] buffer = new byte[8192];
        int length;
        long fileBytes = 0;
        while (true) {
            context.checkExecution();
            length = input.read(buffer);
            if (length < 0) {
                return;
            }
            fileBytes = quota.bytes(fileBytes, length);
            output.write(buffer, 0, length);
        }
    }
}
