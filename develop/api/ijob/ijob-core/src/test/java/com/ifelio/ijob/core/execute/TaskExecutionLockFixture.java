package com.ifelio.ijob.core.execute;

import com.ifelio.ijob.common.config.AppProperties;
import com.ifelio.ijob.contract.enums.PluginType;
import com.ifelio.ijob.core.concurrency.ConcurrencyRequirement;
import com.ifelio.ijob.core.concurrency.ConcurrencyRequirementResolver;
import com.ifelio.ijob.core.concurrency.LockService;

import java.util.List;
import java.util.Optional;

/** Only task-runner unit tests explicitly opt out of policy requirements. */
final class TaskExecutionLockFixture {
    private TaskExecutionLockFixture() {}

    static LockService withoutPolicies() {
        var resolver =
                new ConcurrencyRequirementResolver(null, null) {
                    @Override
                    public List<ConcurrencyRequirement> resolve(
                            Long taskId, PluginType pluginType) {
                        return List.of();
                    }
                };
        return new LockService(
                null,
                null,
                new ExecutionManager(),
                new AppProperties(),
                Optional.empty(),
                resolver);
    }
}
