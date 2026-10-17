package com.ifelio.ijob.core.execute;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.ifelio.ijob.common.config.AppProperties;
import com.ifelio.ijob.common.exception.BusinessException;
import com.ifelio.ijob.common.response.ResultCode;
import com.ifelio.ijob.contract.enums.RuntimeCommandStatus;
import com.ifelio.ijob.contract.enums.RuntimeCommandType;
import com.ifelio.ijob.contract.runtime.RuntimeCommandDTO;
import com.ifelio.ijob.infra.coordination.work.ExecutionFenceCoordinator;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

class RuntimeCommandServiceTest {
    @Test
    void localModeShouldExecuteConcurrentDuplicateRequestOnlyOnce() throws Exception {
        RecordingRuntime runtime = new RecordingRuntime();
        RuntimeCommandService service = service(runtime);
        List<Callable<RuntimeCommandDTO>> requests = new ArrayList<>();
        for (int index = 0; index < 16; index++) {
            requests.add(
                    () -> service.submit(9L, RuntimeCommandType.STOP, "request-1", "operator"));
        }

        try (var executor = Executors.newFixedThreadPool(8)) {
            for (var future : executor.invokeAll(requests)) {
                RuntimeCommandDTO result = future.get();
                assertEquals("request-1", result.getRequestId());
                assertEquals(RuntimeCommandStatus.SUCCEEDED, result.getStatus());
            }
        }

        assertEquals(1, runtime.stopCount.get());
    }

    @Test
    void localModeShouldRejectIdempotencyKeyReuseForDifferentCommand() {
        RuntimeCommandService service = service(new RecordingRuntime());
        service.submit(9L, RuntimeCommandType.STOP, "request-1", "operator");

        BusinessException exception =
                assertThrows(
                        BusinessException.class,
                        () ->
                                service.submit(
                                        9L, RuntimeCommandType.PAUSE, "request-1", "operator"));

        assertEquals(ResultCode.CONFLICT, exception.getCode());
    }

    private RuntimeCommandService service(RecordingRuntime runtime) {
        return new RuntimeCommandService(
                null, new ExecutionFenceCoordinator(new AppProperties()), runtime);
    }

    private static final class RecordingRuntime extends RuntimeService {
        private final AtomicInteger stopCount = new AtomicInteger();

        private RecordingRuntime() {
            super(null, null, null, null, null);
        }

        @Override
        public void stop(Long instanceId) {
            stopCount.incrementAndGet();
        }
    }
}
