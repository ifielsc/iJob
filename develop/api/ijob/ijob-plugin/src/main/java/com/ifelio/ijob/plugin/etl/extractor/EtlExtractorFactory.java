package com.ifelio.ijob.plugin.etl.extractor;

import com.ifelio.ijob.core.resource.ResourceResolver;
import com.ifelio.ijob.plugin.etl.config.EtlTaskConfig;
import com.ifelio.ijob.plugin.etl.extractor.database.DatabaseExtractor;
import com.ifelio.ijob.plugin.etl.extractor.file.FileExtractor;
import com.ifelio.ijob.plugin.etl.extractor.file.FileSqlExtractor;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class EtlExtractorFactory {
    private final ResourceResolver resourceResolver;

    public Extractor create(EtlTaskConfig config) {
        return switch (config.getSource().getType()) {
            case KAFKA, RABBITMQ ->
                    throw new IllegalArgumentException(
                            "Message sources require their bounded runner");
            case DATABASE -> new DatabaseExtractor(resourceResolver);
            case FILE ->
                    config.getSource().getReadMode() == EtlTaskConfig.FileReadMode.SQL
                            ? new FileSqlExtractor(resourceResolver)
                            : new FileExtractor(resourceResolver);
        };
    }
}
