package com.ifelio.ijob.infra.coordination.permit;

import com.ifelio.ijob.common.config.AppProperties;
import com.ifelio.ijob.infra.coordination.work.ExecutionFenceCoordinator;
import com.ifelio.ijob.infra.coordination.work.ExecutionFencePermit;
import com.ifelio.ijob.infra.coordination.work.TriggerWorkClaim;
import com.ifelio.ijob.infra.entity.runtime.Instance;
import com.ifelio.ijob.infra.mapper.coordination.ConcurrencyPermitMapper;
import com.ifelio.ijob.infra.mapper.runtime.InstanceMapper;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class DatabaseConcurrencyPermitService {
    private final ConcurrencyPermitMapper mapper;
    private final InstanceMapper instanceMapper;
    private final ExecutionFenceCoordinator fenceCoordinator;
    private final AppProperties appProperties;

    @Transactional
    public DistributedPermit tryAcquire(Long instanceId, String scopeKey, int maxConcurrency) {
        List<DistributedPermit> permits =
                tryAcquireAll(instanceId, List.of(new PermitRequirement(scopeKey, maxConcurrency)));
        return permits == null ? null : permits.getFirst();
    }

    @Transactional
    public List<DistributedPermit> tryAcquireAll(
            Long instanceId, List<PermitRequirement> requirements) {
        if (requirements == null || requirements.isEmpty()) {
            return List.of();
        }
        List<PermitRequirement> ordered =
                requirements.stream().sorted(Comparator.naturalOrder()).toList();
        ordered.forEach(
                requirement -> requireScope(requirement.scopeKey(), requirement.maxConcurrency()));
        Instance instance = instanceMapper.selectById(instanceId);
        if (instance == null || instance.getTriggerId() == null) {
            throw new IllegalArgumentException("Concurrency permit instance is invalid");
        }
        TriggerWorkClaim claim = fenceCoordinator.activeClaim(instance.getTriggerId());
        if (claim == null) {
            return null;
        }
        List<DistributedPermit> acquired = new ArrayList<>();
        for (PermitRequirement requirement : ordered) {
            DistributedPermit permit = tryAcquireOne(instanceId, claim, requirement);
            if (permit == null) {
                for (int index = acquired.size() - 1; index >= 0; index--) {
                    DistributedPermit held = acquired.get(index);
                    if (mapper.release(
                                    held.permitToken(),
                                    held.leaseOwner(),
                                    held.permitFence(),
                                    held.executionFence())
                            != 1) {
                        throw new IllegalStateException(
                                "Partial concurrency permit rollback failed");
                    }
                }
                return null;
            }
            acquired.add(permit);
        }
        return List.copyOf(acquired);
    }

    private DistributedPermit tryAcquireOne(
            Long instanceId, TriggerWorkClaim claim, PermitRequirement requirement) {
        for (int slot = 1; slot <= requirement.maxConcurrency(); slot++) {
            mapper.insertSlotIfAbsent(requirement.scopeKey(), slot);
            String permitToken = UUID.randomUUID().toString();
            if (mapper.claimSlot(
                            requirement.scopeKey(),
                            slot,
                            instanceId,
                            claim.leaseOwner(),
                            permitToken,
                            claim.claimToken(),
                            claim.fenceToken(),
                            micros(
                                    appProperties
                                            .getRuntime()
                                            .getConcurrencyPermit()
                                            .getLeaseDuration()))
                    == 1) {
                DistributedPermit permit = mapper.selectByToken(permitToken);
                if (permit == null) {
                    throw new IllegalStateException(
                            "Claimed concurrency permit cannot be read back");
                }
                return permit;
            }
        }
        return null;
    }

    public boolean isAdmissible(Long instanceId) {
        Instance instance = instanceMapper.selectById(instanceId);
        if (instance == null || instance.getTriggerId() == null) {
            return false;
        }
        ExecutionFencePermit permit = fenceCoordinator.requirePermit(instance.getTriggerId());
        if (!permit.isClaimed()) {
            return false;
        }
        return mapper.isInstanceAdmissible(instanceId, permit.claimToken(), permit.fenceToken());
    }

    @Transactional
    public boolean renew(DistributedPermit permit) {
        return mapper.renew(
                        permit.permitToken(),
                        permit.leaseOwner(),
                        permit.permitFence(),
                        permit.executionFence(),
                        micros(
                                appProperties
                                        .getRuntime()
                                        .getConcurrencyPermit()
                                        .getLeaseDuration()))
                == 1;
    }

    @Transactional
    public boolean release(DistributedPermit permit) {
        return mapper.release(
                        permit.permitToken(),
                        permit.leaseOwner(),
                        permit.permitFence(),
                        permit.executionFence())
                == 1;
    }

    private void requireScope(String scopeKey, int maxConcurrency) {
        if (scopeKey == null
                || scopeKey.isBlank()
                || scopeKey.length() > 191
                || maxConcurrency <= 0
                || maxConcurrency > 10_000) {
            throw new IllegalArgumentException("Concurrency permit scope is invalid");
        }
    }

    private long micros(Duration duration) {
        try {
            return Math.multiplyExact(duration.toMillis(), 1_000L);
        } catch (ArithmeticException exception) {
            throw new IllegalStateException("Concurrency permit duration is too large", exception);
        }
    }
}
