package com.ifielsc.ijob.core.etl;

import java.util.List;

public record EtlCheckpointSnapshot(
        Long taskId,
        String checkpointKey,
        String sourceFingerprint,
        String watermarkType,
        Object watermark,
        List<Object> tieBreakers,
        long version) {}
