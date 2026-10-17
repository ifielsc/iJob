package com.ifelio.ijob.plugin.etl.transformer;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.ifelio.ijob.plugin.etl.model.DataRecord;

import org.junit.jupiter.api.Test;

import java.util.List;

class TransformChainTest {
    @Test
    void shouldDeduplicateAcrossReadBatchesWithTypedCompositeKeys() {
        TransformConfig deduplicate = new TransformConfig();
        deduplicate.setType(TransformType.DEDUPLICATE);
        deduplicate.getProperties().put("fields", List.of("id", "kind"));
        TransformChain chain =
                new TransformChain(new TransformerFactory(List.of()), List.of(deduplicate));

        assertEquals(1, chain.transform(List.of(record(1, "A"))).size());
        assertEquals(1, chain.transform(List.of(record("1", "A"), record(1, "A"))).size());
    }

    private DataRecord record(Object id, String kind) {
        DataRecord record = new DataRecord();
        record.put("id", id);
        record.put("kind", kind);
        return record;
    }
}
