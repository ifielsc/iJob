// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.infra.mapper.coordination;

import com.ifielsc.ijob.infra.coordination.id.IdSegmentState;

import org.apache.ibatis.annotations.Param;

public interface IdSegmentMapper {
    int lockExisting(@Param("segmentKey") String segmentKey);

    IdSegmentState selectForUpdate(@Param("segmentKey") String segmentKey);

    int advance(
            @Param("segmentKey") String segmentKey,
            @Param("expectedNextValue") long expectedNextValue,
            @Param("nextValue") long nextValue,
            @Param("segmentSize") int segmentSize);
}
