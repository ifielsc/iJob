package com.ifielsc.ijob.infra.coordination.id;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.ifielsc.ijob.infra.mapper.coordination.IdSegmentMapper;

import org.junit.jupiter.api.Test;

class DatabaseIdSegmentProviderTest {
    @Test
    void shouldReserveCurrentRangeAndAdvanceDatabaseState() {
        RecordingMapper mapper = new RecordingMapper(1001L);
        DatabaseIdSegmentProvider provider = new DatabaseIdSegmentProvider(mapper);

        IdSegmentRange range = provider.reserve("platform-primary-id", 500);

        assertEquals(new IdSegmentRange(1001L, 1501L), range);
        assertEquals(1, mapper.insertCalls);

        assertEquals(1001L, mapper.expectedNextValue);
        assertEquals(1501L, mapper.nextValue);
        assertEquals(500, mapper.segmentSize);
    }

    @Test
    void shouldRejectOverflowBeforeDatabaseAdvance() {
        RecordingMapper mapper = new RecordingMapper(Long.MAX_VALUE - 5);
        DatabaseIdSegmentProvider provider = new DatabaseIdSegmentProvider(mapper);

        assertThrows(
                IllegalStateException.class, () -> provider.reserve("platform-primary-id", 100));
        assertEquals(0, mapper.advanceCalls);
    }

    @Test
    void missingSeedMustFailWithoutAllocatingOrRecreatingAnId() {
        RecordingMapper mapper = new RecordingMapper(null);
        assertThrows(
                IllegalStateException.class,
                () -> new DatabaseIdSegmentProvider(mapper).reserve("pk:ifj_task", 1));
        assertEquals(0, mapper.advanceCalls);
    }

    private static final class RecordingMapper implements IdSegmentMapper {
        private final Long currentNextValue;
        private int insertCalls;
        private int advanceCalls;
        private long expectedNextValue;
        private long nextValue;
        private int segmentSize;

        private RecordingMapper(Long currentNextValue) {
            this.currentNextValue = currentNextValue;
        }

        @Override
        public int lockExisting(String segmentKey) {
            insertCalls++;
            return 1;
        }

        @Override
        public IdSegmentState selectForUpdate(String segmentKey) {
            IdSegmentState state = new IdSegmentState();
            state.setSegmentKey(segmentKey);
            state.setNextValue(currentNextValue);
            state.setSegmentSize(segmentSize);
            return state;
        }

        @Override
        public int advance(
                String segmentKey, long expectedNextValue, long nextValue, int segmentSize) {
            advanceCalls++;
            this.expectedNextValue = expectedNextValue;
            this.nextValue = nextValue;
            this.segmentSize = segmentSize;
            return 1;
        }
    }
}
