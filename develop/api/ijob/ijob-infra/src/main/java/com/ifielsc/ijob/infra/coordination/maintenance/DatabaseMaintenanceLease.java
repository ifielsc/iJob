// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.infra.coordination.maintenance;

import com.ifielsc.ijob.infra.mapper.coordination.MaintenanceLeaseMapper;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.UUID;
import java.util.function.Supplier;

@Component
@RequiredArgsConstructor
public class DatabaseMaintenanceLease {
    private final MaintenanceLeaseMapper mapper;

    @Transactional
    public MaintenanceLeaseClaim claim(
            String leaseName, String leaseOwner, Duration leaseDuration) {
        requireText(leaseName, 64, "Lease name");
        requireText(leaseOwner, 128, "Lease owner");
        String leaseToken = UUID.randomUUID().toString();
        mapper.insertIfAbsent(leaseName);
        if (mapper.claim(leaseName, leaseOwner, leaseToken, micros(leaseDuration)) != 1) {
            return null;
        }
        MaintenanceLease lease = mapper.selectByToken(leaseToken);
        if (lease == null || !leaseName.equals(lease.getLeaseName())) {
            throw new IllegalStateException("Claimed maintenance lease cannot be read back");
        }
        return new MaintenanceLeaseClaim(
                lease.getLeaseName(),
                lease.getLeaseOwner(),
                lease.getLeaseToken(),
                lease.getFenceToken(),
                lease.getLeaseDeadline());
    }

    @Transactional
    public boolean renew(MaintenanceLeaseClaim claim, Duration leaseDuration) {
        return mapper.renew(
                        claim.leaseName(),
                        claim.leaseOwner(),
                        claim.leaseToken(),
                        claim.fenceToken(),
                        micros(leaseDuration))
                == 1;
    }

    @Transactional
    public <T> GuardedResult<T> runIfHeld(MaintenanceLeaseClaim claim, Supplier<T> action) {
        Long fence =
                mapper.lockIfHeld(
                        claim.leaseName(),
                        claim.leaseOwner(),
                        claim.leaseToken(),
                        claim.fenceToken());
        if (fence == null || fence != claim.fenceToken()) {
            return GuardedResult.notHeld();
        }
        T value = action.get();
        if (mapper.markCompleted(
                        claim.leaseName(),
                        claim.leaseOwner(),
                        claim.leaseToken(),
                        claim.fenceToken())
                != 1) {
            throw new IllegalStateException("Maintenance lease completion lost ownership");
        }
        return GuardedResult.held(value);
    }

    @Transactional
    public boolean release(MaintenanceLeaseClaim claim) {
        return mapper.release(
                        claim.leaseName(),
                        claim.leaseOwner(),
                        claim.leaseToken(),
                        claim.fenceToken())
                == 1;
    }

    private long micros(Duration duration) {
        if (duration == null || duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException("Lease duration must be greater than zero");
        }
        try {
            return Math.multiplyExact(duration.toMillis(), 1_000L);
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("Lease duration is too large", exception);
        }
    }

    private void requireText(String value, int maxLength, String name) {
        if (value == null || value.isBlank() || value.length() > maxLength) {
            throw new IllegalArgumentException(name + " is invalid");
        }
    }

    public record GuardedResult<T>(boolean held, T value) {
        public static <T> GuardedResult<T> held(T value) {
            return new GuardedResult<>(true, value);
        }

        public static <T> GuardedResult<T> notHeld() {
            return new GuardedResult<>(false, null);
        }
    }
}
