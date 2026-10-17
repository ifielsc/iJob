package com.ifelio.ijob.infra.coordination.maintenance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ifelio.ijob.infra.mapper.coordination.MaintenanceLeaseMapper;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;

class DatabaseMaintenanceLeaseTest {
    @Test
    void shouldClaimGuardRenewReleaseAndFenceTakeover() {
        FakeMapper mapper = new FakeMapper();
        DatabaseMaintenanceLease leases = new DatabaseMaintenanceLease(mapper);

        MaintenanceLeaseClaim first =
                leases.claim("runtime-maintenance", "node-a", Duration.ofSeconds(30));

        assertNotNull(first);
        assertEquals(1, first.fenceToken());
        assertNull(leases.claim("runtime-maintenance", "node-b", Duration.ofSeconds(30)));
        assertTrue(leases.renew(first, Duration.ofSeconds(30)));
        DatabaseMaintenanceLease.GuardedResult<String> result =
                leases.runIfHeld(first, () -> "completed");
        assertTrue(result.held());
        assertEquals("completed", result.value());
        assertTrue(leases.release(first));

        MaintenanceLeaseClaim second =
                leases.claim("runtime-maintenance", "node-b", Duration.ofSeconds(30));
        assertNotNull(second);
        assertEquals(2, second.fenceToken());
        assertFalse(leases.runIfHeld(first, () -> "stale").held());
    }

    private static final class FakeMapper implements MaintenanceLeaseMapper {
        private final MaintenanceLease lease = new MaintenanceLease();

        @Override
        public int insertIfAbsent(String leaseName) {
            if (lease.getLeaseName() == null) {
                lease.setLeaseName(leaseName);
            }
            return 1;
        }

        @Override
        public int claim(String leaseName, String leaseOwner, String leaseToken, long leaseMicros) {
            if (lease.getLeaseOwner() != null) {
                return 0;
            }
            lease.setLeaseOwner(leaseOwner);
            lease.setLeaseToken(leaseToken);
            lease.setLeaseDeadline(LocalDateTime.now().plusSeconds(30));
            lease.setFenceToken(lease.getFenceToken() + 1);
            return 1;
        }

        @Override
        public MaintenanceLease selectByToken(String leaseToken) {
            return leaseToken.equals(lease.getLeaseToken()) ? lease : null;
        }

        @Override
        public int renew(
                String leaseName,
                String leaseOwner,
                String leaseToken,
                long fenceToken,
                long leaseMicros) {
            return owned(leaseName, leaseOwner, leaseToken, fenceToken) ? 1 : 0;
        }

        @Override
        public Long lockIfHeld(
                String leaseName, String leaseOwner, String leaseToken, long fenceToken) {
            return owned(leaseName, leaseOwner, leaseToken, fenceToken) ? fenceToken : null;
        }

        @Override
        public int markCompleted(
                String leaseName, String leaseOwner, String leaseToken, long fenceToken) {
            return owned(leaseName, leaseOwner, leaseToken, fenceToken) ? 1 : 0;
        }

        @Override
        public int release(
                String leaseName, String leaseOwner, String leaseToken, long fenceToken) {
            if (!owned(leaseName, leaseOwner, leaseToken, fenceToken)) {
                return 0;
            }
            lease.setLeaseOwner(null);
            lease.setLeaseToken(null);
            lease.setLeaseDeadline(null);
            return 1;
        }

        private boolean owned(
                String leaseName, String leaseOwner, String leaseToken, long fenceToken) {
            return leaseName.equals(lease.getLeaseName())
                    && leaseOwner.equals(lease.getLeaseOwner())
                    && leaseToken.equals(lease.getLeaseToken())
                    && fenceToken == lease.getFenceToken();
        }
    }
}
