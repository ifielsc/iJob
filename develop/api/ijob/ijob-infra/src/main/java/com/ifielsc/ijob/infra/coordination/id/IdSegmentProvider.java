// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.infra.coordination.id;

@FunctionalInterface
public interface IdSegmentProvider {
    IdSegmentRange reserve(String segmentKey, int segmentSize);

    default IdSegmentRange reserveInTransaction(String segmentKey, int segmentSize) {
        throw new UnsupportedOperationException("Joined ID reservations are required");
    }
}
