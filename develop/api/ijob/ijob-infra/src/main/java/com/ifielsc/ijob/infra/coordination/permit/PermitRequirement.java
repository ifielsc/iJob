// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.infra.coordination.permit;

public record PermitRequirement(String scopeKey, int maxConcurrency)
        implements Comparable<PermitRequirement> {
    @Override
    public int compareTo(PermitRequirement other) {
        return scopeKey.compareTo(other.scopeKey);
    }
}
