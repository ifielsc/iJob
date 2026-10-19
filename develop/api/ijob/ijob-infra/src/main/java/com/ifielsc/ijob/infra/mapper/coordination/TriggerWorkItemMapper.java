package com.ifielsc.ijob.infra.mapper.coordination;

import com.ifielsc.ijob.infra.coordination.work.TriggerWorkItem;

import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface TriggerWorkItemMapper {
    int insertIfAbsent(
            @Param("triggerId") Long triggerId,
            @Param("maxAttempts") int maxAttempts,
            @Param("expiresAt") java.time.LocalDateTime expiresAt);

    TriggerWorkItem selectByTriggerId(Long triggerId);

    List<Long> selectDispatchableTriggerIds(
            @Param("instanceGroups") List<String> instanceGroups,
            @Param("allowedTaskIds") List<Long> allowedTaskIds,
            @Param("allowedJobIds") List<Long> allowedJobIds,
            @Param("limit") int limit);

    List<Long> selectTimedOutTriggerIds(@Param("limit") int limit);

    List<Long> selectExpiredTriggerIds(@Param("limit") int limit);

    List<Long> selectDeadOrphanTriggerIds(
            @Param("orphanGraceMicros") long orphanGraceMicros, @Param("limit") int limit);

    int claimByTriggerId(
            @Param("triggerId") Long triggerId,
            @Param("leaseOwner") String leaseOwner,
            @Param("claimToken") String claimToken,
            @Param("leaseMicros") long leaseMicros,
            @Param("instanceGroups") List<String> instanceGroups,
            @Param("allowedTaskIds") List<Long> allowedTaskIds,
            @Param("allowedJobIds") List<Long> allowedJobIds);

    int claimTimedOut(
            @Param("triggerId") Long triggerId,
            @Param("leaseOwner") String leaseOwner,
            @Param("claimToken") String claimToken,
            @Param("leaseMicros") long leaseMicros);

    int claimExpired(
            @Param("triggerId") Long triggerId,
            @Param("leaseOwner") String leaseOwner,
            @Param("claimToken") String claimToken,
            @Param("leaseMicros") long leaseMicros);

    int claimDeadOrphan(
            @Param("triggerId") Long triggerId,
            @Param("leaseOwner") String leaseOwner,
            @Param("claimToken") String claimToken,
            @Param("leaseMicros") long leaseMicros,
            @Param("orphanGraceMicros") long orphanGraceMicros);

    TriggerWorkItem selectByClaimToken(String claimToken);

    int renew(
            @Param("triggerId") Long triggerId,
            @Param("leaseOwner") String leaseOwner,
            @Param("claimToken") String claimToken,
            @Param("fenceToken") long fenceToken,
            @Param("leaseMicros") long leaseMicros);

    int complete(
            @Param("triggerId") Long triggerId,
            @Param("leaseOwner") String leaseOwner,
            @Param("claimToken") String claimToken,
            @Param("fenceToken") long fenceToken);

    int bindRoot(
            @Param("triggerId") Long triggerId,
            @Param("rootInstanceId") Long rootInstanceId,
            @Param("leaseOwner") String leaseOwner,
            @Param("claimToken") String claimToken,
            @Param("fenceToken") long fenceToken);

    int suspend(
            @Param("triggerId") Long triggerId,
            @Param("leaseOwner") String leaseOwner,
            @Param("claimToken") String claimToken,
            @Param("fenceToken") long fenceToken);

    int resumeSuspended(@Param("triggerId") Long triggerId, @Param("fenceToken") long fenceToken);

    int completeSuspended(@Param("triggerId") Long triggerId, @Param("fenceToken") long fenceToken);

    int retryOrDead(
            @Param("triggerId") Long triggerId,
            @Param("leaseOwner") String leaseOwner,
            @Param("claimToken") String claimToken,
            @Param("fenceToken") long fenceToken,
            @Param("delayMicros") long delayMicros,
            @Param("lastError") String lastError);

    int deferUnstarted(
            @Param("triggerId") Long triggerId,
            @Param("leaseOwner") String leaseOwner,
            @Param("claimToken") String claimToken,
            @Param("fenceToken") long fenceToken,
            @Param("delayMicros") long delayMicros,
            @Param("lastError") String lastError);

    int markExpiredExhaustedDead();
}
