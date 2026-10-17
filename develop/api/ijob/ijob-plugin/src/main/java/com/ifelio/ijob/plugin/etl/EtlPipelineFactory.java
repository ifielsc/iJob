package com.ifelio.ijob.plugin.etl;

import com.ifelio.ijob.plugin.etl.config.EtlTaskConfig;
import com.ifelio.ijob.plugin.etl.extractor.EtlExtractorFactory;
import com.ifelio.ijob.plugin.etl.loader.EtlLoaderFactory;
import com.ifelio.ijob.plugin.etl.transformer.TransformChain;
import com.ifelio.ijob.plugin.etl.transformer.TransformerFactory;
import com.ifelio.ijob.plugin.etl.validator.ValidatorChain;
import com.ifelio.ijob.plugin.etl.validator.ValidatorFactory;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class EtlPipelineFactory {
    private final EtlExtractorFactory extractorFactory;
    private final EtlLoaderFactory loaderFactory;
    private final TransformerFactory transformerFactory;
    private final ValidatorFactory validatorFactory;

    public EtlPipeline createRecords(
            EtlTaskConfig config,
            java.util.List<com.ifelio.ijob.plugin.etl.model.DataRecord> records) {
        return new EtlPipeline(
                context ->
                        new com.ifelio.ijob.plugin.etl.reader.DataReader() {
                            private final java.util.Iterator<
                                            com.ifelio.ijob.plugin.etl.model.DataRecord>
                                    iterator = records.iterator();

                            @Override
                            public boolean hasNext() {
                                return iterator.hasNext();
                            }

                            @Override
                            public com.ifelio.ijob.plugin.etl.model.DataRecord next() {
                                return iterator.next();
                            }
                        },
                new TransformChain(transformerFactory, config.getTransforms()),
                new ValidatorChain(config.getValidations(), validatorFactory),
                loaderFactory.create(config));
    }

    public EtlPipeline create(EtlTaskConfig config) {
        return new EtlPipeline(
                extractorFactory.create(config),
                new TransformChain(transformerFactory, config.getTransforms()),
                new ValidatorChain(config.getValidations(), validatorFactory),
                loaderFactory.create(config));
    }
}
