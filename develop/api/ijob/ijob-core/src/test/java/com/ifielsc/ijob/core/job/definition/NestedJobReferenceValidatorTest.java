// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.core.job.definition;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.ifielsc.ijob.common.exception.BusinessException;
import com.ifielsc.ijob.common.response.ResultCode;
import com.ifielsc.ijob.contract.enums.TargetType;
import com.ifielsc.ijob.contract.job.JobNodeRequest;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

class NestedJobReferenceValidatorTest {
    private final NestedJobReferenceValidator validator = new NestedJobReferenceValidator();

    @Test
    void shouldAcceptMaximumDepthForCreate() {
        Map<Long, Set<Long>> snapshot = chainSnapshot(7);

        assertDoesNotThrow(() -> validator.validate(null, List.of(jobNode(1L)), snapshot));
    }

    @Test
    void shouldRejectDepthBeyondLimit() {
        Map<Long, Set<Long>> snapshot = chainSnapshot(8);

        assertValidationError(
                null, List.of(jobNode(1L)), snapshot, "Nested job depth must not exceed 8");
    }

    @Test
    void shouldRejectDirectSelfReferenceForUpdate() {
        assertValidationError(
                10L,
                List.of(jobNode(10L)),
                Map.of(10L, Set.of()),
                "A job must not reference itself");
    }

    @Test
    void shouldRejectIndirectCycleBackToUpdatedJob() {
        Map<Long, Set<Long>> snapshot =
                Map.of(
                        1L, Set.of(2L),
                        2L, Set.of(10L),
                        10L, Set.of());

        assertValidationError(
                10L,
                List.of(jobNode(1L)),
                snapshot,
                "Nested job references must not contain cycles");
    }

    @Test
    void shouldRejectCycleInsideReferencedSnapshot() {
        Map<Long, Set<Long>> snapshot =
                Map.of(
                        1L, Set.of(2L),
                        2L, Set.of(1L));

        assertValidationError(
                null,
                List.of(jobNode(1L)),
                snapshot,
                "Nested job references must not contain cycles");
    }

    @Test
    void shouldRejectIncompleteReachableSnapshot() {
        assertValidationError(
                null,
                List.of(jobNode(1L)),
                Map.of(),
                "Nested job reference snapshot is incomplete");

        assertValidationError(
                null,
                List.of(jobNode(1L)),
                Map.of(1L, Set.of(2L)),
                "Nested job reference snapshot is incomplete");
    }

    @Test
    void shouldIgnoreTaskNodesAndDeduplicateJobReferences() {
        List<JobNodeRequest> nodes = List.of(taskNode(99L), jobNode(1L), jobNode(1L));

        assertDoesNotThrow(
                () -> validator.validate(99L, nodes, Map.of(1L, Set.of(), 99L, Set.of())));
    }

    @Test
    void shouldRejectInvalidRootOrProposedReference() {
        assertValidationError(0L, List.of(), Map.of(), "Current job ID must be greater than zero");
        assertValidationError(
                null,
                List.of(jobNode(null)),
                Map.of(),
                "Job reference ID must be greater than zero");
    }

    @Test
    void shouldRejectInvalidSnapshotStructure() {
        assertValidationError(null, List.of(), null, "Nested job reference snapshot is required");

        Map<Long, Set<Long>> nullChildren = new HashMap<>();
        nullChildren.put(1L, null);
        assertValidationError(
                null,
                List.of(),
                nullChildren,
                "Nested job reference snapshots must not contain null reference sets");

        Set<Long> invalidChildren = new HashSet<>();
        invalidChildren.add(null);
        assertValidationError(
                null,
                List.of(),
                Map.of(1L, invalidChildren),
                "Job reference ID must be greater than zero");
    }

    @Test
    void shouldRejectMissingOrNullProposedNodes() {
        assertValidationError(null, null, Map.of(), "Job node definitions is required");

        List<JobNodeRequest> nodes = new ArrayList<>();
        nodes.add(null);
        assertValidationError(null, nodes, Map.of(), "Job node is required");
    }

    private Map<Long, Set<Long>> chainSnapshot(int jobCount) {
        Map<Long, Set<Long>> snapshot = new HashMap<>();
        for (long jobId = 1; jobId <= jobCount; jobId++) {
            snapshot.put(jobId, jobId == jobCount ? Set.of() : Set.of(jobId + 1));
        }
        return snapshot;
    }

    private JobNodeRequest jobNode(Long refId) {
        JobNodeRequest node = new JobNodeRequest();
        node.setNodeType(TargetType.JOB);
        node.setRefId(refId);
        return node;
    }

    private JobNodeRequest taskNode(Long refId) {
        JobNodeRequest node = new JobNodeRequest();
        node.setNodeType(TargetType.TASK);
        node.setRefId(refId);
        return node;
    }

    private void assertValidationError(
            Long currentJobId,
            List<JobNodeRequest> proposedNodes,
            Map<Long, Set<Long>> referenceSnapshot,
            String expectedMessage) {
        BusinessException exception =
                assertThrows(
                        BusinessException.class,
                        () -> validator.validate(currentJobId, proposedNodes, referenceSnapshot));
        assertEquals(ResultCode.PARAM_ERROR, exception.getCode());
        assertEquals(expectedMessage, exception.getMessage());
    }
}
