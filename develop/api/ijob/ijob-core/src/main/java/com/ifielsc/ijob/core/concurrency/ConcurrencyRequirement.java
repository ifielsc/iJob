// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.core.concurrency;

public record ConcurrencyRequirement(String scopeKey, int maxConcurrency, long policyEpoch)
        implements Comparable<ConcurrencyRequirement> {
    public ConcurrencyRequirement {
        if (scopeKey == null
                || scopeKey.isBlank()
                || maxConcurrency <= 0
                || maxConcurrency > 10_000) {
            throw new IllegalArgumentException("Concurrency requirement is invalid");
        }
    }

    public ConcurrencyRequirement(String scopeKey, int maxConcurrency) {
        this(scopeKey, maxConcurrency, 0L);
    }

    @Override
    public int compareTo(ConcurrencyRequirement other) {
        return scopeKey.compareTo(other.scopeKey);
    }
}
