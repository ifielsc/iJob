// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.plugin.etl.extractor;

import com.ifielsc.ijob.core.resource.ResourceResolver;
import com.ifielsc.ijob.plugin.etl.config.EtlTaskConfig;
import com.ifielsc.ijob.plugin.etl.extractor.database.DatabaseExtractor;
import com.ifielsc.ijob.plugin.etl.extractor.file.FileExtractor;
import com.ifielsc.ijob.plugin.etl.extractor.file.FileSqlExtractor;

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
