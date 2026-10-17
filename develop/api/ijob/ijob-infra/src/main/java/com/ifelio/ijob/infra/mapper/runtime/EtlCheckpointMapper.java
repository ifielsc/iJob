package com.ifelio.ijob.infra.mapper.runtime;

import com.ifelio.ijob.infra.coordination.work.ExecutionFencePermit;
import com.ifelio.ijob.infra.entity.runtime.EtlCheckpoint;

import org.apache.ibatis.annotations.Param;

public interface EtlCheckpointMapper {
    EtlCheckpoint select(
            @Param("taskId") Long taskId, @Param("checkpointKey") String checkpointKey);

    int insertIfAbsent(
            @Param("checkpoint") EtlCheckpoint checkpoint,
            @Param("permit") ExecutionFencePermit permit);

    int advance(
            @Param("checkpoint") EtlCheckpoint checkpoint,
            @Param("expectedVersion") Long expectedVersion,
            @Param("permit") ExecutionFencePermit permit);
}
