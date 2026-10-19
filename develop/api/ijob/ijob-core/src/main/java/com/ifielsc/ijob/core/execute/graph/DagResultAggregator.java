package com.ifielsc.ijob.core.execute.graph;

import com.ifielsc.ijob.common.exception.BusinessException;
import com.ifielsc.ijob.common.response.ResultCode;
import com.ifielsc.ijob.contract.enums.InstanceStatus;

import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class DagResultAggregator {
    public DagExecutionResult aggregate(Map<Long, InstanceStatus> nodeResults, int totalNodeCount) {
        if (nodeResults == null) {
            throw incompleteResult();
        }
        return aggregate(new DagCoordinationResult(nodeResults, false), totalNodeCount);
    }

    public DagExecutionResult aggregate(
            DagCoordinationResult coordinationResult, int totalNodeCount) {
        if (coordinationResult == null) {
            throw incompleteResult();
        }
        Map<Long, InstanceStatus> nodeResults = coordinationResult.nodeResults();
        boolean hasFailure =
                nodeResults != null
                        && nodeResults.values().stream()
                                .anyMatch(
                                        status ->
                                                status == InstanceStatus.FAILED
                                                        || status == InstanceStatus.TIMED_OUT);
        if (totalNodeCount <= 0
                || nodeResults.size() > totalNodeCount
                || (nodeResults.size() < totalNodeCount
                        && !(coordinationResult.abortedByFailure() && hasFailure))
                || nodeResults.values().stream()
                        .anyMatch(status -> status == null || !status.isFinished())) {
            throw incompleteResult();
        }

        int successCount = count(nodeResults, InstanceStatus.SUCCESS);
        int failedCount = count(nodeResults, InstanceStatus.FAILED);
        int timedOutCount = count(nodeResults, InstanceStatus.TIMED_OUT);
        int stoppedCount = count(nodeResults, InstanceStatus.STOPPED);
        int skippedCount = count(nodeResults, InstanceStatus.SKIPPED);
        InstanceStatus status;
        if (failedCount > 0 || timedOutCount > 0) {
            status = InstanceStatus.FAILED;
        } else if (stoppedCount > 0) {
            status = InstanceStatus.STOPPED;
        } else {
            status = InstanceStatus.SUCCESS;
        }
        return new DagExecutionResult(
                status, successCount, failedCount, timedOutCount, stoppedCount, skippedCount);
    }

    private int count(Map<Long, InstanceStatus> nodeResults, InstanceStatus expectedStatus) {
        return (int) nodeResults.values().stream().filter(expectedStatus::equals).count();
    }

    private BusinessException incompleteResult() {
        return BusinessException.localized(ResultCode.SYSTEM_ERROR, "Job DAG result is incomplete");
    }
}
