package com.ifelio.ijob.plugin.localjar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ifelio.ijob.contract.enums.InstanceStatus;
import com.ifelio.ijob.contract.enums.PluginType;
import com.ifelio.ijob.core.execute.ExecutionHandle;
import com.ifelio.ijob.core.execute.task.TaskContext;
import com.ifelio.ijob.core.execute.task.TaskResult;
import com.ifelio.ijob.core.execute.variable.VariableResolver;
import com.ifelio.ijob.core.task.definition.TaskExecutionSpec;
import com.ifelio.ijob.plugin.common.config.PluginConfigCodec;
import com.ifelio.ijob.plugin.support.RecordingInstanceLogs;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import sample.localjar.GoodLocalJarTask;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

class LocalJarTaskRunnerTest {
    private static final String ENTRY_CLASS = "sample.localjar.GoodLocalJarTask";
    private final ObjectMapper objectMapper = new ObjectMapper();

    @TempDir Path root;

    @Test
    void localJarShouldRejectUnknownRootEvenWithPermissiveApplicationMapper() throws Exception {
        objectMapper.disable(
                com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        Path jar = createJar("unknown.jar", "2", ENTRY_CLASS);
        Components components = components();
        String valid = draft(jar, ENTRY_CLASS, "{\"futureBusinessField\":false}");
        try {
            assertThrows(
                    com.ifelio.ijob.common.exception.BusinessException.class,
                    () ->
                            components.snapshotter.snapshot(
                                    "{\"futureRootField\":0," + valid.substring(1)));
            String snapshot = components.snapshotter.snapshot(valid);
            assertTrue(snapshot.contains("\"futureBusinessField\":false"));
            assertEquals(
                    InstanceStatus.FAILED,
                    components
                            .runner
                            .run(
                                    spec("{\"futureRootField\":0," + snapshot.substring(1)),
                                    context(402L))
                            .getStatus());
        } finally {
            components.cache.close();
        }
    }

    @Test
    void businessNumbersShouldRemainExactAcrossPublicationAndExecution() throws Exception {
        Path jar = createJar("precision.jar", "2", ENTRY_CLASS);
        Components components = components();
        String userConfig =
                "{\"amount\":9007199254740993.123456789,\"nested\":[0.1234567890123456789,9223372036854775808123]}";
        try {
            String snapshot = components.snapshotter.snapshot(draft(jar, ENTRY_CLASS, userConfig));
            assertTrue(snapshot.contains(userConfig));
            TaskContext context = context(401L);
            assertEquals(
                    InstanceStatus.SUCCESS,
                    components.runner.run(spec(snapshot), context).getStatus());
            assertEquals(userConfig, context.getVariable("external.configuration"));
        } finally {
            components.cache.close();
        }
    }

    @Test
    void publicationShouldRejectAmbiguousJsonAndUnsupportedFormat() throws Exception {
        Path jar = createJar("strict.jar", "2", ENTRY_CLASS);
        Components components = components();
        String valid = draft(jar, ENTRY_CLASS, "{}");
        try {
            for (String json :
                    List.of(
                            valid + " {}",
                            valid.replace("\"userConfig\":{}", "\"userConfig\":{\"x\":1,\"x\":2}"),
                            valid.replace(
                                    "\"userConfig\":{}", "\"userConfig\":{},\"userConfig\":null"),
                            "{\"formatVersion\":2," + valid.substring(1))) {
                assertThrows(
                        com.ifelio.ijob.common.exception.BusinessException.class,
                        () -> components.snapshotter.snapshot(json));
            }
        } finally {
            components.cache.close();
        }
    }

    @Test
    void codecShouldRejectFractionalSnapshotIntegers() {
        PluginConfigCodec codec = new PluginConfigCodec(objectMapper);
        for (String json : List.of("{\"formatVersion\":1.5}", "{\"jarSize\":12.5}")) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> codec.read(json, LocalJarTaskConfig.class, "Local JAR"));
        }
    }

    @Test
    void businessConfigurationShouldSupportAllJsonRootKinds() throws Exception {
        Path jar = createJar("roots.jar", "2", ENTRY_CLASS);
        Components components = components();
        try {
            for (String userConfig :
                    List.of("null", "true", "\"literal ${name}\"", "42", "[]", "{}")) {
                String snapshot =
                        components.snapshotter.snapshot(draft(jar, ENTRY_CLASS, userConfig));
                assertEquals(
                        objectMapper.readTree(userConfig),
                        objectMapper.readTree(snapshot).get("userConfig"));
            }
        } finally {
            components.cache.close();
        }
    }

    @Test
    void snapshotShouldCaptureArtifactAndRunnerShouldExecuteIt() throws Exception {
        Path jar = createJar("extension.jar", "2", ENTRY_CLASS);
        Components components = components();
        String snapshot =
                components.snapshotter.snapshot(draft(jar, ENTRY_CLASS, "{\"value\":\"ready\"}"));
        JsonNode captured = objectMapper.readTree(snapshot);

        assertEquals(Files.size(jar), captured.path("jarSize").asLong());
        assertEquals("1.0.0", captured.path("implementationVersion").asText());
        assertEquals("2", captured.path("spiVersion").asText());
        assertEquals(sha256(jar), captured.path("sha256").asText());

        TaskContext context = context(41L);
        TaskResult result = components.runner.run(spec(snapshot), context);

        assertEquals(InstanceStatus.SUCCESS, result.getStatus());
        assertEquals("external success", result.getMessage());
        assertEquals("{\"value\":\"ready\"}", context.getVariable("external.configuration"));
        components.cache.close();
    }

    @Test
    void runnerShouldRejectArtifactChangedAfterPublication() throws Exception {
        Path jar = createJar("extension.jar", "2", ENTRY_CLASS);
        Components components = components();
        String snapshot = components.snapshotter.snapshot(draft(jar, ENTRY_CLASS, "{}"));
        Files.write(jar, new byte[] {1}, StandardOpenOption.APPEND);

        TaskResult result = components.runner.run(spec(snapshot), context(42L));

        assertEquals(InstanceStatus.FAILED, result.getStatus());
        assertTrue(result.getMessage().contains("SHA-256"));
        components.cache.close();
    }

    @Test
    void externalFailureShouldReturnNonRetryableResult() throws Exception {
        Path jar = createJar("failing.jar", "2", ENTRY_CLASS);
        Components components = components();
        String snapshot =
                components.snapshotter.snapshot(draft(jar, ENTRY_CLASS, "{\"fail\":true}"));

        TaskResult result = components.runner.run(spec(snapshot), context(45L));

        assertEquals(InstanceStatus.FAILED, result.getStatus());
        assertEquals(false, result.isRetryableFailure());
        assertEquals("Local JAR task execution failed", result.getMessage());
        components.cache.close();
    }

    @Test
    void validatorShouldRejectTraversalAndUnsupportedSpi() throws Exception {
        Components components = components();
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        components.validator.validate(
                                "../extension.jar", "0".repeat(64), ENTRY_CLASS));

        Path jar = createJar("future.jar", "3", ENTRY_CLASS);
        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                components.validator.validate(
                                        jar.getFileName().toString(), sha256(jar), ENTRY_CLASS));

        assertTrue(exception.getMessage().contains("unsupported"));
        components.cache.close();
    }

    @Test
    void validatorShouldRejectLegacySpiAndReservedEntryPackages() throws Exception {
        Components components = components();
        try {
            Path legacy = createJar("legacy.jar", "1", ENTRY_CLASS);
            IllegalArgumentException failure =
                    assertThrows(
                            IllegalArgumentException.class,
                            () ->
                                    components.validator.validate(
                                            "legacy.jar", sha256(legacy), ENTRY_CLASS));
            assertTrue(failure.getMessage().contains("SPI version is unsupported"));
            for (String prefix : List.of("com.ifeel.ijob.", "com.ifelio.ijob.")) {
                IllegalArgumentException reserved =
                        assertThrows(
                                IllegalArgumentException.class,
                                () ->
                                        components.validator.validate(
                                                "legacy.jar",
                                                sha256(legacy),
                                                prefix + "Extension"));
                assertTrue(reserved.getMessage().contains("entry class is invalid"));
            }
        } finally {
            components.cache.close();
        }
    }

    @Test
    void stopShouldReachExternalHook() throws Exception {
        Path jar = createJar("blocking.jar", "2", ENTRY_CLASS);
        Components components = components();
        String snapshot =
                components.snapshotter.snapshot(draft(jar, ENTRY_CLASS, "{\"block\":true}"));
        TaskExecutionSpec spec = spec(snapshot);
        TaskContext context =
                new TaskContext(
                        43L,
                        new ExecutionHandle(43L),
                        null,
                        new RecordingInstanceLogs(),
                        new VariableResolver());

        try (ExecutorService executor = Executors.newSingleThreadExecutor()) {
            Future<TaskResult> running =
                    executor.submit(() -> components.runner.run(spec, context));
            waitForVariable(context, "external.configuration");
            components.runner.stop(spec, context);

            TaskResult result = running.get(2, TimeUnit.SECONDS);
            assertEquals(InstanceStatus.STOPPED, result.getStatus());
            assertEquals("stopped by hook", result.getMessage());
        } finally {
            components.cache.close();
        }
    }

    @Test
    void stopHookShouldRunBeforeRegisteredResourcesAreClosed() throws Exception {
        Path jar = createJar("resource.jar", "2", ENTRY_CLASS);
        Components components = components();
        String snapshot =
                components.snapshotter.snapshot(
                        draft(jar, ENTRY_CLASS, "{\"block\":true,\"resource\":true}"));
        TaskExecutionSpec spec = spec(snapshot);
        TaskContext context = context(44L);

        try (ExecutorService executor = Executors.newSingleThreadExecutor()) {
            Future<TaskResult> running =
                    executor.submit(() -> components.runner.run(spec, context));
            waitForVariable(context, "external.resourceRegistered");
            components.runner.stop(spec, context);

            assertEquals(InstanceStatus.STOPPED, running.get(2, TimeUnit.SECONDS).getStatus());
            assertEquals(false, context.getVariable("external.resourceClosedBeforeStop"));
            assertEquals(true, context.getVariable("external.resourceClosed"));
        } finally {
            components.cache.close();
        }
    }

    @Test
    void concurrentCacheAcquiresShouldShareOneLoaderPerFingerprint() throws Exception {
        Path jar = createJar("concurrent.jar", "2", ENTRY_CLASS);
        Components components = components();
        LocalJarArtifact artifact =
                components.validator.validate(
                        jar.getFileName().toString(), sha256(jar), ENTRY_CLASS);
        int concurrency = 8;
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch acquired = new CountDownLatch(concurrency);
        CountDownLatch release = new CountDownLatch(1);
        List<Future<LocalJarClassLoader>> futures = new ArrayList<>();

        try (ExecutorService executor = Executors.newFixedThreadPool(concurrency)) {
            for (int index = 0; index < concurrency; index++) {
                futures.add(
                        executor.submit(
                                () -> {
                                    start.await();
                                    try (LocalJarClassLoaderCache.Lease lease =
                                            components.cache.acquire(artifact)) {
                                        acquired.countDown();
                                        release.await();
                                        return lease.loader();
                                    }
                                }));
            }
            start.countDown();
            assertTrue(acquired.await(2, TimeUnit.SECONDS));
            release.countDown();
            LocalJarClassLoader first = futures.getFirst().get(2, TimeUnit.SECONDS);
            for (Future<LocalJarClassLoader> future : futures) {
                assertSame(first, future.get(2, TimeUnit.SECONDS));
            }
        } finally {
            release.countDown();
            components.cache.close();
        }
    }

    @Test
    void validatorShouldInspectOneImmutableCopy() throws Exception {
        Path jar = createJar("immutable.jar", "2", ENTRY_CLASS);
        Components components = components();
        String publishedSha = sha256(jar);
        AtomicInteger checks = new AtomicInteger();

        LocalJarArtifact artifact =
                components.validator.validate(
                        jar.getFileName().toString(),
                        publishedSha,
                        ENTRY_CLASS,
                        () -> {
                            if (checks.incrementAndGet() == 3) {
                                try {
                                    Files.write(jar, new byte[] {1}, StandardOpenOption.APPEND);
                                } catch (IOException exception) {
                                    throw new IllegalStateException(exception);
                                }
                            }
                        });

        assertEquals(publishedSha, artifact.sha256());
        assertNotEquals(publishedSha, sha256(jar));
        components.cache.close();
    }

    private Components components() {
        LocalJarPluginProperties properties = new LocalJarPluginProperties();
        properties.setEnabled(true);
        properties.setRootDirectory(root);
        LocalJarArtifactValidator validator = new LocalJarArtifactValidator(properties);
        LocalJarClassLoaderCache cache = new LocalJarClassLoaderCache(properties);
        PluginConfigCodec codec = new PluginConfigCodec(objectMapper);
        return new Components(
                validator,
                cache,
                new LocalJarConfigSnapshotter(codec, validator),
                new LocalJarTaskRunner(codec, validator, cache));
    }

    private String draft(Path jar, String entryClass, String userConfig) throws Exception {
        return "{\"jarFile\":\""
                + jar.getFileName()
                + "\",\"sha256\":\""
                + sha256(jar)
                + "\",\"entryClass\":\""
                + entryClass
                + "\",\"userConfig\":"
                + userConfig
                + "}";
    }

    private TaskExecutionSpec spec(String snapshot) {
        return new TaskExecutionSpec(
                "local extension", PluginType.LOCAL_JAR, snapshot, 0, 0, 5, true, null);
    }

    private TaskContext context(long instanceId) {
        return new TaskContext(
                instanceId,
                new ExecutionHandle(instanceId),
                null,
                new RecordingInstanceLogs(),
                new VariableResolver());
    }

    private Path createJar(String fileName, String spiVersion, String entryClass) throws Exception {
        Manifest manifest = new Manifest();
        Attributes attributes = manifest.getMainAttributes();
        attributes.put(Attributes.Name.MANIFEST_VERSION, "1.0");
        attributes.put(Attributes.Name.IMPLEMENTATION_VERSION, "1.0.0");
        attributes.putValue("IJob-Plugin-Api-Version", spiVersion);
        attributes.putValue("IJob-Plugin-Class", entryClass);
        Path jar = root.resolve(fileName);
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(jar), manifest);
                InputStream input =
                        GoodLocalJarTask.class.getResourceAsStream(
                                "/sample/localjar/GoodLocalJarTask.class")) {
            if (input == null) {
                throw new IllegalStateException("test entry class missing");
            }
            output.putNextEntry(new JarEntry("sample/localjar/GoodLocalJarTask.class"));
            input.transferTo(output);
            output.closeEntry();
        }
        return jar;
    }

    private String sha256(Path path) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = Files.newInputStream(path)) {
            input.transferTo(
                    new java.security.DigestOutputStream(
                            java.io.OutputStream.nullOutputStream(), digest));
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private void waitForVariable(TaskContext context, String name) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (!context.containsVariable(name) && System.nanoTime() < deadline) {
            Thread.sleep(5);
        }
        assertTrue(context.containsVariable(name));
    }

    private record Components(
            LocalJarArtifactValidator validator,
            LocalJarClassLoaderCache cache,
            LocalJarConfigSnapshotter snapshotter,
            LocalJarTaskRunner runner) {}
}
