package com.ifielsc.ijob.infra.coordination.work;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ifielsc.ijob.common.config.AppProperties;
import com.ifielsc.ijob.infra.mapper.coordination.TriggerWorkItemMapper;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

class DatabaseTriggerWorkQueueTest {
    @Test
    void shouldEnqueueClaimRenewAndCompleteExactlyOnce() {
        FakeMapper mapper = new FakeMapper();
        DatabaseTriggerWorkQueue queue = queue(mapper, 3);

        queue.enqueue(41L);
        TriggerWorkClaim claim = queue.claim(41L, "node-a");

        assertNotNull(claim);
        assertEquals(1, claim.attemptCount());
        assertEquals(1, claim.fenceToken());
        assertNull(queue.claim(41L, "node-b"));
        assertTrue(queue.renew(claim));
        assertTrue(queue.complete(claim));
        assertFalse(queue.complete(claim));
        assertEquals(TriggerWorkStatus.COMPLETED, queue.get(41L).getStatus());
    }

    @Test
    void finalFailedAttemptShouldBecomeDead() {
        FakeMapper mapper = new FakeMapper();
        DatabaseTriggerWorkQueue queue = queue(mapper, 1);
        queue.enqueue(42L);

        TriggerWorkClaim claim = queue.claim(42L, "node-a");

        assertTrue(queue.retry(claim, "failure"));
        assertEquals(TriggerWorkStatus.DEAD, queue.get(42L).getStatus());
        assertEquals("failure", queue.get(42L).getLastError());
    }

    @Test
    void unstartedDeferralShouldNotConsumeAnExecutionAttempt() {
        FakeMapper mapper = new FakeMapper();
        DatabaseTriggerWorkQueue queue = queue(mapper, 1);
        queue.enqueue(46L);

        TriggerWorkClaim claim = queue.claim(46L, "node-a");

        assertEquals(1, claim.attemptCount());
        assertTrue(queue.deferUnstarted(claim, "executor saturated"));
        assertEquals(0, queue.get(46L).getAttemptCount());
        assertEquals(TriggerWorkStatus.RETRY_WAIT, queue.get(46L).getStatus());
        assertEquals("executor saturated", queue.get(46L).getLastError());
    }

    @Test
    void suspendedWorkShouldReleaseLeaseAndRequeueWithANewFence() {
        FakeMapper mapper = new FakeMapper();
        DatabaseTriggerWorkQueue queue = queue(mapper, 3);
        queue.enqueue(43L);

        TriggerWorkClaim first = queue.claim(43L, "node-a");
        assertTrue(queue.suspend(first));
        assertEquals(TriggerWorkStatus.SUSPENDED, queue.get(43L).getStatus());
        assertFalse(queue.resumeSuspended(new SuspendedWorkPermit(43L, first.fenceToken() + 1)));
        assertTrue(queue.resumeSuspended(new SuspendedWorkPermit(43L, first.fenceToken())));
        TriggerWorkClaim second = queue.claim(43L, "node-b");

        assertEquals(first.fenceToken() + 1, second.fenceToken());
        assertFalse(queue.complete(first));
        assertTrue(queue.complete(second));
    }

    @Test
    void maintenanceShouldFenceAndSettleDeadWorkWithoutIncreasingAttempts() {
        FakeMapper mapper = new FakeMapper();
        DatabaseTriggerWorkQueue queue = queue(mapper, 1);
        queue.enqueue(44L);
        TriggerWorkClaim execution = queue.claim(44L, "node-a");
        assertTrue(queue.retry(execution, "failed"));

        TriggerWorkClaim recovery =
                queue.claimDeadOrphan(44L, "maintenance", Duration.ofMinutes(1));

        assertNotNull(recovery);
        assertEquals(1, recovery.attemptCount());
        assertEquals(execution.fenceToken() + 1, recovery.fenceToken());
        assertTrue(queue.complete(recovery));
    }

    @Test
    void shouldPassCanonicalWorkerGroupsToBothScanAndAtomicClaim() {
        FakeMapper mapper = new FakeMapper();
        AppProperties properties = new AppProperties();
        properties.getRuntime().setGroups(List.of(" GPU ", "cpu", "gpu"));
        properties.getRuntime().setAllowedTaskIds(List.of(9L, 3L, 9L));
        properties.getRuntime().setAllowedJobIds(List.of(20L));
        DatabaseTriggerWorkQueue queue = new DatabaseTriggerWorkQueue(mapper, properties);
        queue.enqueue(45L);

        queue.listDispatchableTriggerIds();
        queue.claim(45L, "node-a");

        assertEquals(List.of("cpu", "gpu"), mapper.scannedGroups);
        assertEquals(List.of("cpu", "gpu"), mapper.claimedGroups);
        assertEquals(List.of(3L, 9L), mapper.scannedTaskIds);
        assertEquals(List.of(20L), mapper.scannedJobIds);
        assertEquals(List.of(3L, 9L), mapper.claimedTaskIds);
        assertEquals(List.of(20L), mapper.claimedJobIds);
    }

    private DatabaseTriggerWorkQueue queue(FakeMapper mapper, int maxAttempts) {
        AppProperties properties = new AppProperties();
        properties.getRuntime().getWorkQueue().setMaxAttempts(maxAttempts);
        return new DatabaseTriggerWorkQueue(mapper, properties);
    }

    private static final class FakeMapper implements TriggerWorkItemMapper {
        private TriggerWorkItem item;
        private List<String> scannedGroups;
        private List<String> claimedGroups;
        private List<Long> scannedTaskIds;
        private List<Long> scannedJobIds;
        private List<Long> claimedTaskIds;
        private List<Long> claimedJobIds;

        @Override
        public int insertIfAbsent(Long triggerId, int maxAttempts, LocalDateTime expiresAt) {
            if (item != null) {
                return 0;
            }
            item = new TriggerWorkItem();
            item.setTriggerId(triggerId);
            item.setStatus(TriggerWorkStatus.READY);
            item.setAttemptCount(0);
            item.setMaxAttempts(maxAttempts);
            item.setExpiresAt(expiresAt);
            return 1;
        }

        @Override
        public TriggerWorkItem selectByTriggerId(Long triggerId) {
            return item != null && item.getTriggerId().equals(triggerId) ? item : null;
        }

        @Override
        public List<Long> selectDispatchableTriggerIds(
                List<String> instanceGroups,
                List<Long> allowedTaskIds,
                List<Long> allowedJobIds,
                int limit) {
            scannedGroups = instanceGroups;
            scannedTaskIds = allowedTaskIds;
            scannedJobIds = allowedJobIds;
            return item != null && item.getStatus() == TriggerWorkStatus.READY
                    ? List.of(item.getTriggerId())
                    : List.of();
        }

        @Override
        public List<Long> selectTimedOutTriggerIds(int limit) {
            return List.of();
        }

        @Override
        public List<Long> selectExpiredTriggerIds(int limit) {
            return List.of();
        }

        @Override
        public List<Long> selectDeadOrphanTriggerIds(long orphanGraceMicros, int limit) {
            return item != null && item.getStatus() == TriggerWorkStatus.DEAD
                    ? List.of(item.getTriggerId())
                    : List.of();
        }

        @Override
        public int claimByTriggerId(
                Long triggerId,
                String leaseOwner,
                String claimToken,
                long leaseMicros,
                List<String> instanceGroups,
                List<Long> allowedTaskIds,
                List<Long> allowedJobIds) {
            claimedGroups = instanceGroups;
            claimedTaskIds = allowedTaskIds;
            claimedJobIds = allowedJobIds;
            if (item == null || item.getStatus() != TriggerWorkStatus.READY) {
                return 0;
            }
            item.setStatus(TriggerWorkStatus.CLAIMED);
            item.setLeaseOwner(leaseOwner);
            item.setClaimToken(claimToken);
            item.setLeaseDeadline(LocalDateTime.now().plusSeconds(30));
            item.setAttemptCount(item.getAttemptCount() + 1);
            item.setFenceToken(item.getFenceToken() + 1);
            return 1;
        }

        @Override
        public int claimTimedOut(
                Long triggerId, String leaseOwner, String claimToken, long leaseMicros) {
            return claimMaintenance(triggerId, leaseOwner, claimToken, TriggerWorkStatus.SUSPENDED);
        }

        @Override
        public int claimExpired(
                Long triggerId, String leaseOwner, String claimToken, long leaseMicros) {
            return claimMaintenance(triggerId, leaseOwner, claimToken, TriggerWorkStatus.READY);
        }

        @Override
        public int claimDeadOrphan(
                Long triggerId,
                String leaseOwner,
                String claimToken,
                long leaseMicros,
                long orphanGraceMicros) {
            return claimMaintenance(triggerId, leaseOwner, claimToken, TriggerWorkStatus.DEAD);
        }

        @Override
        public TriggerWorkItem selectByClaimToken(String claimToken) {
            return item != null && claimToken.equals(item.getClaimToken()) ? item : null;
        }

        @Override
        public int renew(
                Long triggerId,
                String leaseOwner,
                String claimToken,
                long fenceToken,
                long leaseMicros) {
            return owned(triggerId, leaseOwner, claimToken) && item.getFenceToken() == fenceToken
                    ? 1
                    : 0;
        }

        @Override
        public int complete(Long triggerId, String leaseOwner, String claimToken, long fenceToken) {
            if (!owned(triggerId, leaseOwner, claimToken) || item.getFenceToken() != fenceToken) {
                return 0;
            }
            release(TriggerWorkStatus.COMPLETED, null);
            return 1;
        }

        @Override
        public int bindRoot(
                Long triggerId,
                Long rootInstanceId,
                String leaseOwner,
                String claimToken,
                long fenceToken) {
            if (!owned(triggerId, leaseOwner, claimToken) || item.getFenceToken() != fenceToken) {
                return 0;
            }
            item.setRootInstanceId(rootInstanceId);
            return 1;
        }

        @Override
        public int suspend(Long triggerId, String leaseOwner, String claimToken, long fenceToken) {
            if (!owned(triggerId, leaseOwner, claimToken) || item.getFenceToken() != fenceToken) {
                return 0;
            }
            release(TriggerWorkStatus.SUSPENDED, null);
            return 1;
        }

        @Override
        public int resumeSuspended(Long triggerId, long fenceToken) {
            if (item == null
                    || item.getStatus() != TriggerWorkStatus.SUSPENDED
                    || item.getFenceToken() != fenceToken) {
                return 0;
            }
            item.setStatus(TriggerWorkStatus.READY);
            return 1;
        }

        @Override
        public int completeSuspended(Long triggerId, long fenceToken) {
            if (item == null
                    || item.getStatus() != TriggerWorkStatus.SUSPENDED
                    || item.getFenceToken() != fenceToken) {
                return 0;
            }
            item.setStatus(TriggerWorkStatus.COMPLETED);
            return 1;
        }

        @Override
        public int retryOrDead(
                Long triggerId,
                String leaseOwner,
                String claimToken,
                long fenceToken,
                long delayMicros,
                String lastError) {
            if (!owned(triggerId, leaseOwner, claimToken) || item.getFenceToken() != fenceToken) {
                return 0;
            }
            TriggerWorkStatus next =
                    item.getAttemptCount() >= item.getMaxAttempts()
                            ? TriggerWorkStatus.DEAD
                            : TriggerWorkStatus.RETRY_WAIT;
            release(next, lastError);
            return 1;
        }

        @Override
        public int deferUnstarted(
                Long triggerId,
                String leaseOwner,
                String claimToken,
                long fenceToken,
                long delayMicros,
                String lastError) {
            if (!owned(triggerId, leaseOwner, claimToken) || item.getFenceToken() != fenceToken) {
                return 0;
            }
            item.setAttemptCount(Math.max(0, item.getAttemptCount() - 1));
            release(TriggerWorkStatus.RETRY_WAIT, lastError);
            return 1;
        }

        @Override
        public int markExpiredExhaustedDead() {
            return 0;
        }

        private boolean owned(Long triggerId, String leaseOwner, String claimToken) {
            return item != null
                    && item.getStatus() == TriggerWorkStatus.CLAIMED
                    && triggerId.equals(item.getTriggerId())
                    && leaseOwner.equals(item.getLeaseOwner())
                    && claimToken.equals(item.getClaimToken());
        }

        private int claimMaintenance(
                Long triggerId,
                String leaseOwner,
                String claimToken,
                TriggerWorkStatus expectedStatus) {
            if (item == null || item.getStatus() != expectedStatus) {
                return 0;
            }
            item.setStatus(TriggerWorkStatus.CLAIMED);
            item.setLeaseOwner(leaseOwner);
            item.setClaimToken(claimToken);
            item.setLeaseDeadline(LocalDateTime.now().plusSeconds(30));
            item.setFenceToken(item.getFenceToken() + 1);
            return 1;
        }

        private void release(TriggerWorkStatus status, String error) {
            item.setStatus(status);
            item.setLeaseOwner(null);
            item.setClaimToken(null);
            item.setLeaseDeadline(null);
            item.setLastError(error);
        }
    }
}
