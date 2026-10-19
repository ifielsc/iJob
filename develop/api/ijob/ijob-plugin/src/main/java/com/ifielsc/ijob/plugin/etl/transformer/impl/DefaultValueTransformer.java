// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.plugin.etl.transformer.impl;

import com.ifielsc.ijob.plugin.etl.model.DataRecord;
import com.ifielsc.ijob.plugin.etl.transformer.AbstractTransformer;
import com.ifielsc.ijob.plugin.etl.transformer.TransformConfig;
import com.ifielsc.ijob.plugin.etl.transformer.TransformType;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DefaultValueTransformer extends AbstractTransformer {
    @Override
    public String type() {
        return TransformType.DEFAULT_VALUE.name();
    }

    @Override
    public List<DataRecord> transform(List<DataRecord> records, TransformConfig config) {
        config.getProperties()
                .forEach(
                        (field, value) -> {
                            for (DataRecord record : records) {
                                if (record.get(field) == null) {
                                    record.put(field, value);
                                }
                            }
                        });

        return records;
    }
}
