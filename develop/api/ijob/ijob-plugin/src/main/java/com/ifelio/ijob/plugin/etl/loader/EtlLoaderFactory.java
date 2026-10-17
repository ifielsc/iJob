package com.ifelio.ijob.plugin.etl.loader;

import com.ifelio.ijob.core.resource.ResourceResolver;
import com.ifelio.ijob.plugin.etl.config.EtlTaskConfig;
import com.ifelio.ijob.plugin.etl.loader.database.DatabaseLoader;
import com.ifelio.ijob.plugin.etl.loader.file.DelimitedFileLoader;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class EtlLoaderFactory {
    private final ResourceResolver resourceResolver;

    public Loader create(EtlTaskConfig config) {
        return switch (config.getTarget().getType()) {
            case DATABASE -> new DatabaseLoader(resourceResolver, config.getTarget());
            case FILE -> new DelimitedFileLoader(resourceResolver, config.getTarget());
        };
    }
}
