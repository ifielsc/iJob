package com.ifelio.ijob.infra.coordination.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ifelio.ijob.common.config.AppProperties;
import com.ifelio.ijob.common.exception.BusinessException;
import com.ifelio.ijob.contract.enums.RuntimeCommandStatus;
import com.ifelio.ijob.contract.enums.RuntimeCommandType;
import com.ifelio.ijob.contract.runtime.RuntimeCommandDTO;
import com.ifelio.ijob.infra.coordination.work.TriggerWorkClaim;
import com.ifelio.ijob.infra.mapper.coordination.RuntimeCommandMapper;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

class DatabaseRuntimeCommandQueueTest {
    @Test
    void shouldCreateIdempotentCommandAndRejectKeyReuse() {
        FakeMapper mapper = new FakeMapper();
        DatabaseRuntimeCommandQueue queue =
                new DatabaseRuntimeCommandQueue(mapper, new AppProperties());

        RuntimeCommandDTO first =
                queue.submit("request-1", 9L, RuntimeCommandType.STOP, "operator");
        RuntimeCommandDTO duplicate =
                queue.submit("request-1", 9L, RuntimeCommandType.STOP, "operator");

        assertEquals("request-1", first.getRequestId());
        assertEquals(first.getRequestId(), duplicate.getRequestId());
        assertEquals(1, mapper.insertCount);
        assertThrows(
                BusinessException.class,
                () -> queue.submit("request-1", 10L, RuntimeCommandType.STOP, "operator"));
    }

    @Test
    void retryShouldReactivateTriggerAndWorkBeforeReturning() {
        FakeMapper mapper = new FakeMapper();
        DatabaseRuntimeCommandQueue queue =
                new DatabaseRuntimeCommandQueue(mapper, new AppProperties());

        RuntimeCommandDTO result =
                queue.submit("retry-1", 9L, RuntimeCommandType.RETRY, "operator");

        assertEquals(RuntimeCommandStatus.PENDING, result.getStatus());
        assertEquals(1, mapper.activateCount);
        assertEquals(1, mapper.requeueCount);
    }

    @Test
    void claimedCommandShouldFinishOnlyWithItsProcessingFence() {
        FakeMapper mapper = new FakeMapper();
        DatabaseRuntimeCommandQueue queue =
                new DatabaseRuntimeCommandQueue(mapper, new AppProperties());
        queue.submit("pause-1", 9L, RuntimeCommandType.PAUSE, "operator");
        TriggerWorkClaim work =
                new TriggerWorkClaim(
                        7L, "node-a", "work-token", 3L, 1, 5, LocalDateTime.now().plusSeconds(30));

        RuntimeCommandClaim claim = queue.claim("pause-1", work);

        assertNotNull(claim);
        assertEquals(3L, claim.processingFence());
        assertEquals(RuntimeCommandStatus.PROCESSING, mapper.command.getStatus());
        assertTrue(queue.succeed(claim, "done"));
        assertEquals(RuntimeCommandStatus.SUCCEEDED, mapper.command.getStatus());
    }

    @Test
    void queryShouldSettleExpiredCommandsBeforeReturning() {
        FakeMapper mapper = new FakeMapper();
        DatabaseRuntimeCommandQueue queue =
                new DatabaseRuntimeCommandQueue(mapper, new AppProperties());
        queue.submit("request-1", 9L, RuntimeCommandType.STOP, "operator");

        queue.get("request-1");

        assertEquals(1, mapper.expireCount);
    }

    private static final class FakeMapper implements RuntimeCommandMapper {
        private RuntimeCommand command;
        private int insertCount;
        private int activateCount;
        private int requeueCount;
        private int expireCount;

        @Override
        public int insertFromInstance(
                String requestId,
                Long instanceId,
                RuntimeCommandType commandType,
                String requestedBy,
                long timeoutMicros) {
            if (command != null) {
                return 0;
            }
            command = new RuntimeCommand();
            command.setRequestId(requestId);
            command.setInstanceId(instanceId);
            command.setRootInstanceId(instanceId);
            command.setTriggerId(7L);
            command.setCommandType(commandType);
            command.setStatus(RuntimeCommandStatus.PENDING);
            command.setRequestedBy(requestedBy);
            insertCount++;
            return 1;
        }

        @Override
        public RuntimeCommand selectByRequestId(String requestId) {
            return command != null && command.getRequestId().equals(requestId) ? command : null;
        }

        @Override
        public RuntimeCommandDTO selectDtoByRequestId(String requestId) {
            RuntimeCommand selected = selectByRequestId(requestId);
            if (selected == null) {
                return null;
            }
            RuntimeCommandDTO dto = new RuntimeCommandDTO();
            dto.setRequestId(selected.getRequestId());
            dto.setInstanceId(selected.getInstanceId());
            dto.setRootInstanceId(selected.getRootInstanceId());
            dto.setTriggerId(selected.getTriggerId());
            dto.setCommandType(selected.getCommandType());
            dto.setStatus(selected.getStatus());
            return dto;
        }

        @Override
        public int rejectInitiallyInvalid(String requestId) {
            return 0;
        }

        @Override
        public int succeedInitiallySatisfied(String requestId) {
            return 0;
        }

        @Override
        public int requeueWorkForCommand(String requestId) {
            requeueCount++;
            return 1;
        }

        @Override
        public int activateTriggerForRetry(String requestId) {
            activateCount++;
            return 1;
        }

        @Override
        public List<String> selectDispatchableRequestIds(
                Long triggerId, long fenceToken, int limit) {
            return command == null ? List.of() : List.of(command.getRequestId());
        }

        @Override
        public List<RuntimeCommandCandidate> selectDispatchableOwned(String leaseOwner, int limit) {
            return List.of();
        }

        @Override
        public int claim(
                String requestId,
                Long triggerId,
                String leaseOwner,
                String processingToken,
                long fenceToken) {
            if (command == null || command.getStatus() != RuntimeCommandStatus.PENDING) {
                return 0;
            }
            command.setStatus(RuntimeCommandStatus.PROCESSING);
            command.setProcessingOwner(leaseOwner);
            command.setProcessingToken(processingToken);
            command.setProcessingFence(fenceToken);
            return 1;
        }

        @Override
        public int finish(
                String requestId,
                String processingToken,
                long fenceToken,
                RuntimeCommandStatus status,
                String resultMessage) {
            if (command == null
                    || !processingToken.equals(command.getProcessingToken())
                    || fenceToken != command.getProcessingFence()) {
                return 0;
            }
            command.setStatus(status);
            command.setResultMessage(resultMessage);
            command.setProcessingOwner(null);
            command.setProcessingToken(null);
            command.setProcessingFence(null);
            return 1;
        }

        @Override
        public int defer(
                String requestId,
                String processingToken,
                long fenceToken,
                long delayMicros,
                String resultMessage) {
            return 0;
        }

        @Override
        public int succeedCompletedStops(int limit) {
            return 0;
        }

        @Override
        public int expirePending(int limit) {
            expireCount++;
            return 0;
        }

        @Override
        public int expireByRequestId(String requestId) {
            expireCount++;
            return 0;
        }
    }
}
