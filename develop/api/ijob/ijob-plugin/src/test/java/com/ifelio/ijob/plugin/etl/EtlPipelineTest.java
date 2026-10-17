package com.ifelio.ijob.plugin.etl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ifelio.ijob.core.etl.EtlCheckpointSnapshot;
import com.ifelio.ijob.core.etl.EtlCheckpointStore;
import com.ifelio.ijob.core.execute.ExecutionHandle;
import com.ifelio.ijob.core.execute.task.TaskContext;
import com.ifelio.ijob.core.execute.variable.VariableResolver;
import com.ifelio.ijob.plugin.etl.config.EtlTaskConfig;
import com.ifelio.ijob.plugin.etl.extractor.Extractor;
import com.ifelio.ijob.plugin.etl.incremental.EtlIncrementalSession;
import com.ifelio.ijob.plugin.etl.loader.LoadBatchResult;
import com.ifelio.ijob.plugin.etl.loader.Loader;
import com.ifelio.ijob.plugin.etl.model.DataRecord;
import com.ifelio.ijob.plugin.etl.model.EtlContext;
import com.ifelio.ijob.plugin.etl.model.EtlResult;
import com.ifelio.ijob.plugin.etl.reader.DataReader;
import com.ifelio.ijob.plugin.etl.transformer.TransformChain;
import com.ifelio.ijob.plugin.etl.transformer.TransformerFactory;
import com.ifelio.ijob.plugin.etl.validator.ValidatorChain;
import com.ifelio.ijob.plugin.etl.validator.ValidatorConfig;
import com.ifelio.ijob.plugin.etl.validator.ValidatorFactory;
import com.ifelio.ijob.plugin.etl.validator.ValidatorType;
import com.ifelio.ijob.plugin.etl.validator.impl.RequiredValidator;
import com.ifelio.ijob.plugin.support.RecordingInstanceLogs;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

class EtlPipelineTest {
    @Test
    void shouldStreamBatchesAndCloseLoader() {
        List<DataRecord> source = List.of(record(1), record(2), record(3));
        AtomicBoolean closed = new AtomicBoolean();
        RecordingLoader loader = new RecordingLoader(closed);
        EtlPipeline pipeline =
                new EtlPipeline(
                        extractor(source),
                        new TransformChain(new TransformerFactory(List.of()), List.of()),
                        new ValidatorChain(List.of(), new ValidatorFactory(List.of())),
                        loader);
        EtlTaskConfig config = new EtlTaskConfig();
        config.getExecution().setReadBatchSize(2);
        EtlContext context = new EtlContext(taskContext(), config);

        EtlResult result = pipeline.execute(context);

        assertEquals(3, result.getExtractCount());
        assertEquals(3, result.getLoadCount());
        assertEquals(List.of(2, 1), loader.batchSizes);
        assertTrue(closed.get());
    }

    @Test
    void shouldStopIncrementalCheckpointAtFilteredRecordsWhenConfigured() {
        AtomicBoolean closed = new AtomicBoolean();
        RecordingLoader loader = new RecordingLoader(closed);
        ValidatorConfig required = new ValidatorConfig();
        required.setType(ValidatorType.REQUIRED);
        required.setRuleId("required-value");
        required.getProperties().put("fields", List.of("required"));
        EtlPipeline pipeline =
                new EtlPipeline(
                        extractor(List.of(record(1))),
                        new TransformChain(new TransformerFactory(List.of()), List.of()),
                        new ValidatorChain(
                                List.of(required),
                                new ValidatorFactory(List.of(new RequiredValidator()))),
                        loader);
        EtlTaskConfig config = new EtlTaskConfig();
        config.getIncremental().setEnabled(true);
        config.getIncremental().setWatermarkField("id");
        config.getIncremental().setWatermarkType("LONG");
        config.getIncremental().setAllowCheckpointPastInvalidRecords(false);
        EtlContext context = new EtlContext(taskContext(), config);
        EtlCheckpointSnapshot snapshot =
                new EtlCheckpointSnapshot(1L, "test", "fingerprint", "LONG", 0L, List.of(), 0);
        context.setIncrementalSession(
                new EtlIncrementalSession(
                        new EtlCheckpointStore(null, null, null),
                        snapshot,
                        config.getIncremental(),
                        1L,
                        10L));

        assertThrows(IllegalArgumentException.class, () -> pipeline.execute(context));
        assertTrue(closed.get());
    }

    private Extractor extractor(List<DataRecord> records) {
        return context ->
                new DataReader() {
                    private final Iterator<DataRecord> iterator = records.iterator();

                    @Override
                    public boolean hasNext() {
                        return iterator.hasNext();
                    }

                    @Override
                    public DataRecord next() {
                        return iterator.next();
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
            public void progress(Integer progress) {
                // No persistence is needed for this unit test.
            }
        };
    }

    private DataRecord record(int id) {
        DataRecord record = new DataRecord();
        record.put("id", id);
        return record;
    }

    private static final class RecordingLoader implements Loader {
        private final AtomicBoolean closed;
        private final List<Integer> batchSizes = new ArrayList<>();

        private RecordingLoader(AtomicBoolean closed) {
            this.closed = closed;
        }

        @Override
        public void open(EtlContext context) {}

        @Override
        public LoadBatchResult writeBatch(List<DataRecord> records) {
            batchSizes.add(records.size());
            return new LoadBatchResult(records.size(), 0, 0, 0, 0);
        }

        @Override
        public void flush() {}

        @Override
        public void close() {
            closed.set(true);
        }
    }
}
