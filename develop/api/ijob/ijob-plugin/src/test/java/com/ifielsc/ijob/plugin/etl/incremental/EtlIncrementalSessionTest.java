// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.plugin.etl.incremental;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ifielsc.ijob.core.etl.EtlCheckpointSnapshot;
import com.ifielsc.ijob.core.etl.EtlCheckpointStore;
import com.ifielsc.ijob.plugin.etl.config.EtlTaskConfig;
import com.ifielsc.ijob.plugin.etl.model.DataRecord;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

class EtlIncrementalSessionTest {
    @Test
    void shouldBindWindowAndCommitHighestTuple() {
        List<Object> committed = new ArrayList<>();
        EtlCheckpointStore store = recordingStore(committed);
        EtlTaskConfig.IncrementalConfig config = incrementalConfig();
        EtlCheckpointSnapshot snapshot =
                new EtlCheckpointSnapshot(1L, "orders", "fingerprint", "LONG", 10, List.of(2), 3);
        EtlIncrementalSession session = new EtlIncrementalSession(store, snapshot, config, 99L, 20);
        Map<String, Object> parameters = new LinkedHashMap<>();

        session.addParameters(parameters);
        session.observe(List.of(record(11, 1), record(11, 2)));
        session.commit();

        assertEquals(10, parameters.get("etlLastWatermark"));
        assertEquals(20, parameters.get("etlUpperWatermark"));
        assertEquals(2, parameters.get("etlLastTie1"));
        assertEquals(List.of(11, List.of(2)), committed);
    }

    @Test
    void shouldRejectNonMonotonicSourceOrder() {
        EtlCheckpointSnapshot snapshot =
                new EtlCheckpointSnapshot(1L, "orders", "fingerprint", "LONG", 10, List.of(), 0);
        EtlIncrementalSession session =
                new EtlIncrementalSession(
                        recordingStore(new ArrayList<>()), snapshot, incrementalConfig(), 99L, 20);

        assertThrows(
                IllegalArgumentException.class,
                () -> session.observe(List.of(record(12, 1), record(11, 1))));
    }

    @Test
    void shouldRejectRowsPastCapturedUpperBound() {
        var snapshot =
                new EtlCheckpointSnapshot(1L, "orders", "fingerprint", "LONG", 10, List.of(), 0);
        var session =
                new EtlIncrementalSession(
                        recordingStore(new ArrayList<>()), snapshot, incrementalConfig(), 99L, 20);
        assertThrows(IllegalArgumentException.class, () -> session.observe(List.of(record(21, 1))));
    }

    private EtlCheckpointStore recordingStore(List<Object> committed) {
        return new EtlCheckpointStore(null, null, new ObjectMapper()) {
            @Override
            public void advance(
                    EtlCheckpointSnapshot snapshot,
                    Long instanceId,
                    Object watermark,
                    List<Object> tieBreakers) {
                committed.add(watermark);
                committed.add(tieBreakers);
            }
        };
    }

    private EtlTaskConfig.IncrementalConfig incrementalConfig() {
        EtlTaskConfig.IncrementalConfig config = new EtlTaskConfig.IncrementalConfig();
        config.setEnabled(true);
        config.setWatermarkField("watermark");
        config.setWatermarkType("LONG");
        config.getTieBreakerFields().add("id");
        return config;
    }

    private DataRecord record(int watermark, int id) {
        DataRecord record = new DataRecord();
        record.put("watermark", watermark);
        record.put("id", id);
        return record;
    }
}
