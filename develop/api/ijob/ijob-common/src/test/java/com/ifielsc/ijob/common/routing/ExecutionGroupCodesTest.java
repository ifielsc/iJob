package com.ifielsc.ijob.common.routing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

class ExecutionGroupCodesTest {
    @Test
    void shouldCanonicalizeDeduplicateAndSortGroups() {
        assertEquals(
                List.of("cpu", "gpu.prod"),
                ExecutionGroupCodes.normalize(List.of(" GPU.Prod ", "cpu", "gpu.prod")));
    }

    @Test
    void shouldTreatMissingGroupsAsUniversal() {
        assertEquals(List.of(), ExecutionGroupCodes.normalize(null));
        assertEquals(List.of(), ExecutionGroupCodes.normalize(List.of()));
    }

    @Test
    void shouldRejectBlankInvalidAndExcessiveGroups() {
        assertThrows(
                IllegalArgumentException.class, () -> ExecutionGroupCodes.normalize(List.of(" ")));
        assertThrows(
                IllegalArgumentException.class,
                () -> ExecutionGroupCodes.normalize(List.of("gpu/prod")));

        List<String> tooMany = new ArrayList<>();
        for (int index = 0; index <= ExecutionGroupCodes.MAX_GROUPS; index++) {
            tooMany.add("group-" + index);
        }
        assertThrows(IllegalArgumentException.class, () -> ExecutionGroupCodes.normalize(tooMany));
    }
}
