package com.ifelio.ijob.core.etl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ifelio.ijob.common.exception.BusinessException;
import com.ifelio.ijob.common.response.ResultCode;
import com.ifelio.ijob.contract.enums.TargetType;
import com.ifelio.ijob.infra.coordination.work.ExecutionFenceCoordinator;
import com.ifelio.ijob.infra.coordination.work.ExecutionFencePermit;
import com.ifelio.ijob.infra.entity.runtime.EtlCheckpoint;
import com.ifelio.ijob.infra.entity.runtime.Instance;
import com.ifelio.ijob.infra.mapper.runtime.EtlCheckpointMapper;
import com.ifelio.ijob.infra.mapper.runtime.InstanceMapper;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class EtlCheckpointStore {
    private static final TypeReference<List<Object>> LIST_TYPE = new TypeReference<>() {};

    private final EtlCheckpointMapper checkpointMapper;
    private final InstanceMapper instanceMapper;
    private final ObjectMapper objectMapper;
    private final ExecutionFenceCoordinator fenceCoordinator;

    @Autowired
    public EtlCheckpointStore(
            EtlCheckpointMapper checkpointMapper,
            InstanceMapper instanceMapper,
            ObjectMapper objectMapper,
            ExecutionFenceCoordinator fenceCoordinator) {
        this.checkpointMapper = checkpointMapper;
        this.instanceMapper = instanceMapper;
        this.objectMapper = objectMapper;
        this.fenceCoordinator = fenceCoordinator;
    }

    public EtlCheckpointStore(
            EtlCheckpointMapper checkpointMapper,
            InstanceMapper instanceMapper,
            ObjectMapper objectMapper) {
        this(checkpointMapper, instanceMapper, objectMapper, null);
    }

    @Transactional
    public EtlCheckpointSnapshot loadOrCreate(
            Long instanceId,
            String checkpointKey,
            String sourceFingerprint,
            String watermarkType,
            Object initialWatermark,
            List<Object> initialTieBreakers) {
        Instance instance = instanceMapper.selectById(instanceId);
        if (instance == null
                || instance.getTargetType() != TargetType.TASK
                || instance.getTargetId() == null
                || instance.getTargetId() <= 0) {
            throw systemError("ETL task identity is unavailable");
        }
        EtlCheckpoint candidate = new EtlCheckpoint();
        candidate.setTaskId(instance.getTargetId());
        candidate.setCheckpointKey(checkpointKey);
        candidate.setSourceFingerprint(sourceFingerprint);
        candidate.setWatermarkType(watermarkType);
        candidate.setWatermarkValue(serialize(initialWatermark));
        candidate.setTieBreakerValue(serialize(initialTieBreakers));
        candidate.setLastInstanceId(instanceId);
        ExecutionFencePermit permit = permit(instance.getTriggerId());
        checkpointMapper.insertIfAbsent(candidate, permit);
        EtlCheckpoint stored = checkpointMapper.select(candidate.getTaskId(), checkpointKey);
        if (stored == null) {
            throw systemError("ETL checkpoint could not be initialized");
        }
        if (!sourceFingerprint.equals(stored.getSourceFingerprint())
                || !watermarkType.equals(stored.getWatermarkType())) {
            throw BusinessException.localized(
                    ResultCode.CONFLICT, "ETL checkpoint source has changed");
        }
        return new EtlCheckpointSnapshot(
                stored.getTaskId(),
                stored.getCheckpointKey(),
                stored.getSourceFingerprint(),
                stored.getWatermarkType(),
                deserializeValue(stored.getWatermarkValue()),
                deserializeList(stored.getTieBreakerValue()),
                stored.getVersion());
    }

    @Transactional
    public void advance(
            EtlCheckpointSnapshot snapshot,
            Long instanceId,
            Object watermark,
            List<Object> tieBreakers) {
        advanceAndGet(snapshot, instanceId, watermark, tieBreakers);
    }

    @Transactional
    public EtlCheckpointSnapshot advanceAndGet(
            EtlCheckpointSnapshot snapshot,
            Long instanceId,
            Object watermark,
            List<Object> tieBreakers) {
        EtlCheckpoint checkpoint = new EtlCheckpoint();
        checkpoint.setTaskId(snapshot.taskId());
        checkpoint.setCheckpointKey(snapshot.checkpointKey());
        checkpoint.setSourceFingerprint(snapshot.sourceFingerprint());
        checkpoint.setWatermarkType(snapshot.watermarkType());
        checkpoint.setWatermarkValue(serialize(watermark));
        checkpoint.setTieBreakerValue(serialize(tieBreakers));
        checkpoint.setLastInstanceId(instanceId);
        Instance instance = instanceMapper.selectById(instanceId);
        if (instance == null) {
            throw systemError("ETL task identity is unavailable");
        }
        ExecutionFencePermit permit = permit(instance.getTriggerId());
        if (checkpointMapper.advance(checkpoint, snapshot.version(), permit) != 1) {
            throw BusinessException.localized(
                    ResultCode.CONFLICT, "ETL checkpoint was advanced concurrently");
        }
        return new EtlCheckpointSnapshot(
                snapshot.taskId(),
                snapshot.checkpointKey(),
                snapshot.sourceFingerprint(),
                snapshot.watermarkType(),
                watermark,
                List.copyOf(tieBreakers),
                snapshot.version() + 1);
    }

    private String serialize(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw systemError("ETL checkpoint value is invalid");
        }
    }

    private Object deserializeValue(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return objectMapper
                    .readerFor(Object.class)
                    .with(
                            com.fasterxml.jackson.databind.DeserializationFeature
                                    .USE_BIG_DECIMAL_FOR_FLOATS)
                    .readValue(value);
        } catch (JsonProcessingException exception) {
            throw systemError("ETL checkpoint value is invalid");
        }
    }

    private List<Object> deserializeList(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        try {
            return List.copyOf(
                    objectMapper
                            .readerFor(LIST_TYPE)
                            .with(
                                    com.fasterxml.jackson.databind.DeserializationFeature
                                            .USE_BIG_DECIMAL_FOR_FLOATS)
                            .<List<Object>>readValue(value));
        } catch (JsonProcessingException exception) {
            throw systemError("ETL checkpoint tie breakers are invalid");
        }
    }

    private BusinessException systemError(String message) {
        return BusinessException.localized(ResultCode.SYSTEM_ERROR, message);
    }

    private ExecutionFencePermit permit(Long triggerId) {
        return fenceCoordinator == null
                ? ExecutionFencePermit.unfenced()
                : fenceCoordinator.requirePermit(triggerId);
    }
}
