package com.ifelio.ijob.plugin.etl;

import com.ifelio.ijob.plugin.etl.config.EtlTaskConfig;
import com.ifelio.ijob.plugin.etl.extractor.Extractor;
import com.ifelio.ijob.plugin.etl.loader.Loader;
import com.ifelio.ijob.plugin.etl.model.DataRecord;
import com.ifelio.ijob.plugin.etl.model.EtlContext;
import com.ifelio.ijob.plugin.etl.model.EtlResult;
import com.ifelio.ijob.plugin.etl.reader.DataReader;
import com.ifelio.ijob.plugin.etl.transformer.TransformChain;
import com.ifelio.ijob.plugin.etl.validator.ValidationResult;
import com.ifelio.ijob.plugin.etl.validator.ValidatorChain;

import lombok.RequiredArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@RequiredArgsConstructor
public class EtlPipeline {
    private final Extractor extractor;
    private final TransformChain transformChain;
    private final ValidatorChain validatorChain;
    private final Loader loader;

    public EtlResult execute(EtlContext context) {
        List<DataRecord> buffer = new ArrayList<>(context.getBatchSize());
        boolean loaderOpened = false;
        DataReader registeredReader = null;
        try {
            context.checkControl();
            context.getTaskContext().registerResource(loader);
            context.getWriteGuard().run();
            loader.open(context);
            loaderOpened = true;
            context.getTaskContext()
                    .logInfo("ETL loader opened, batchSize=" + context.getBatchSize());
            context.progress(5);
            try (DataReader reader = extractor.extract(context)) {
                registeredReader = reader;
                context.getTaskContext().registerResource(reader);
                context.getTaskContext()
                        .logInfo("ETL extraction, transformation and validation started");
                while (reader.hasNext()) {
                    context.checkControl();
                    DataRecord record = reader.next();
                    if (record == null) {
                        continue;
                    }
                    validateRecordLimits(record, context);
                    buffer.add(record);
                    long total = context.getExtractCount() + buffer.size();
                    if (total > context.getConfig().getExecution().getMaxRecords()) {
                        throw new IllegalArgumentException("ETL source record limit exceeded");
                    }
                    if (buffer.size() >= context.getBatchSize()) {
                        processBatch(buffer, context);
                        buffer.clear();
                    }
                }
                if (!buffer.isEmpty()) {
                    processBatch(buffer, context);
                }
                context.checkControl();
                context.getWriteGuard().run();
                loader.flush();
                context.getTaskContext()
                        .logInfo(
                                "ETL loader flushed, loaded="
                                        + context.getLoadCount()
                                        + ", invalid="
                                        + context.getValidateErrorCount());
                context.getIncrementalSession().commit();
                if (context.getIncrementalSession().enabled()) {
                    context.getTaskContext().logInfo("ETL checkpoint committed");
                }
            }
        } finally {
            try {
                if (loaderOpened) loader.close();
            } finally {
                context.getTaskContext().unregisterResource(loader);
                if (registeredReader != null)
                    context.getTaskContext().unregisterResource(registeredReader);
            }
        }
        context.progress(99);
        return EtlResult.success(context);
    }

    private void processBatch(List<DataRecord> records, EtlContext context) {
        context.checkControl();
        context.setExtractCount(context.getExtractCount() + records.size());
        context.getIncrementalSession().observe(records);
        List<DataRecord> transformed = transformChain.transform(new ArrayList<>(records), context);
        context.setTransformCount(context.getTransformCount() + transformed.size());
        List<ValidationResult> validationResults = validatorChain.validate(transformed, context);
        long invalid = validationResults.stream().filter(result -> !result.isSuccess()).count();
        context.setValidateErrorCount(context.getValidateErrorCount() + invalid);
        if (invalid > 0
                && (context.getStagedSource() != null
                        || context.getIncrementalSession().enabled()
                                && !context.getConfig()
                                        .getIncremental()
                                        .isAllowCheckpointPastInvalidRecords())) {
            throw new IllegalArgumentException(
                    "ETL incremental validation rejected records; "
                            + "checkpoint advancement is disabled");
        }
        List<DataRecord> valid =
                validationResults.stream()
                        .filter(ValidationResult::isSuccess)
                        .map(ValidationResult::getRecord)
                        .toList();
        if (!valid.isEmpty()) {
            context.checkControl();
            context.getWriteGuard().run();
            com.ifelio.ijob.plugin.etl.loader.LoadBatchResult loadResult = loader.writeBatch(valid);
            context.setLoadCount(context.getLoadCount() + loadResult.loaded());
            context.setInsertCount(context.getInsertCount() + loadResult.inserted());
            context.setUpdateCount(context.getUpdateCount() + loadResult.updated());
            context.setSkipCount(context.getSkipCount() + loadResult.skipped());
            context.setProtectedCount(context.getProtectedCount() + loadResult.protectedRecords());
        }
        context.progress(10 + (int) Math.min(80, context.getExtractCount() / 1000));
    }

    private void validateRecordLimits(DataRecord record, EtlContext context) {
        EtlTaskConfig.ExecutionConfig execution = context.getConfig().getExecution();
        if (record.getFields().size() > execution.getMaxRecordFields()) {
            throw new IllegalArgumentException("ETL record field limit exceeded");
        }
        for (Object value : record.getFields().values()) {
            if (value instanceof CharSequence text
                    && text.length() > execution.getMaxTextFieldChars()) {
                throw new IllegalArgumentException("ETL text field limit exceeded");
            }
        }
    }
}
