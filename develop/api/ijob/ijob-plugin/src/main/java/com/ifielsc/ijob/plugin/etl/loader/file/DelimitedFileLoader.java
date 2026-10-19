package com.ifielsc.ijob.plugin.etl.loader.file;

import com.ifielsc.ijob.core.resource.ResourceClientLease;
import com.ifielsc.ijob.core.resource.ResourceResolver;
import com.ifielsc.ijob.core.resource.factory.file.client.FileClient;
import com.ifielsc.ijob.plugin.etl.config.EtlTaskConfig;
import com.ifielsc.ijob.plugin.etl.loader.LoadBatchResult;
import com.ifielsc.ijob.plugin.etl.loader.Loader;
import com.ifielsc.ijob.plugin.etl.loader.TargetMappingPlan;
import com.ifielsc.ijob.plugin.etl.model.DataRecord;
import com.ifielsc.ijob.plugin.etl.model.EtlContext;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public final class DelimitedFileLoader implements Loader {
    private final ResourceResolver resourceResolver;
    private EtlTaskConfig.TargetConfig config;
    private final boolean automatic;
    private boolean mappingResolved;
    private final AtomicBoolean closed = new AtomicBoolean();
    private ResourceClientLease<FileClient> lease;
    private EtlContext context;
    private Path temp;
    private BufferedWriter writer;
    private String targetPath;
    private boolean skipOutput;
    private boolean headerWritten;
    private long rowCount;

    public DelimitedFileLoader(
            ResourceResolver resourceResolver, EtlTaskConfig.TargetConfig config) {
        this.resourceResolver = resourceResolver;
        this.config = config;
        this.automatic = config.getMappingMode() == EtlTaskConfig.MappingMode.AUTO;
    }

    @Override
    public void open(EtlContext context) {
        this.context = context;
        targetPath = context.getTaskContext().resolve(config.getPath());
        attachLease(resourceResolver.leaseFileClient(config.getResourceId()));
        try {
            boolean exists = lease.getClient().exists(targetPath);
            if (exists && config.getExistingFilePolicy() == EtlTaskConfig.ExistingFilePolicy.FAIL) {
                throw new IllegalArgumentException("ETL target file already exists");
            }
            if (exists && config.getExistingFilePolicy() == EtlTaskConfig.ExistingFilePolicy.SKIP) {
                skipOutput = true;
                return;
            }
            var encoder = Charset.forName(config.getCharset()).newEncoder();
            temp = Files.createTempFile("ijob-etl-target-", ".tmp");
            failIfClosed();
            writer =
                    new BufferedWriter(
                            new OutputStreamWriter(
                                    new LimitedOutputStream(
                                            Files.newOutputStream(temp),
                                            context.getConfig().getExecution().getMaxFileBytes()),
                                    encoder));
            failIfClosed();
        } catch (Exception exception) {
            cleanup();
            throw new IllegalStateException("ETL file target could not be opened", exception);
        }
    }

    private void attachLease(ResourceClientLease<FileClient> opened) {
        lease = opened;
        if (closed.get()) {
            opened.close();
            throw new IllegalStateException("ETL file target was stopped");
        }
    }

    private void failIfClosed() {
        if (closed.get()) {
            throw new IllegalStateException("ETL file target was stopped");
        }
    }

    @Override
    public LoadBatchResult writeBatch(List<DataRecord> records) {
        if (skipOutput) {
            return new LoadBatchResult(0, 0, 0, records.size(), 0);
        }
        try {
            if (records.isEmpty()) return new LoadBatchResult(0, 0, 0, 0, 0);
            if (automatic) {
                if (!mappingResolved) {
                    config = TargetMappingPlan.resolve(context, config, List.of(), records);
                    mappingResolved = true;
                } else TargetMappingPlan.validateRows(context, records);
            }
            TargetMappingPlan.validateMappedFields(config, records);
            if (!headerWritten && config.isHeader()) {
                writeValues(List.copyOf(config.getColumns().keySet()));
                headerWritten = true;
            }
            for (DataRecord record : records) {
                context.checkControl();
                List<String> values =
                        config.getColumns().values().stream()
                                .map(record::get)
                                .map(value -> value == null ? "" : value.toString())
                                .toList();
                writeValues(values);
                rowCount++;
            }
            // Drain the encoder each batch so small over-limit batches fail before more extraction.
            writer.flush();
            return new LoadBatchResult(records.size(), 0, 0, 0, 0);
        } catch (FileLimitException exception) {
            throw new IllegalArgumentException("ETL target file limit exceeded", exception);
        } catch (IOException exception) {
            throw new IllegalStateException("ETL target file write failed", exception);
        }
    }

    private void writeValues(List<String> values) throws IOException {
        for (int index = 0; index < values.size(); index++) {
            if (index > 0) {
                writer.write(config.getDelimiter());
            }
            writeEscaped(values.get(index));
        }
        writer.newLine();
    }

    private void writeEscaped(String value) throws IOException {
        Character quote = config.getQuote();
        if (quote == null) {
            if (value.contains(config.getDelimiter())
                    || value.indexOf('\n') >= 0
                    || value.indexOf('\r') >= 0) {
                throw new IllegalArgumentException("ETL target value requires quote escaping");
            }
            writer.write(value);
            return;
        }
        boolean quoted =
                value.contains(config.getDelimiter())
                        || value.indexOf(quote) >= 0
                        || value.indexOf('\n') >= 0
                        || value.indexOf('\r') >= 0;
        if (quoted) {
            writer.write(quote);
        }
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (current == quote) {
                writer.write(quote);
            }
            writer.write(current);
        }
        if (quoted) {
            writer.write(quote);
        }
    }

    @Override
    public void flush() {
        if (skipOutput) {
            return;
        }
        try {
            if (rowCount == 0) {
                if (config.getEmptyFilePolicy() == EtlTaskConfig.EmptyFilePolicy.SKIP) {
                    closeWriter();
                    return;
                }
                if (config.getEmptyFilePolicy() == EtlTaskConfig.EmptyFilePolicy.CREATE_WITH_HEADER
                        && config.isHeader()
                        && !headerWritten) {
                    if (automatic && !mappingResolved)
                        throw new IllegalArgumentException(
                                "ETL AUTO mapping cannot infer an empty file header; use SKIP,"
                                        + " CREATE_EMPTY or MANUAL mapping");
                    writeValues(List.copyOf(config.getColumns().keySet()));
                    headerWritten = true;
                }
            }
            writer.flush();
            closeWriter();
            if (Files.size(temp) > context.getConfig().getExecution().getMaxFileBytes()) {
                throw new IllegalArgumentException("ETL target file limit exceeded");
            }
            context.checkControl();
            try (InputStream input = Files.newInputStream(temp)) {
                lease.getClient().upload(targetPath, input, Files.size(temp));
            }
        } catch (FileLimitException exception) {
            throw new IllegalArgumentException("ETL target file limit exceeded", exception);
        } catch (IOException exception) {
            throw new IllegalStateException("ETL target file upload failed", exception);
        }
    }

    private void closeWriter() throws IOException {
        if (writer != null) {
            try {
                writer.close();
            } finally {
                writer = null;
            }
        }
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        cleanup();
    }

    private synchronized void cleanup() {
        try {
            closeWriter();
        } catch (IOException ignored) {
            // Continue with lease and temporary file cleanup.
        }
        if (lease != null) {
            lease.close();
            lease = null;
        }
        if (temp != null) {
            try {
                Files.deleteIfExists(temp);
                temp = null;
            } catch (IOException exception) {
                throw new IllegalStateException(
                        "ETL target temporary file cleanup failed", exception);
            }
        }
    }

    /** Counts encoded bytes, including escaping, headers and platform line separators. */
    private static final class LimitedOutputStream extends OutputStream {
        private final OutputStream output;
        private final long limit;
        private long written;
        private boolean exceeded;

        private LimitedOutputStream(OutputStream output, long limit) {
            this.output = output;
            this.limit = limit;
        }

        private void requireCapacity(int length) throws FileLimitException {
            if (exceeded || length > limit - written) {
                exceeded = true;
                throw new FileLimitException();
            }
        }

        @Override
        public void write(int value) throws IOException {
            requireCapacity(1);
            output.write(value);
            written++;
        }

        @Override
        public void write(byte[] bytes, int offset, int length) throws IOException {
            java.util.Objects.checkFromIndexSize(offset, length, bytes.length);
            requireCapacity(length);
            output.write(bytes, offset, length);
            written += length;
        }

        @Override
        public void flush() throws IOException {
            output.flush();
        }

        @Override
        public void close() throws IOException {
            output.close();
        }
    }

    private static final class FileLimitException extends IOException {
        private static final long serialVersionUID = 1L;

        private FileLimitException() {
            super("ETL target file limit exceeded");
        }
    }
}
