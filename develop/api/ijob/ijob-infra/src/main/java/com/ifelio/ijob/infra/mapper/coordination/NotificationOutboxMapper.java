package com.ifelio.ijob.infra.mapper.coordination;

import com.ifelio.ijob.infra.coordination.notification.NotificationOutboxItem;

import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface NotificationOutboxMapper {
    int insertIfAbsent(NotificationOutboxItem item);

    NotificationOutboxItem selectByDeduplicationKey(String deduplicationKey);

    NotificationOutboxItem selectByClaimToken(String claimToken);

    List<Long> selectDispatchableIds(@Param("limit") int limit);

    int claim(
            @Param("id") Long id,
            @Param("leaseOwner") String leaseOwner,
            @Param("claimToken") String claimToken,
            @Param("leaseMicros") long leaseMicros);

    int renew(
            @Param("id") Long id,
            @Param("leaseOwner") String leaseOwner,
            @Param("claimToken") String claimToken,
            @Param("leaseMicros") long leaseMicros);

    int complete(
            @Param("id") Long id,
            @Param("leaseOwner") String leaseOwner,
            @Param("claimToken") String claimToken);

    int retryOrDead(
            @Param("id") Long id,
            @Param("leaseOwner") String leaseOwner,
            @Param("claimToken") String claimToken,
            @Param("retryDelayMicros") long retryDelayMicros,
            @Param("failureCode") String failureCode,
            @Param("failureSummary") String failureSummary,
            @Param("terminal") int terminal);

    int markExpiredExhaustedDead();

    int defer(
            @Param("id") Long id,
            @Param("leaseOwner") String leaseOwner,
            @Param("claimToken") String claimToken,
            @Param("retryDelayMicros") long retryDelayMicros);

    int recordResponse(
            @Param("id") Long id,
            @Param("leaseOwner") String leaseOwner,
            @Param("claimToken") String claimToken,
            @Param("httpStatus") Integer httpStatus);
}
