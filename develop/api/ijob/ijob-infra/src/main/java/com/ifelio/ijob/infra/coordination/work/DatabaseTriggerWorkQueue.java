package com.ifelio.ijob.infra.coordination.work;

import com.ifelio.ijob.common.config.AppProperties;
import com.ifelio.ijob.common.routing.ExecutionGroupCodes;
import com.ifelio.ijob.common.routing.ExecutionTargetIds;
import com.ifelio.ijob.infra.mapper.coordination.TriggerWorkItemMapper;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class DatabaseTriggerWorkQueue {
    private final TriggerWorkItemMapper mapper;
    private final AppProperties appProperties;

    @Transactional(propagation = Propagation.MANDATORY)
    public void enqueue(Long triggerId) {
        enqueue(triggerId, null);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void enqueue(Long triggerId, java.time.LocalDateTime expiresAt) {
        requirePositive(triggerId, "Trigger id");
        mapper.insertIfAbsent(
                triggerId, appProperties.getRuntime().getWorkQueue().getMaxAttempts(), expiresAt);
        if (mapper.selectByTriggerId(triggerId) == null) {
            throw new IllegalStateException("Trigger work item creation failed");
        }
    }

    public List<Long> listDispatchableTriggerIds() {
        return listDispatchableTriggerIds(
                appProperties.getRuntime().getWorkQueue().getScanBatchSize());
    }

    public List<Long> listDispatchableTriggerIds(int limit) {
        if (limit <= 0) {
            return List.of();
        }
        mapper.markExpiredExhaustedDead();
        return List.copyOf(
                mapper.selectDispatchableTriggerIds(
                        executionGroups(),
                        allowedTaskIds(),
                        allowedJobIds(),
                        Math.min(
                                limit,
                                appProperties.getRuntime().getWorkQueue().getScanBatchSize())));
    }

    public List<Long> listTimedOutTriggerIds(int limit) {
        return List.copyOf(mapper.selectTimedOutTriggerIds(limit));
    }

    public List<Long> listExpiredTriggerIds(int limit) {
        return List.copyOf(mapper.selectExpiredTriggerIds(limit));
    }

    public List<Long> listDeadOrphanTriggerIds(Duration orphanGrace, int limit) {
        return List.copyOf(mapper.selectDeadOrphanTriggerIds(micros(orphanGrace), limit));
    }

    @Transactional
    public TriggerWorkClaim claim(Long triggerId, String leaseOwner) {
        requirePositive(triggerId, "Trigger id");
        requireOwner(leaseOwner);
        String claimToken = UUID.randomUUID().toString();
        if (mapper.claimByTriggerId(
                        triggerId,
                        leaseOwner,
                        claimToken,
                        micros(appProperties.getRuntime().getWorkQueue().getLeaseDuration()),
                        executionGroups(),
                        allowedTaskIds(),
                        allowedJobIds())
                != 1) {
            return null;
        }
        TriggerWorkItem item = mapper.selectByClaimToken(claimToken);
        if (item == null || !triggerId.equals(item.getTriggerId())) {
            throw new IllegalStateException("Claimed trigger work item cannot be read back");
        }
        return new TriggerWorkClaim(
                item.getTriggerId(),
                item.getLeaseOwner(),
                item.getClaimToken(),
                item.getFenceToken(),
                item.getAttemptCount(),
                item.getMaxAttempts(),
                item.getLeaseDeadline());
    }

    @Transactional
    public TriggerWorkClaim claimTimedOut(Long triggerId, String leaseOwner) {
        return claimForMaintenance(triggerId, leaseOwner, true, null);
    }

    @Transactional
    public TriggerWorkClaim claimExpired(Long triggerId, String leaseOwner) {
        requirePositive(triggerId, "Trigger id");
        requireOwner(leaseOwner);
        String claimToken = UUID.randomUUID().toString();
        if (mapper.claimExpired(
                        triggerId,
                        leaseOwner,
                        claimToken,
                        micros(appProperties.getRuntime().getWorkQueue().getLeaseDuration()))
                != 1) {
            return null;
        }
        TriggerWorkItem item = mapper.selectByClaimToken(claimToken);
        if (item == null || !triggerId.equals(item.getTriggerId())) {
            throw new IllegalStateException("Expired trigger work claim cannot be read back");
        }
        return new TriggerWorkClaim(
                item.getTriggerId(),
                item.getLeaseOwner(),
                item.getClaimToken(),
                item.getFenceToken(),
                item.getAttemptCount(),
                item.getMaxAttempts(),
                item.getLeaseDeadline());
    }

    @Transactional
    public TriggerWorkClaim claimDeadOrphan(
            Long triggerId, String leaseOwner, Duration orphanGrace) {
        return claimForMaintenance(triggerId, leaseOwner, false, orphanGrace);
    }

    @Transactional
    public boolean renew(TriggerWorkClaim claim) {
        return mapper.renew(
                        claim.triggerId(),
                        claim.leaseOwner(),
                        claim.claimToken(),
                        claim.fenceToken(),
                        micros(appProperties.getRuntime().getWorkQueue().getLeaseDuration()))
                == 1;
    }

    @Transactional
    public boolean complete(TriggerWorkClaim claim) {
        return mapper.complete(
                        claim.triggerId(),
                        claim.leaseOwner(),
                        claim.claimToken(),
                        claim.fenceToken())
                == 1;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void bindRoot(TriggerWorkClaim claim, Long rootInstanceId) {
        requirePositive(rootInstanceId, "Root instance id");
        if (mapper.bindRoot(
                        claim.triggerId(),
                        rootInstanceId,
                        claim.leaseOwner(),
                        claim.claimToken(),
                        claim.fenceToken())
                != 1) {
            throw new ExecutionOwnershipLostException(claim.triggerId());
        }
    }

    @Transactional
    public boolean suspend(TriggerWorkClaim claim) {
        return mapper.suspend(
                        claim.triggerId(),
                        claim.leaseOwner(),
                        claim.claimToken(),
                        claim.fenceToken())
                == 1;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public boolean resumeSuspended(SuspendedWorkPermit permit) {
        return mapper.resumeSuspended(permit.triggerId(), permit.fenceToken()) == 1;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public boolean completeSuspended(SuspendedWorkPermit permit) {
        return mapper.completeSuspended(permit.triggerId(), permit.fenceToken()) == 1;
    }

    @Transactional
    public boolean retry(TriggerWorkClaim claim, String error) {
        String summary = error == null ? null : error.substring(0, Math.min(error.length(), 500));
        return mapper.retryOrDead(
                        claim.triggerId(),
                        claim.leaseOwner(),
                        claim.claimToken(),
                        claim.fenceToken(),
                        micros(appProperties.getRuntime().getWorkQueue().getRetryDelay()),
                        summary)
                == 1;
    }

    @Transactional
    public boolean deferUnstarted(TriggerWorkClaim claim, String error) {
        String summary = error == null ? null : error.substring(0, Math.min(error.length(), 500));
        return mapper.deferUnstarted(
                        claim.triggerId(),
                        claim.leaseOwner(),
                        claim.claimToken(),
                        claim.fenceToken(),
                        micros(appProperties.getRuntime().getWorkQueue().getRetryDelay()),
                        summary)
                == 1;
    }

    public TriggerWorkItem get(Long triggerId) {
        return mapper.selectByTriggerId(triggerId);
    }

    private TriggerWorkClaim claimForMaintenance(
            Long triggerId, String leaseOwner, boolean timedOut, Duration orphanGrace) {
        requirePositive(triggerId, "Trigger id");
        requireOwner(leaseOwner);
        String claimToken = UUID.randomUUID().toString();
        int updated =
                timedOut
                        ? mapper.claimTimedOut(
                                triggerId,
                                leaseOwner,
                                claimToken,
                                micros(
                                        appProperties
                                                .getRuntime()
                                                .getWorkQueue()
                                                .getLeaseDuration()))
                        : mapper.claimDeadOrphan(
                                triggerId,
                                leaseOwner,
                                claimToken,
                                micros(
                                        appProperties
                                                .getRuntime()
                                                .getWorkQueue()
                                                .getLeaseDuration()),
                                micros(orphanGrace));
        if (updated != 1) {
            return null;
        }
        TriggerWorkItem item = mapper.selectByClaimToken(claimToken);
        if (item == null || !triggerId.equals(item.getTriggerId())) {
            throw new IllegalStateException("Maintenance trigger claim cannot be read back");
        }
        return new TriggerWorkClaim(
                item.getTriggerId(),
                item.getLeaseOwner(),
                item.getClaimToken(),
                item.getFenceToken(),
                item.getAttemptCount(),
                item.getMaxAttempts(),
                item.getLeaseDeadline());
    }

    private long micros(Duration duration) {
        try {
            return Math.multiplyExact(duration.toMillis(), 1_000L);
        } catch (ArithmeticException exception) {
            throw new IllegalStateException("Trigger work queue duration is too large", exception);
        }
    }

    private List<String> executionGroups() {
        return ExecutionGroupCodes.normalize(appProperties.getRuntime().getGroups());
    }

    private List<Long> allowedTaskIds() {
        return ExecutionTargetIds.normalize(appProperties.getRuntime().getAllowedTaskIds(), "Task");
    }

    private List<Long> allowedJobIds() {
        return ExecutionTargetIds.normalize(appProperties.getRuntime().getAllowedJobIds(), "Job");
    }

    private void requirePositive(Long value, String name) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException(name + " must be greater than zero");
        }
    }

    private void requireOwner(String owner) {
        if (owner == null || owner.isBlank() || owner.length() > 128) {
            throw new IllegalArgumentException("Lease owner is invalid");
        }
    }
}
