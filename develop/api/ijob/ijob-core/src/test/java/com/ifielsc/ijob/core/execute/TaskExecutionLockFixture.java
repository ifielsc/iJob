// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.core.execute;

import com.ifielsc.ijob.common.config.AppProperties;
import com.ifielsc.ijob.contract.enums.PluginType;
import com.ifielsc.ijob.core.concurrency.ConcurrencyRequirement;
import com.ifielsc.ijob.core.concurrency.ConcurrencyRequirementResolver;
import com.ifielsc.ijob.core.concurrency.LockService;

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
