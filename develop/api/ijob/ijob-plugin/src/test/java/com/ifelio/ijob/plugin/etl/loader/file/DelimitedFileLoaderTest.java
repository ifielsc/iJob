package com.ifelio.ijob.plugin.etl.loader.file;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ifelio.ijob.core.execute.ExecutionHandle;
import com.ifelio.ijob.core.execute.task.TaskContext;
import com.ifelio.ijob.core.execute.variable.VariableResolver;
import com.ifelio.ijob.core.resource.ResourceClientLease;
import com.ifelio.ijob.core.resource.ResourceResolver;
import com.ifelio.ijob.core.resource.factory.file.client.FileClient;
import com.ifelio.ijob.core.resource.factory.file.client.FileInfo;
import com.ifelio.ijob.plugin.etl.config.EtlTaskConfig;
import com.ifelio.ijob.plugin.etl.model.DataRecord;
import com.ifelio.ijob.plugin.etl.model.EtlContext;
import com.ifelio.ijob.plugin.support.RecordingInstanceLogs;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

class DelimitedFileLoaderTest {
    @Test
    void missingManualSourceFieldFailsWithoutUploadingFile() {
        MemoryFileClient client = new MemoryFileClient();
        EtlTaskConfig config = config();
        config.getTarget().getColumns().put("name", "source_name");
        DataRecord valid = new DataRecord(Map.of("source_name", "valid"));
        DelimitedFileLoader loader = new DelimitedFileLoader(resolver(client), config.getTarget());
        try {
            loader.open(new EtlContext(taskContext(), config));
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            loader.writeBatch(
                                    List.of(valid, new DataRecord(Map.of("other", "missing")))));
        } finally {
            loader.close();
        }
        assertNull(client.uploadPath);
    }

    @Test
    void explicitNullManualSourceFieldWritesAnEmptyCell() {
        MemoryFileClient client = new MemoryFileClient();
        EtlTaskConfig config = config();
        config.getTarget().getColumns().put("name", "source_name");
        DataRecord record = new DataRecord();
        record.put("source_name", null);
        DelimitedFileLoader loader = new DelimitedFileLoader(resolver(client), config.getTarget());
        try {
            loader.open(new EtlContext(taskContext(), config));
            loader.writeBatch(List.of(record));
            loader.flush();
        } finally {
            loader.close();
        }
        assertEquals(
                "name" + System.lineSeparator() + System.lineSeparator(),
                client.output.toString(StandardCharsets.UTF_8));
    }

    @Test
    void exactEncodedBudgetIncludesHeaderEscapingAndLineEndings() {
        MemoryFileClient client = new MemoryFileClient();
        var config = config();
        String expected =
                "name" + System.lineSeparator() + "\"中文,\"\"值\"\"\"" + System.lineSeparator();
        config.getExecution().setMaxFileBytes(expected.getBytes(StandardCharsets.UTF_8).length);
        var record = new DataRecord();
        record.put("name", "中文,\"值\"");
        var loader = new DelimitedFileLoader(resolver(client), config.getTarget());
        try {
            loader.open(new EtlContext(taskContext(), config));
            loader.writeBatch(List.of(record));
            loader.flush();
        } finally {
            loader.close();
        }
        assertEquals(expected, client.output.toString(StandardCharsets.UTF_8));
    }

    @Test
    void accumulatedBatchesCannotExceedDiskBudgetAndCloseDeletesStaging() throws Exception {
        MemoryFileClient client = new MemoryFileClient();
        var config = config();
        int firstBytes =
                ("name" + System.lineSeparator() + "中文" + System.lineSeparator())
                        .getBytes(StandardCharsets.UTF_8)
                        .length;
        config.getExecution().setMaxFileBytes(firstBytes + 1);
        var record = new DataRecord();
        record.put("name", "中文");
        var loader = new DelimitedFileLoader(resolver(client), config.getTarget());
        java.nio.file.Path staging = null;
        try {
            loader.open(new EtlContext(taskContext(), config));
            staging =
                    (java.nio.file.Path)
                            org.springframework.test.util.ReflectionTestUtils.getField(
                                    loader, "temp");
            loader.writeBatch(List.of(record));
            assertEquals(firstBytes, java.nio.file.Files.size(staging));
            assertThrows(IllegalArgumentException.class, () -> loader.writeBatch(List.of(record)));
            assertTrue(java.nio.file.Files.size(staging) <= firstBytes + 1);
            assertNull(client.uploadPath);
        } finally {
            loader.close();
        }
        assertTrue(java.nio.file.Files.notExists(staging));
        assertNull(client.uploadPath);
    }

    @Test
    void emptyFileHeaderIsAlsoBounded() {
        MemoryFileClient client = new MemoryFileClient();
        var config = config();
        config.getExecution().setMaxFileBytes(1);
        var loader = new DelimitedFileLoader(resolver(client), config.getTarget());
        try {
            loader.open(new EtlContext(taskContext(), config));
            assertThrows(IllegalArgumentException.class, loader::flush);
        } finally {
            loader.close();
        }
        assertNull(client.uploadPath);
    }

    @Test
    void rejectsEncodedOutputInTheBatchBeforeUpload() {
        MemoryFileClient client = new MemoryFileClient();
        EtlTaskConfig config = config();
        config.getExecution().setMaxFileBytes(8);
        DelimitedFileLoader loader = new DelimitedFileLoader(resolver(client), config.getTarget());
        DataRecord record = new DataRecord();
        record.put("name", "中文,\"value\"");
        try {
            loader.open(new EtlContext(taskContext(), config));
            assertThrows(RuntimeException.class, () -> loader.writeBatch(List.of(record)));
            assertNull(client.uploadPath);
        } finally {
            loader.close();
        }
        assertNull(client.uploadPath);
    }

    @Test
    void shouldWriteHeaderAndEscapedValues() {
        MemoryFileClient client = new MemoryFileClient();
        EtlTaskConfig config = config();
        DelimitedFileLoader loader = new DelimitedFileLoader(resolver(client), config.getTarget());
        EtlContext context = new EtlContext(taskContext(), config);
        DataRecord record = new DataRecord();
        record.put("name", "Alice, A");

        loader.open(context);
        loader.writeBatch(List.of(record));
        loader.flush();
        loader.close();

        String output = client.output.toString(StandardCharsets.UTF_8);
        assertTrue(output.startsWith("name" + System.lineSeparator()));
        assertTrue(output.contains("\"Alice, A\""));
        assertEquals("result.csv", client.uploadPath);
    }

    @Test
    void automaticFileColumnsUseOverridesExclusionsAndStableOrder() {
        MemoryFileClient client = new MemoryFileClient();
        var config = config();
        var target = config.getTarget();
        target.setMappingMode(EtlTaskConfig.MappingMode.AUTO);
        target.setColumns(Map.of("customer", "name"));
        target.setExcludedFields(List.of("secret"));
        var loader = new DelimitedFileLoader(resolver(client), target);
        var context = new EtlContext(taskContext(), config);
        var first = new DataRecord();
        first.put("id", 1);
        first.put("name", "Alice");
        first.put("secret", "hidden");
        var second = new DataRecord();
        second.put("secret", "hidden");
        second.put("name", "Bob");
        second.put("id", 2);
        try {
            loader.open(context);
            loader.writeBatch(List.of(first));
            loader.writeBatch(List.of(second));
            loader.flush();
        } finally {
            loader.close();
        }
        assertEquals(
                "id,customer"
                        + System.lineSeparator()
                        + "1,Alice"
                        + System.lineSeparator()
                        + "2,Bob"
                        + System.lineSeparator(),
                client.output.toString(StandardCharsets.UTF_8));
        assertEquals(Map.of("customer", "name"), target.getColumns());
    }

    @Test
    void automaticEmptyFileCannotGuessHeaderAndDoesNotUpload() {
        MemoryFileClient client = new MemoryFileClient();
        var config = config();
        config.getTarget().setMappingMode(EtlTaskConfig.MappingMode.AUTO);
        config.getTarget().getColumns().clear();
        var loader = new DelimitedFileLoader(resolver(client), config.getTarget());
        try {
            loader.open(new EtlContext(taskContext(), config));
            assertThrows(IllegalArgumentException.class, loader::flush);
        } finally {
            loader.close();
        }
        assertNull(client.uploadPath);
    }

    @Test
    void automaticEmptyFileCanExplicitlySkipOrCreateEmpty() {
        for (var policy :
                List.of(
                        EtlTaskConfig.EmptyFilePolicy.SKIP,
                        EtlTaskConfig.EmptyFilePolicy.CREATE_EMPTY)) {
            MemoryFileClient client = new MemoryFileClient();
            var config = config();
            config.getTarget().setMappingMode(EtlTaskConfig.MappingMode.AUTO);
            config.getTarget().getColumns().clear();
            config.getTarget().setEmptyFilePolicy(policy);
            var loader = new DelimitedFileLoader(resolver(client), config.getTarget());
            try {
                loader.open(new EtlContext(taskContext(), config));
                loader.flush();
            } finally {
                loader.close();
            }
            if (policy == EtlTaskConfig.EmptyFilePolicy.SKIP) assertNull(client.uploadPath);
            else assertEquals("result.csv", client.uploadPath);
            assertEquals(0, client.output.size());
        }
    }

    private EtlTaskConfig config() {
        EtlTaskConfig config = new EtlTaskConfig();
        EtlTaskConfig.TargetConfig target = new EtlTaskConfig.TargetConfig();
        target.setType(EtlTaskConfig.TargetType.FILE);
        target.setResourceId(1L);
        target.setPath("result.csv");
        target.getColumns().put("name", "name");
        config.setTarget(target);
        return config;
    }

    private ResourceResolver resolver(FileClient client) {
        return new ResourceResolver(null) {
            @Override
            public ResourceClientLease<FileClient> leaseFileClient(Long resourceId) {
                return new ResourceClientLease<>(client, () -> {});
            }
        };
    }

    private TaskContext taskContext() {
        return new TaskContext(
                1L,
                new ExecutionHandle(1L),
                null,
                new RecordingInstanceLogs(),
                new VariableResolver(),
                Map.of()) {
            @Override
            public void progress(Integer progress) {}
        };
    }

    private static final class MemoryFileClient implements FileClient {
        private final ByteArrayOutputStream output = new ByteArrayOutputStream();
        private String uploadPath;

        @Override
        public List<FileInfo> list(String path) {
            return List.of();
        }

        @Override
        public InputStream download(String path) {
            return new ByteArrayInputStream(new byte[0]);
        }

        @Override
        public void upload(String path, InputStream input) throws java.io.IOException {
            uploadPath = path;
            input.transferTo(output);
        }

        @Override
        public void mkdirs(String path) {}

        @Override
        public boolean exists(String path) {
            return false;
        }

        @Override
        public void delete(String path) {}
    }
}
