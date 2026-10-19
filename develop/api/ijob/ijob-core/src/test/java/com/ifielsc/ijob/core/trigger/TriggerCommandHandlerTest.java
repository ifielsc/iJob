package com.ifielsc.ijob.core.trigger;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

class TriggerCommandHandlerTest {
    @Test
    void shouldDispatchTheSameWaitingTriggerOnlyOnceUntilExecutionFinishes() {
        RecordingExecutor executor = new RecordingExecutor();
        RecordingTriggerExecuteService service = new RecordingTriggerExecuteService();
        TriggerCommandHandler handler = new TriggerCommandHandler(executor, service, null);

        handler.handle(new TriggerCommand(42L));
        handler.handle(new TriggerCommand(42L));

        assertEquals(1, executor.tasks.size());
        executor.tasks.getFirst().run();
        assertEquals(1, service.executions);

        handler.handle(new TriggerCommand(42L));
        assertEquals(2, executor.tasks.size());
    }

    private static final class RecordingExecutor implements Executor {
        private final List<Runnable> tasks = new ArrayList<>();

        @Override
        public void execute(Runnable command) {
            tasks.add(command);
        }
    }

    private static final class RecordingTriggerExecuteService extends TriggerExecuteService {
        private int executions;

        private RecordingTriggerExecuteService() {
            super(null, null, null);
        }

        @Override
        public TriggerExecutionDisposition execute(Long triggerId) {
            executions++;
            return TriggerExecutionDisposition.COMPLETED;
        }
    }
}
