package com.ifelio.ijob.infra.mapper.coordination;

import com.ifelio.ijob.infra.coordination.id.IdSegmentState;

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
