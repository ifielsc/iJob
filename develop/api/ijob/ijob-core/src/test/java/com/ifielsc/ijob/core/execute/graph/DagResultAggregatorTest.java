package com.ifielsc.ijob.core.execute.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.ifielsc.ijob.common.exception.BusinessException;
import com.ifielsc.ijob.common.response.ResultCode;
import com.ifielsc.ijob.contract.enums.InstanceStatus;

import org.junit.jupiter.api.Test;

import java.util.Map;

class DagResultAggregatorTest {
    private final DagResultAggregator aggregator = new DagResultAggregator();

    @Test
    void successAndLegalSkipShouldAggregateSuccess() {
        DagExecutionResult result =
                aggregator.aggregate(
                        Map.of(
                                1L, InstanceStatus.SUCCESS,
                                2L, InstanceStatus.SKIPPED),
                        2);

        assertEquals(InstanceStatus.SUCCESS, result.status());
        assertEquals(1, result.successCount());
        assertEquals(1, result.skippedCount());
    }

    @Test
    void skippedFailureStrategyMustNotHideNodeFailure() {
        DagExecutionResult failed =
                aggregator.aggregate(
                        Map.of(
                                1L, InstanceStatus.FAILED,
                                2L, InstanceStatus.SUCCESS),
                        2);
        DagExecutionResult timedOut = aggregator.aggregate(Map.of(1L, InstanceStatus.TIMED_OUT), 1);

        assertEquals(InstanceStatus.FAILED, failed.status());
        assertEquals(InstanceStatus.FAILED, timedOut.status());
        assertEquals(1, timedOut.timedOutCount());
    }

    @Test
    void stoppedNodeShouldAggregateStoppedWhenThereIsNoFailure() {
        DagExecutionResult result =
                aggregator.aggregate(
                        Map.of(
                                1L, InstanceStatus.SUCCESS,
                                2L, InstanceStatus.STOPPED),
                        2);

        assertEquals(InstanceStatus.STOPPED, result.status());
    }

    @Test
    void incompleteOrNonTerminalResultsShouldFailClosed() {
        BusinessException missing =
                assertThrows(
                        BusinessException.class,
                        () -> aggregator.aggregate(Map.of(1L, InstanceStatus.SUCCESS), 2));
        BusinessException running =
                assertThrows(
                        BusinessException.class,
                        () -> aggregator.aggregate(Map.of(1L, InstanceStatus.RUNNING), 1));
        BusinessException missingMap =
                assertThrows(
                        BusinessException.class,
                        () -> aggregator.aggregate((Map<Long, InstanceStatus>) null, 1));

        assertEquals(ResultCode.SYSTEM_ERROR, missing.getCode());
        assertEquals(ResultCode.SYSTEM_ERROR, running.getCode());
        assertEquals(ResultCode.SYSTEM_ERROR, missingMap.getCode());
    }

    @Test
    void stopFailureAbortShouldAllowPartialFailedAggregation() {
        DagExecutionResult result =
                aggregator.aggregate(
                        new DagCoordinationResult(Map.of(1L, InstanceStatus.FAILED), true), 3);

        assertEquals(InstanceStatus.FAILED, result.status());
        assertEquals(1, result.failedCount());
    }

    @Test
    void partialAbortWithoutFailureShouldFailClosed() {
        BusinessException exception =
                assertThrows(
                        BusinessException.class,
                        () ->
                                aggregator.aggregate(
                                        new DagCoordinationResult(
                                                Map.of(1L, InstanceStatus.STOPPED), true),
                                        2));

        assertEquals(ResultCode.SYSTEM_ERROR, exception.getCode());
    }
}
