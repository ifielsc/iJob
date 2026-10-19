// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.common.routing;

import java.util.Collection;
import java.util.List;
import java.util.TreeSet;

public final class ExecutionTargetIds {
    public static final int MAX_IDS_PER_TYPE = 100;

    private ExecutionTargetIds() {}

    public static List<Long> normalize(Collection<Long> values, String targetName) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        TreeSet<Long> normalized = new TreeSet<>();
        for (Long value : values) {
            if (value == null || value <= 0) {
                throw new IllegalArgumentException(
                        targetName + " execution target id must be greater than zero");
            }
            normalized.add(value);
        }
        if (normalized.size() > MAX_IDS_PER_TYPE) {
            throw new IllegalArgumentException(
                    "At most "
                            + MAX_IDS_PER_TYPE
                            + " "
                            + targetName
                            + " execution target ids are allowed");
        }
        return List.copyOf(normalized);
    }
}
