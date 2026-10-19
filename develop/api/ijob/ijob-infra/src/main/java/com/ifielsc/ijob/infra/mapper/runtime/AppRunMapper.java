package com.ifielsc.ijob.infra.mapper.runtime;

import org.apache.ibatis.annotations.Param;

public interface AppRunMapper {
    int markStaleActiveRunsCrashed(
            @Param("excludeRunId") String excludeRunId,
            @Param("staleMicros") long staleMicros,
            @Param("limit") int limit);

    int insertStarting(
            @Param("runId") String runId,
            @Param("instanceName") String instanceName,
            @Param("runtimeMode") String runtimeMode,
            @Param("roles") String roles,
            @Param("workerGroups") String workerGroups,
            @Param("workerTaskIds") String workerTaskIds,
            @Param("workerJobIds") String workerJobIds,
            @Param("runtimeProtocolVersion") int runtimeProtocolVersion,
            @Param("applicationVersion") String applicationVersion,
            @Param("hostName") String hostName,
            @Param("processId") Long processId,
            @Param("startedAt") java.time.LocalDateTime startedAt);

    int markRunning(@Param("runId") String runId);

    int heartbeat(@Param("runId") String runId);

    int markDraining(@Param("runId") String runId);

    int markStopped(@Param("runId") String runId);
}
