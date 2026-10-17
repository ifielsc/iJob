package com.ifelio.ijob.plugin.etl.incremental;

import com.ifelio.ijob.core.etl.EtlCheckpointSnapshot;
import com.ifelio.ijob.core.etl.EtlCheckpointStore;
import com.ifelio.ijob.plugin.etl.config.EtlTaskConfig;
import com.ifelio.ijob.plugin.etl.model.DataRecord;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class EtlIncrementalSession {
    private static final EtlIncrementalSession DISABLED = new EtlIncrementalSession();

    private final EtlCheckpointStore store;
    private final EtlCheckpointSnapshot snapshot;
    private final EtlTaskConfig.IncrementalConfig config;
    private final Long instanceId;
    private final Object upperBound;
    private Object observedWatermark;
    private List<Object> observedTieBreakers;

    private EtlIncrementalSession() {
        store = null;
        snapshot = null;
        config = null;
        instanceId = null;
        upperBound = null;
    }

    public EtlIncrementalSession(
            EtlCheckpointStore store,
            EtlCheckpointSnapshot snapshot,
            EtlTaskConfig.IncrementalConfig config,
            Long instanceId,
            Object upperBound) {
        this.store = store;
        this.snapshot = snapshot;
        this.config = config;
        this.instanceId = instanceId;
        this.upperBound = upperBound;
        this.observedWatermark = snapshot.watermark();
        this.observedTieBreakers = snapshot.tieBreakers();
    }

    public static EtlIncrementalSession disabled() {
        return DISABLED;
    }

    public boolean enabled() {
        return store != null;
    }

    public void addParameters(Map<String, Object> parameters) {
        if (!enabled()) {
            return;
        }
        parameters.put("etlLastWatermark", snapshot.watermark());
        parameters.put("etlUpperWatermark", upperBound);
        for (int index = 0; index < snapshot.tieBreakers().size(); index++) {
            parameters.put("etlLastTie" + (index + 1), snapshot.tieBreakers().get(index));
        }
    }

    public void observe(List<DataRecord> records) {
        if (!enabled()) {
            return;
        }
        for (DataRecord record : records) {
            Object watermark = record.get(config.getWatermarkField());
            if (watermark == null) {
                throw new IllegalArgumentException("ETL incremental watermark must not be null");
            }
            if (EtlWatermark.compare(watermark, upperBound, config.getWatermarkType()) > 0)
                throw new IllegalArgumentException(
                        "ETL incremental source exceeded the captured upper bound");
            List<Object> ties = new ArrayList<>();
            for (String field : config.getTieBreakerFields()) {
                Object value = record.get(field);
                if (value == null) {
                    throw new IllegalArgumentException(
                            "ETL incremental tie breaker must not be null: " + field);
                }
                ties.add(value);
            }
            if (compare(watermark, ties, observedWatermark, observedTieBreakers) < 0) {
                throw new IllegalArgumentException("ETL incremental source order is not monotonic");
            }
            if (compare(watermark, ties, observedWatermark, observedTieBreakers) > 0) {
                observedWatermark = watermark;
                observedTieBreakers = List.copyOf(ties);
            }
        }
    }

    public void commit() {
        if (!enabled()
                || compare(
                                observedWatermark,
                                observedTieBreakers,
                                snapshot.watermark(),
                                snapshot.tieBreakers())
                        <= 0) {
            return;
        }
        store.advance(snapshot, instanceId, observedWatermark, observedTieBreakers);
    }

    private int compare(
            Object leftWatermark,
            List<Object> leftTies,
            Object rightWatermark,
            List<Object> rightTies) {
        if (rightWatermark == null) {
            return leftWatermark == null ? 0 : 1;
        }
        int watermark = compareValue(leftWatermark, rightWatermark, config.getWatermarkType());
        if (watermark != 0) {
            return watermark;
        }
        int size = Math.min(leftTies.size(), rightTies.size());
        for (int index = 0; index < size; index++) {
            int tie = compareGeneric(leftTies.get(index), rightTies.get(index));
            if (tie != 0) {
                return tie;
            }
        }
        return Integer.compare(leftTies.size(), rightTies.size());
    }

    private int compareValue(Object left, Object right, String type) {
        return EtlWatermark.compare(left, right, type);
    }

    private int compareGeneric(Object left, Object right) {
        if (left instanceof Number && right instanceof Number) {
            return decimal(left).compareTo(decimal(right));
        }
        return left.toString().compareTo(right.toString());
    }

    private BigDecimal decimal(Object value) {
        return value instanceof BigDecimal decimal ? decimal : new BigDecimal(value.toString());
    }
}
