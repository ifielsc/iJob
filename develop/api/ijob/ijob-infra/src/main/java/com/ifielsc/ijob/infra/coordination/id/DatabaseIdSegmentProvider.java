// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.infra.coordination.id;

import com.ifielsc.ijob.infra.mapper.coordination.IdSegmentMapper;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
public class DatabaseIdSegmentProvider implements IdSegmentProvider {
    private final IdSegmentMapper idSegmentMapper;

    public DatabaseIdSegmentProvider(IdSegmentMapper idSegmentMapper) {
        this.idSegmentMapper = idSegmentMapper;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public IdSegmentRange reserve(String segmentKey, int segmentSize) {
        return allocate(segmentKey, segmentSize);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public IdSegmentRange reserveInTransaction(String segmentKey, int segmentSize) {
        return allocate(segmentKey, segmentSize);
    }

    private IdSegmentRange allocate(String segmentKey, int segmentSize) {
        if (segmentKey == null || segmentKey.isBlank() || segmentKey.length() > 64) {
            throw new IllegalArgumentException("ID segment key is invalid");
        }
        if (segmentSize < 1 || segmentSize > 100000) {
            throw new IllegalArgumentException("ID segment size is invalid");
        }

        idSegmentMapper.lockExisting(segmentKey);
        IdSegmentState state = idSegmentMapper.selectForUpdate(segmentKey);
        if (state == null || state.getNextValue() == null) {
            throw new IllegalStateException("ID segment state is missing");
        }

        long start = state.getNextValue();
        long end;
        try {
            end = Math.addExact(start, segmentSize);
        } catch (ArithmeticException exception) {
            throw new IllegalStateException("ID segment is exhausted", exception);
        }
        if (idSegmentMapper.advance(segmentKey, state.getNextValue(), end, segmentSize) != 1) {
            throw new IllegalStateException("ID segment allocation lost its database lock");
        }
        return new IdSegmentRange(start, end);
    }
}
