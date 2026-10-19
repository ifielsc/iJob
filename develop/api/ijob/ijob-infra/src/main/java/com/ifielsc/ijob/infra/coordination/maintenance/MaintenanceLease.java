package com.ifielsc.ijob.infra.coordination.maintenance;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class MaintenanceLease {
    private String leaseName;
    private String leaseOwner;
    private String leaseToken;
    private LocalDateTime leaseDeadline;
    private long fenceToken;
    private LocalDateTime lastAcquiredAt;
    private LocalDateTime lastCompletedAt;
    private Boolean lastSuccess;
    private String lastFailureCode;
}
