package com.ifelio.ijob.plugin.filetransfer;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.hutool.json.JSONUtil;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ifelio.ijob.contract.enums.InstanceStatus;
import com.ifelio.ijob.contract.enums.PluginType;
import com.ifelio.ijob.core.execute.ExecutionHandle;
import com.ifelio.ijob.core.execute.task.TaskContext;
import com.ifelio.ijob.core.execute.task.TaskResult;
import com.ifelio.ijob.core.execute.variable.VariableResolver;
import com.ifelio.ijob.core.instance.InstanceLogService;
import com.ifelio.ijob.core.instance.InstanceService;
import com.ifelio.ijob.core.resource.ResourceClientLease;
import com.ifelio.ijob.core.resource.ResourceResolver;
import com.ifelio.ijob.core.resource.factory.file.client.FileClient;
import com.ifelio.ijob.core.resource.factory.file.client.FileInfo;
import com.ifelio.ijob.core.task.definition.TaskExecutionSpec;
import com.ifelio.ijob.plugin.common.config.PluginConfigCodec;
import com.ifelio.ijob.plugin.filetransfer.archive.ArchiveService;
import com.ifelio.ijob.plugin.filetransfer.archive.GzipArchiveHandler;
import com.ifelio.ijob.plugin.filetransfer.archive.ZipArchiveHandler;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

class FileTransferServiceTest {
    @Test
    void uncertainOrSkippedPublicationKeepsSourceAndNeverFallsBackToDirect() throws Exception {
        for (boolean uncertain : List.of(true, false)) {
            var source = sourceWithOneFile();
            var target =
                    new RecordingFileClient() {
                        @Override
                        public boolean uploadPublished(
                                String path,
                                InputStream input,
                                long length,
                                boolean overwrite,
                                Runnable beforePublish)
                                throws IOException {
                            if (uncertain)
                                throw new com.ifelio.ijob.core.resource.factory.file.client
                                        .FilePublicationUncertainException(
                                        new IOException("lost rename response"));
                            return false;
                        }

                        @Override
                        public void upload(String path, InputStream input) {
                            throw new AssertionError("No direct fallback");
                        }
                    };
            var config = base();
            config.setSourcePath("/in");
            config.setTargetPath("/out");
            config.setDeleteSource(true);
            config.setRemotePublishMode(RemotePublishMode.TEMP_RENAME);
            var service =
                    service(
                            new RecordingResolver(Map.of(1L, source, 2L, target)),
                            new RecordingArchiveService());
            if (uncertain)
                assertThrows(
                        com.ifelio.ijob.core.resource.factory.file.client
                                .FilePublicationUncertainException.class,
                        () -> service.transfer(config, context(97L, new ExecutionHandle(97L))));
            else
                assertEquals(
                        1,
                        service.transfer(config, context(97L, new ExecutionHandle(97L)))
                                .skippedFileCount());
            assertTrue(source.deleted.isEmpty());
        }
    }

    @Test
    void ownTemporaryFilesAreNeverReimported() throws Exception {
        var source = sourceWithOneFile();
        source.listings.put(
                "/in",
                List.of(
                        file("data.csv", 8),
                        file(".ijob-transfer-123e4567-e89b-12d3-a456-426614174000.part", 50)));
        var target = new RecordingFileClient();
        var config = base();
        config.setSourcePath("/in");
        config.setTargetPath("/out");
        service(
                        new RecordingResolver(Map.of(1L, source, 2L, target)),
                        new RecordingArchiveService())
                .transfer(config, context(97L, new ExecutionHandle(97L)));
        assertEquals(Set.of("/out/data.csv"), target.uploads.keySet());
    }

    @Test
    void gzipOutputsMustNotOverwriteUnreadSourcesInEitherOrder() throws Exception {
        for (boolean reverse : List.of(false, true)) {
            var source = new RecordingFileClient();
            source.listings.put(
                    "/in",
                    reverse
                            ? List.of(file("a.txt.gz", 9), file("a.txt", 5))
                            : List.of(file("a.txt", 5), file("a.txt.gz", 9)));
            source.downloads.put("/in/a.txt", bytes("first"));
            source.downloads.put("/in/a.txt.gz", bytes("untouched"));
            var target = new RecordingFileClient();
            var resolver = new RecordingResolver(Map.of(1L, source, 2L, target));
            var config = base();
            config.setSourcePath("/in");
            config.setTargetPath("/out");
            config.setCompressType(CompressType.GZIP);
            config.setOverwrite(false);
            config.setDeleteSource(true);
            var result =
                    service(resolver, new ArchiveService(List.of(new GzipArchiveHandler())))
                            .transfer(config, context(91L, new ExecutionHandle(91L)));
            try (var first =
                            new GZIPInputStream(
                                    new ByteArrayInputStream(target.uploads.get("/out/a.txt.gz")));
                    var second =
                            new GZIPInputStream(
                                    new ByteArrayInputStream(
                                            target.uploads.get("/out/a.txt.gz.gz")))) {
                assertArrayEquals(bytes("first"), first.readAllBytes());
                assertArrayEquals(bytes("untouched"), second.readAllBytes());
            }
            assertEquals(2, result.deletedSourceFileCount());
            assertEquals(Set.of("/in/a.txt", "/in/a.txt.gz"), new HashSet<>(source.deleted));
        }
    }

    @Test
    void archiveNameMatchingInputMustPreserveOriginalBytes() throws Exception {
        var source = new RecordingFileClient();
        source.listings.put("/in", List.of(file("bundle.zip", 8)));
        source.downloads.put("/in/bundle.zip", bytes("original"));
        var target = new RecordingFileClient();
        var config = base();
        config.setSourcePath("/in");
        config.setTargetPath("/out");
        config.setCompressType(CompressType.ZIP);
        config.setArchiveName("bundle.zip");
        config.setMaxStagingBytes(4096);
        config.setDeleteSource(true);
        service(
                        new RecordingResolver(Map.of(1L, source, 2L, target)),
                        new ArchiveService(List.of(new ZipArchiveHandler())))
                .transfer(config, context(92L, new ExecutionHandle(92L)));
        try (var archive =
                new ZipInputStream(
                        new ByteArrayInputStream(target.uploads.get("/out/bundle.zip")))) {
            assertEquals("bundle.zip", archive.getNextEntry().getName());
            assertArrayEquals(bytes("original"), archive.readAllBytes());
        }
        assertEquals(List.of("/in/bundle.zip"), source.deleted);
    }

    @Test
    void rawDownloadBudgetUsesActualBytesAndCleansPartialStaging() throws Exception {
        RecordingFileClient source = new RecordingFileClient();
        source.listings.put("/in", List.of(file("data.csv", 0)));
        source.downloads.put("/in/data.csv", bytes("12345"));
        var target = new RecordingFileClient();
        var resolver = new RecordingResolver(Map.of(1L, source, 2L, target));
        var config = base();
        config.setSourcePath("/in");
        config.setTargetPath("/out");
        config.setMaxSourceFileBytes(4);
        config.setMaxSourceTotalBytes(4);
        var before = transferTempDirs(88L);
        assertThrows(
                IOException.class,
                () ->
                        service(resolver, new RecordingArchiveService())
                                .transfer(config, context(88L, new ExecutionHandle(88L))));
        assertEquals(before, transferTempDirs(88L));
        assertTrue(target.uploads.isEmpty());
        assertEquals(1, source.closeCount);
    }

    @Test
    void recursiveScanStopsAtDepthLimitEvenWithoutMatchingFiles() throws Exception {
        var source = new RecordingFileClient();
        source.listings.put("/in", List.of(directory("a")));
        source.listings.put("/in/a", List.of(directory("b")));
        var config = base();
        config.setSourcePath("/in");
        config.setTargetPath("/out");
        config.setRecursive(true);
        config.setMaxScanDepth(1);
        var before = transferTempDirs(89L);
        assertThrows(
                IOException.class,
                () ->
                        service(
                                        new RecordingResolver(Map.of(1L, source)),
                                        new RecordingArchiveService())
                                .transfer(config, context(89L, new ExecutionHandle(89L))));
        assertEquals(before, transferTempDirs(89L));
        assertEquals(1, source.closeCount);
    }

    private final List<ExecutorService> executors = new ArrayList<>();

    @AfterEach
    void cleanup() {
        executors.forEach(ExecutorService::shutdownNow);
    }

    @Test
    void recursiveTransferShouldFilterResolveUploadAndReleaseLeases() throws Exception {
        RecordingFileClient source = new RecordingFileClient();
        source.listings.put("/in", List.of(directory("sub"), file("ignore.txt", 3)));
        source.listings.put("/in/sub", List.of(file("data.csv", 8)));
        source.downloads.put("/in/sub/data.csv", bytes("accepted"));
        RecordingFileClient target = new RecordingFileClient();
        RecordingResolver resolver = new RecordingResolver(Map.of(1L, source, 2L, target));
        RecordingArchiveService archive = new RecordingArchiveService();
        FileTransferConfig config = base();
        config.setSourcePath("/${source}");
        config.setTargetPath("/${target}");
        config.setRecursive(true);
        config.setFilePattern("*.csv");
        TaskContext context = context(31L, new ExecutionHandle(31L));
        context.putVariable("source", "in");
        context.putVariable("target", "out");

        service(resolver, archive).transfer(config, context);

        assertAll(
                () -> assertArrayEquals(bytes("accepted"), target.uploads.get("/out/sub/data.csv")),
                () -> assertEquals(List.of("/out/sub"), target.createdDirs),
                () -> assertEquals(1, source.closeCount),
                () -> assertEquals(1, target.closeCount),
                () -> assertEquals(2, resolver.leaseCount),
                () -> assertFalse(Files.exists(archive.tempDir)),
                () ->
                        assertEquals(
                                List.of(5, 35, 50, 60, 90, 95),
                                ((RecordingInstanceService) context.getInstanceService())
                                        .progressValues));
    }

    @Test
    void skippedOverwriteShouldNotDeleteSource() throws Exception {
        RecordingFileClient source = sourceWithOneFile();
        RecordingFileClient target = new RecordingFileClient();
        target.existing.add("/out/data.csv");
        RecordingResolver resolver = new RecordingResolver(Map.of(1L, source, 2L, target));
        FileTransferConfig config = base();
        config.setSourcePath("/in");
        config.setTargetPath("/out");
        config.setOverwrite(false);
        config.setDeleteSource(true);

        service(resolver, new RecordingArchiveService())
                .transfer(config, context(32L, new ExecutionHandle(32L)));

        assertTrue(target.uploads.isEmpty());
        assertTrue(source.deleted.isEmpty());
    }

    @Test
    void compressedTransferShouldDeleteOriginalSourceOnlyAfterUpload() throws Exception {
        RecordingFileClient source = sourceWithOneFile();
        RecordingFileClient target = new RecordingFileClient();
        RecordingArchiveService archive = new RecordingArchiveService();
        archive.bundle = true;
        RecordingResolver resolver = new RecordingResolver(Map.of(1L, source, 2L, target));
        FileTransferConfig config = base();
        config.setSourcePath("/in");
        config.setTargetPath("/out");
        config.setDeleteSource(true);
        config.setCompressType(CompressType.ZIP);

        service(resolver, archive).transfer(config, context(33L, new ExecutionHandle(33L)));

        assertArrayEquals(bytes("bundle"), target.uploads.get("/out/bundle.zip"));
        assertEquals(List.of("/in/data.csv"), source.deleted);
        assertFalse(source.deleted.contains(null));
    }

    @Test
    void stopShouldCloseBlockingStreamLeaseAndRemoveTempDirectory() throws Exception {
        BlockingFileClient source = new BlockingFileClient();
        source.listings.put("/in", List.of(file("data.csv", 8)));
        RecordingFileClient target = new RecordingFileClient();
        RecordingResolver resolver = new RecordingResolver(Map.of(1L, source, 2L, target));
        FileTransferTaskRunner runner = runner(resolver, new RecordingArchiveService());
        FileTransferConfig config = base();
        config.setSourcePath("/in");
        config.setTargetPath("/out");
        TaskExecutionSpec spec = spec(config);
        ExecutionHandle handle = new ExecutionHandle(34L);
        TaskContext context = context(34L, handle);
        handle.bind(runner, spec, context);
        Set<Path> before = transferTempDirs(34L);
        ExecutorService executor = register(Executors.newSingleThreadExecutor());

        Future<TaskResult> future = executor.submit(() -> runner.run(spec, context));
        assertTrue(source.started.await(2, TimeUnit.SECONDS));
        handle.requestStop();
        TaskResult result = future.get(2, TimeUnit.SECONDS);

        assertAll(
                () -> assertEquals(InstanceStatus.STOPPED, result.getStatus()),
                () -> assertTrue(source.streamClosed.get()),
                () -> assertEquals(1, source.closeCount),
                () -> assertEquals(before, transferTempDirs(34L)),
                () -> assertEquals(1, resolver.leaseCount));
    }

    @Test
    void invalidConfigurationShouldFailBeforeLeasingResource() {
        RecordingResolver resolver = new RecordingResolver(Map.of());
        FileTransferTaskRunner runner = runner(resolver, new RecordingArchiveService());
        FileTransferConfig config = base();
        config.setMode(TransferMode.WRITE_ONLY);
        TaskContext context = context(35L, new ExecutionHandle(35L));

        TaskResult result = runner.run(spec(config), context);

        assertEquals(InstanceStatus.FAILED, result.getStatus());
        assertEquals(0, resolver.leaseCount);
        assertFalse(context.containsVariable("fileTransfer.sourceFileCount"));
    }

    @Test
    void maliciousListingNameShouldFailBeforeDownloadOrTargetLease() {
        RecordingFileClient source = new RecordingFileClient();
        source.listings.put("/in", List.of(file("../escape.txt", 1)));
        RecordingFileClient target = new RecordingFileClient();
        RecordingResolver resolver = new RecordingResolver(Map.of(1L, source, 2L, target));
        FileTransferConfig config = base();
        config.setSourcePath("/in");
        config.setTargetPath("/out");

        assertThrows(
                IOException.class,
                () ->
                        service(resolver, new RecordingArchiveService())
                                .transfer(config, context(36L, new ExecutionHandle(36L))));
        assertEquals(1, resolver.leaseCount);
        assertTrue(target.uploads.isEmpty());
        assertEquals(1, source.closeCount);
    }

    @Test
    void mixedZipAndPlainFilesShouldPreservePlainAndUploadExtractedEntries() throws Exception {
        RecordingFileClient source = new RecordingFileClient();
        source.listings.put("/in", List.of(file("plain.csv", 5), file("bundle.zip", 20)));
        source.downloads.put("/in/plain.csv", bytes("plain"));
        source.downloads.put("/in/bundle.zip", zip("nested/data.csv", "archived"));
        RecordingFileClient target = new RecordingFileClient();
        RecordingResolver resolver = new RecordingResolver(Map.of(1L, source, 2L, target));
        FileTransferConfig config = base();
        config.setSourcePath("/in");
        config.setTargetPath("/out");
        config.setDecompressType(CompressType.ZIP);

        FileTransferResult result =
                service(resolver, new ArchiveService(List.of(new ZipArchiveHandler())))
                        .transfer(config, context(37L, new ExecutionHandle(37L)));

        assertAll(
                () -> assertArrayEquals(bytes("plain"), target.uploads.get("/out/plain.csv")),
                () ->
                        assertArrayEquals(
                                bytes("archived"), target.uploads.get("/out/nested/data.csv")),
                () -> assertFalse(target.uploads.containsKey("/out/bundle.zip")),
                () -> assertEquals(2, result.sourceFileCount()),
                () -> assertEquals(2, result.outputFileCount()),
                () -> assertEquals(2, result.uploadedFileCount()));
    }

    @Test
    void archiveOutputCollidingWithPlainFileShouldFailBeforeTargetLease() throws Exception {
        RecordingFileClient source = new RecordingFileClient();
        source.listings.put("/in", List.of(file("data.csv", 5), file("bundle.zip", 20)));
        source.downloads.put("/in/data.csv", bytes("plain"));
        source.downloads.put("/in/bundle.zip", zip("data.csv", "archived"));
        RecordingFileClient target = new RecordingFileClient();
        RecordingResolver resolver = new RecordingResolver(Map.of(1L, source, 2L, target));
        FileTransferConfig config = base();
        config.setSourcePath("/in");
        config.setTargetPath("/out");
        config.setDecompressType(CompressType.ZIP);

        IOException exception =
                assertThrows(
                        IOException.class,
                        () ->
                                service(
                                                resolver,
                                                new ArchiveService(
                                                        List.of(new ZipArchiveHandler())))
                                        .transfer(config, context(38L, new ExecutionHandle(38L))));

        assertEquals("Duplicate transfer output path:data.csv", exception.getMessage());
        assertEquals(1, resolver.leaseCount);
        assertTrue(target.uploads.isEmpty());
    }

    @Test
    void successfulRunnerShouldPublishSummaryOnlyAfterTransferCompletes() {
        RecordingFileClient source = sourceWithOneFile();
        RecordingFileClient target = new RecordingFileClient();
        RecordingResolver resolver = new RecordingResolver(Map.of(1L, source, 2L, target));
        FileTransferConfig config = base();
        config.setSourcePath("/in");
        config.setTargetPath("/out");
        TaskContext context = context(39L, new ExecutionHandle(39L));

        TaskResult result =
                runner(resolver, new RecordingArchiveService()).run(spec(config), context);

        assertAll(
                () -> assertEquals(InstanceStatus.SUCCESS, result.getStatus()),
                () -> assertEquals(1, context.getVariable("fileTransfer.sourceFileCount")),
                () -> assertEquals(1, context.getVariable("fileTransfer.outputFileCount")),
                () -> assertEquals(1, context.getVariable("fileTransfer.uploadedFileCount")),
                () -> assertEquals(0, context.getVariable("fileTransfer.skippedFileCount")),
                () -> assertTrue(((Long) context.getVariable("fileTransfer.elapsedMillis")) >= 0));
    }

    @Test
    void reservedWindowsDeviceNameShouldFailBeforeLocalFileCreation() {
        RecordingFileClient source = new RecordingFileClient();
        source.listings.put("/in", List.of(file("NUL.txt", 1)));
        RecordingResolver resolver = new RecordingResolver(Map.of(1L, source));
        FileTransferConfig config = base();
        config.setSourcePath("/in");
        config.setTargetPath("/out");

        assertThrows(
                IOException.class,
                () ->
                        service(resolver, new RecordingArchiveService())
                                .transfer(config, context(40L, new ExecutionHandle(40L))));
        assertTrue(source.downloads.isEmpty());
        assertEquals(1, resolver.leaseCount);
    }

    @Test
    void resolvedControlCharacterInPathShouldFailBeforeResourceLease() {
        RecordingResolver resolver = new RecordingResolver(Map.of());
        FileTransferConfig config = base();
        config.setSourcePath("/${source}");
        config.setTargetPath("/out");
        TaskContext context = context(41L, new ExecutionHandle(41L));
        context.putVariable("source", "bad\npath");

        TaskResult result =
                runner(resolver, new RecordingArchiveService()).run(spec(config), context);

        assertEquals(InstanceStatus.FAILED, result.getStatus());
        assertEquals(0, resolver.leaseCount);
        assertFalse(context.containsVariable("fileTransfer.sourceFileCount"));
    }

    private FileTransferService service(ResourceResolver resolver, ArchiveService archiveService) {
        return new FileTransferService(resolver, archiveService);
    }

    private FileTransferTaskRunner runner(
            ResourceResolver resolver, ArchiveService archiveService) {
        return new FileTransferTaskRunner(
                service(resolver, archiveService),
                new FileTransferTaskConfigValidator(),
                new PluginConfigCodec(new ObjectMapper()));
    }

    private FileTransferConfig base() {
        return FileTransferConfig.builder().sourceResourceId(1L).targetResourceId(2L).build();
    }

    private TaskExecutionSpec spec(FileTransferConfig config) {
        return new TaskExecutionSpec(
                "file-transfer",
                PluginType.FILE_TRANSFER,
                JSONUtil.toJsonStr(config),
                0,
                0,
                30,
                true,
                null);
    }

    private TaskContext context(long id, ExecutionHandle handle) {
        return new TaskContext(
                id,
                handle,
                new RecordingInstanceService(),
                new RecordingLogService(),
                new VariableResolver());
    }

    private RecordingFileClient sourceWithOneFile() {
        RecordingFileClient source = new RecordingFileClient();
        source.listings.put("/in", List.of(file("data.csv", 8)));
        source.downloads.put("/in/data.csv", bytes("accepted"));
        return source;
    }

    private FileInfo file(String name, long size) {
        return FileInfo.builder().name(name).size(size).directory(false).build();
    }

    private FileInfo directory(String name) {
        return FileInfo.builder().name(name).directory(true).build();
    }

    private byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private byte[] zip(String name, String content) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            zip.putNextEntry(new ZipEntry(name));
            zip.write(content.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return bytes.toByteArray();
    }

    private Set<Path> transferTempDirs(long instanceId) throws IOException {
        Path root = Path.of(System.getProperty("java.io.tmpdir"));
        Set<Path> result = new HashSet<>();
        try (var paths = Files.list(root)) {
            paths.filter(
                            path ->
                                    path.getFileName()
                                            .toString()
                                            .startsWith("transfer-" + instanceId + "-"))
                    .map(Path::toAbsolutePath)
                    .forEach(result::add);
        }
        return result;
    }

    private <T extends ExecutorService> T register(T executor) {
        executors.add(executor);
        return executor;
    }

    private static class RecordingFileClient implements FileClient {
        protected final Map<String, List<FileInfo>> listings = new HashMap<>();
        protected final Map<String, byte[]> downloads = new HashMap<>();
        protected final Map<String, byte[]> uploads = new LinkedHashMap<>();
        protected final Set<String> existing = new HashSet<>();
        protected final List<String> createdDirs = new ArrayList<>();
        protected final List<String> deleted = new ArrayList<>();
        protected int closeCount;

        @Override
        public List<FileInfo> list(String path) {
            return listings.getOrDefault(path, List.of());
        }

        @Override
        public InputStream download(String path) throws IOException {
            byte[] content = downloads.get(path);
            if (content == null) {
                throw new IOException("missing fake file");
            }
            return new ByteArrayInputStream(content);
        }

        @Override
        public void upload(String path, InputStream input) throws IOException {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            input.transferTo(output);
            uploads.put(path, output.toByteArray());
        }

        @Override
        public void mkdirs(String path) {
            createdDirs.add(path);
        }

        @Override
        public boolean exists(String path) {
            return existing.contains(path);
        }

        @Override
        public void delete(String path) {
            deleted.add(path);
        }

        @Override
        public void close() {
            closeCount++;
        }
    }

    private static final class BlockingFileClient extends RecordingFileClient {
        private final CountDownLatch started = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);
        private final AtomicBoolean streamClosed = new AtomicBoolean();

        @Override
        public InputStream download(String path) {
            return new InputStream() {
                @Override
                public int read() throws IOException {
                    byte[] single = new byte[1];
                    int count = read(single, 0, 1);
                    return count < 0 ? -1 : single[0];
                }

                @Override
                public int read(byte[] bytes, int offset, int length) throws IOException {
                    started.countDown();
                    try {
                        release.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new IOException("interrupted", e);
                    }
                    if (streamClosed.get()) {
                        throw new IOException("closed");
                    }
                    return -1;
                }

                @Override
                public void close() {
                    if (streamClosed.compareAndSet(false, true)) {
                        release.countDown();
                    }
                }
            };
        }

        @Override
        public void close() {
            super.close();
            streamClosed.set(true);
            release.countDown();
        }
    }

    private static final class RecordingResolver extends ResourceResolver {
        private final Map<Long, FileClient> clients;
        private int leaseCount;

        private RecordingResolver(Map<Long, FileClient> clients) {
            super(null);
            this.clients = clients;
        }

        @Override
        public ResourceClientLease<FileClient> leaseFileClient(Long resourceId) {
            leaseCount++;
            FileClient client = clients.get(resourceId);
            if (client == null) {
                throw new IllegalArgumentException("missing fake resource");
            }
            return new ResourceClientLease<>(client, client::close);
        }
    }

    private static final class RecordingArchiveService extends ArchiveService {
        private Path tempDir;
        private boolean bundle;

        private RecordingArchiveService() {
            super(List.of());
        }

        @Override
        public List<TransferFile> decompress(
                List<TransferFile> files,
                FileTransferConfig config,
                TaskContext context,
                Path tempDir) {
            this.tempDir = tempDir;
            return files;
        }

        @Override
        public List<TransferFile> compress(
                List<TransferFile> files,
                FileTransferConfig config,
                TaskContext context,
                Path tempDir)
                throws IOException {
            this.tempDir = tempDir;
            if (!bundle) {
                return files;
            }
            Path archive = tempDir.resolve("bundle.zip");
            Files.write(archive, "bundle".getBytes(StandardCharsets.UTF_8));
            return List.of(
                    TransferFile.builder()
                            .relativePath("bundle.zip")
                            .localFile(archive.toFile())
                            .build());
        }
    }

    private static final class RecordingLogService extends InstanceLogService {
        private RecordingLogService() {
            super(null);
        }

        @Override
        public void info(Long instanceId, String content) {}

        @Override
        public void error(Long instanceId, String content) {}
    }

    private static final class RecordingInstanceService extends InstanceService {
        private final List<Integer> progressValues = new ArrayList<>();

        private RecordingInstanceService() {
            super(null, null, null, null);
        }

        @Override
        public void updateProgress(Long instanceId, Integer progress) {
            progressValues.add(progress);
        }
    }
}
