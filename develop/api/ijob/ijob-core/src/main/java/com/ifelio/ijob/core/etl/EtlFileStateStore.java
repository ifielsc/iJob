package com.ifelio.ijob.core.etl;

import com.ifelio.ijob.common.exception.BusinessException;
import com.ifelio.ijob.common.response.ResultCode;
import com.ifelio.ijob.infra.mapper.runtime.EtlFileStateMapper;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Serializes file runs before target writes; completion and watermark commit atomically. */
@Service
@RequiredArgsConstructor
public class EtlFileStateStore {
    private final EtlFileStateMapper mapper;
    private final EtlCheckpointStore checkpoints;

    @Transactional
    public EtlCheckpointSnapshot acquire(
            EtlCheckpointSnapshot snapshot, Long instanceId, String token) {
        require(mapper.acquire(snapshot.taskId(), snapshot.checkpointKey(), instanceId, token));
        return checkpoints.advanceAndGet(
                snapshot, instanceId, snapshot.watermark(), snapshot.tieBreakers());
    }

    public String digest(EtlCheckpointSnapshot snapshot, String pathHash) {
        return mapper.digest(snapshot.taskId(), snapshot.checkpointKey(), pathHash);
    }

    @Transactional
    public EtlCheckpointSnapshot verify(
            EtlCheckpointSnapshot snapshot, Long instanceId, String token) {
        require(mapper.guard(snapshot.taskId(), snapshot.checkpointKey(), token));
        return checkpoints.advanceAndGet(
                snapshot, instanceId, snapshot.watermark(), snapshot.tieBreakers());
    }

    @Transactional
    public EtlCheckpointSnapshot complete(
            EtlCheckpointSnapshot snapshot,
            Long instanceId,
            String token,
            String pathHash,
            String digest,
            String marker,
            Object watermark) {
        require(mapper.guard(snapshot.taskId(), snapshot.checkpointKey(), token));
        EtlCheckpointSnapshot next =
                checkpoints.advanceAndGet(snapshot, instanceId, watermark, List.of());
        mapper.deleteRecord(snapshot.taskId(), snapshot.checkpointKey(), pathHash);
        require(
                mapper.insertRecord(
                        snapshot.taskId(),
                        snapshot.checkpointKey(),
                        pathHash,
                        digest,
                        marker,
                        instanceId));
        return next;
    }

    public void release(EtlCheckpointSnapshot snapshot, String token) {
        mapper.release(snapshot.taskId(), snapshot.checkpointKey(), token);
    }

    private void require(int count) {
        if (count != 1)
            throw BusinessException.localized(
                    ResultCode.CONFLICT, "ETL file checkpoint is owned by another execution");
    }
}
