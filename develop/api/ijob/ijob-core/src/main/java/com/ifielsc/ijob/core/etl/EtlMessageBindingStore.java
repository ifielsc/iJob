// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.core.etl;

import com.ifielsc.ijob.infra.mapper.runtime.EtlFileStateMapper;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Dedicated binding row contains no broker offsets. Reuses V30 ownership and CAS columns. */
@Service
@RequiredArgsConstructor
public class EtlMessageBindingStore {
    private static final String KEY = "__message_binding_v1";
    private final EtlCheckpointStore checkpoints;
    private final EtlFileStateMapper ownership;

    @Transactional
    public EtlCheckpointSnapshot acquire(Long instanceId, String fingerprint, String token) {
        var snapshot =
                checkpoints.loadOrCreate(
                        instanceId, KEY, fingerprint, "STRING", "BROKER", List.of());
        if (ownership.acquire(snapshot.taskId(), KEY, instanceId, token) != 1) return null;
        return checkpoints.advanceAndGet(snapshot, instanceId, snapshot.watermark(), List.of());
    }

    @Transactional
    public EtlCheckpointSnapshot verify(
            EtlCheckpointSnapshot snapshot, Long instanceId, String token) {
        if (ownership.guard(snapshot.taskId(), KEY, token) != 1)
            throw new IllegalStateException("Message subscription ownership was lost");
        return checkpoints.advanceAndGet(snapshot, instanceId, snapshot.watermark(), List.of());
    }

    public void release(EtlCheckpointSnapshot snapshot, String token) {
        ownership.release(snapshot.taskId(), KEY, token);
    }
}
