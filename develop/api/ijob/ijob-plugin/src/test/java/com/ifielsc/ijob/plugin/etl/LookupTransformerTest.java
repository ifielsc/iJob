package com.ifielsc.ijob.plugin.etl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.ifielsc.ijob.plugin.etl.model.DataRecord;
import com.ifielsc.ijob.plugin.etl.transformer.TransformConfig;
import com.ifielsc.ijob.plugin.etl.transformer.impl.LookupTransformer;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

class LookupTransformerTest {
    @Test
    void allNullKeysStillApplyMissingValuePolicyWithoutOpeningAResource() {
        for (boolean keepOriginal : List.of(false, true)) {
            for (String fallback : new String[] {"fallback", null}) {
                var properties =
                        new LinkedHashMap<String, Object>(
                                Map.of(
                                        "sourceField",
                                        "source",
                                        "targetField",
                                        "target",
                                        "sql",
                                        "select k,v where k in (:keys)",
                                        "keyColumn",
                                        "k",
                                        "valueColumn",
                                        "v",
                                        "resourceId",
                                        1L,
                                        "keepOriginalWhenMissing",
                                        keepOriginal));
                properties.put("defaultValue", fallback);
                var config = new TransformConfig();
                config.setProperties(properties);
                var record = new DataRecord();
                record.put("source", null);
                record.put("target", "original");
                new LookupTransformer(null).transform(List.of(record), config);
                assertEquals(keepOriginal ? "original" : fallback, record.get("target"));
                assertNull(record.get("source"));
            }
        }
    }
}
