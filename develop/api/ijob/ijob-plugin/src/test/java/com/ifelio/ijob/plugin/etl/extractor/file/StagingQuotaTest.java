package com.ifelio.ijob.plugin.etl.extractor.file;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.ifelio.ijob.plugin.etl.config.EtlTaskConfig;
import com.ifelio.ijob.plugin.etl.model.DataRecord;

import org.junit.jupiter.api.Test;

class StagingQuotaTest {
    @Test
    void shouldBoundRowsCellsAndExpandedTextBeforeH2Staging() {
        EtlTaskConfig.ExecutionConfig config = new EtlTaskConfig.ExecutionConfig();
        config.setMaxRecords(2);
        config.setMaxRecordFields(2);
        config.setMaxTextFieldChars(3);
        config.setMaxExpandedTextChars(5);
        config.setMaxStagingCells(4);
        StagingQuota quota = new StagingQuota(config);

        quota.accept(record("ab", "c"));
        quota.accept(record("d", "e"));

        assertEquals(2, quota.rows());
        assertEquals(4, quota.cells());
        assertEquals(5, quota.textChars());
        assertThrows(IllegalArgumentException.class, () -> quota.accept(record("x", "y")));
    }

    @Test
    void shouldRejectOversizedStagingFields() {
        EtlTaskConfig.ExecutionConfig config = new EtlTaskConfig.ExecutionConfig();
        config.setMaxTextFieldChars(2);
        StagingQuota quota = new StagingQuota(config);

        assertThrows(IllegalArgumentException.class, () -> quota.accept(record("abc", null)));
    }

    private DataRecord record(String first, String second) {
        DataRecord record = new DataRecord();
        record.put("first", first);
        record.put("second", second);
        return record;
    }
}
