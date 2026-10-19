package com.ifielsc.ijob.infra.mapper.coordination;

import com.ifielsc.ijob.infra.coordination.permit.DistributedPermit;

import org.apache.ibatis.annotations.Param;

public interface ConcurrencyPermitMapper {
    int insertSlotIfAbsent(@Param("scopeKey") String scopeKey, @Param("slotNo") int slotNo);

    int claimSlot(
            @Param("scopeKey") String scopeKey,
            @Param("slotNo") int slotNo,
            @Param("instanceId") Long instanceId,
            @Param("leaseOwner") String leaseOwner,
            @Param("permitToken") String permitToken,
            @Param("claimToken") String claimToken,
            @Param("executionFence") long executionFence,
            @Param("leaseMicros") long leaseMicros);

    DistributedPermit selectByToken(String permitToken);

    int renew(
            @Param("permitToken") String permitToken,
            @Param("leaseOwner") String leaseOwner,
            @Param("permitFence") long permitFence,
            @Param("executionFence") long executionFence,
            @Param("leaseMicros") long leaseMicros);

    int release(
            @Param("permitToken") String permitToken,
            @Param("leaseOwner") String leaseOwner,
            @Param("permitFence") long permitFence,
            @Param("executionFence") long executionFence);

    boolean isInstanceAdmissible(
            @Param("instanceId") Long instanceId,
            @Param("claimToken") String claimToken,
            @Param("executionFence") long executionFence);

    int countActiveByScope(@Param("scopeKey") String scopeKey);
}
