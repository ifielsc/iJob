package com.ifelio.ijob.common.routing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

class ExecutionTargetIdsTest {
    @Test
    void shouldDeduplicateAndSortIds() {
        assertEquals(List.of(3L, 9L), ExecutionTargetIds.normalize(List.of(9L, 3L, 9L), "Task"));
    }

    @Test
    void shouldTreatMissingIdsAsUnrestricted() {
        assertEquals(List.of(), ExecutionTargetIds.normalize(null, "Task"));
        assertEquals(List.of(), ExecutionTargetIds.normalize(List.of(), "Job"));
    }

    @Test
    void shouldRejectInvalidAndExcessiveIds() {
        assertThrows(
                IllegalArgumentException.class,
                () -> ExecutionTargetIds.normalize(List.of(0L), "Task"));
        assertThrows(
                IllegalArgumentException.class,
                () -> ExecutionTargetIds.normalize(List.of(-1L), "Job"));
        assertThrows(
                IllegalArgumentException.class,
                () -> ExecutionTargetIds.normalize(java.util.Arrays.asList(1L, null), "Task"));

        List<Long> tooMany = new ArrayList<>();
        for (long id = 1; id <= ExecutionTargetIds.MAX_IDS_PER_TYPE + 1L; id++) {
            tooMany.add(id);
        }
        assertThrows(
                IllegalArgumentException.class, () -> ExecutionTargetIds.normalize(tooMany, "Job"));
    }
}
