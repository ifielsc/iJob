package com.ifelio.ijob.infra.mapper.coordination;

import com.ifelio.ijob.infra.coordination.maintenance.MaintenanceLease;

import org.apache.ibatis.annotations.Param;

public interface MaintenanceLeaseMapper {
    int insertIfAbsent(@Param("leaseName") String leaseName);

    int claim(
            @Param("leaseName") String leaseName,
            @Param("leaseOwner") String leaseOwner,
            @Param("leaseToken") String leaseToken,
            @Param("leaseMicros") long leaseMicros);

    MaintenanceLease selectByToken(@Param("leaseToken") String leaseToken);

    int renew(
            @Param("leaseName") String leaseName,
            @Param("leaseOwner") String leaseOwner,
            @Param("leaseToken") String leaseToken,
            @Param("fenceToken") long fenceToken,
            @Param("leaseMicros") long leaseMicros);

    Long lockIfHeld(
            @Param("leaseName") String leaseName,
            @Param("leaseOwner") String leaseOwner,
            @Param("leaseToken") String leaseToken,
            @Param("fenceToken") long fenceToken);

    int markCompleted(
            @Param("leaseName") String leaseName,
            @Param("leaseOwner") String leaseOwner,
            @Param("leaseToken") String leaseToken,
            @Param("fenceToken") long fenceToken);

    int release(
            @Param("leaseName") String leaseName,
            @Param("leaseOwner") String leaseOwner,
            @Param("leaseToken") String leaseToken,
            @Param("fenceToken") long fenceToken);
}
