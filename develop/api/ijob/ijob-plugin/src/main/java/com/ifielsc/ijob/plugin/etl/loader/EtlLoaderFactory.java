// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.plugin.etl.loader;

import com.ifielsc.ijob.core.resource.ResourceResolver;
import com.ifielsc.ijob.plugin.etl.config.EtlTaskConfig;
import com.ifielsc.ijob.plugin.etl.loader.database.DatabaseLoader;
import com.ifielsc.ijob.plugin.etl.loader.file.DelimitedFileLoader;

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
