// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.infra.coordination.id;

public record IdSegmentRange(long startInclusive, long endExclusive) {
    public IdSegmentRange {
        if (startInclusive < 1 || endExclusive <= startInclusive) {
            throw new IllegalArgumentException("ID segment range is invalid");
        }
    }
}
