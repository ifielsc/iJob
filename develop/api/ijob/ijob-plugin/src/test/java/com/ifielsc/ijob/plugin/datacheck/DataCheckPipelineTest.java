package com.ifielsc.ijob.plugin.datacheck;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ifielsc.ijob.common.exception.StopException;
import com.ifielsc.ijob.core.execute.ExecutionHandle;
import com.ifielsc.ijob.core.execute.task.TaskContext;
import com.ifielsc.ijob.core.instance.InstanceService;
import com.ifielsc.ijob.plugin.datacheck.analyzer.CompositeDataCheckAnalyzer;
import com.ifielsc.ijob.plugin.datacheck.analyzer.CountCheckAnalyzer;
import com.ifielsc.ijob.plugin.datacheck.analyzer.EmptyCheckAnalyzer;
import com.ifielsc.ijob.plugin.datacheck.analyzer.ExistsCheckAnalyzer;
import com.ifielsc.ijob.plugin.datacheck.analyzer.NormalCheckAnalyzer;
import com.ifielsc.ijob.plugin.datacheck.config.CheckLevel;
import com.ifielsc.ijob.plugin.datacheck.config.CheckType;
import com.ifielsc.ijob.plugin.datacheck.config.CompareType;
import com.ifielsc.ijob.plugin.datacheck.config.DataCheckConfig;
import com.ifielsc.ijob.plugin.datacheck.config.DataCheckItemConfig;
import com.ifielsc.ijob.plugin.datacheck.config.HandlerConfig;
import com.ifielsc.ijob.plugin.datacheck.executor.DataCheckSqlExecutor;
import com.ifielsc.ijob.plugin.datacheck.handler.CompositeDataCheckHandler;
import com.ifielsc.ijob.plugin.datacheck.handler.VariableDataCheckHandler;
import com.ifielsc.ijob.plugin.datacheck.model.DataCheckContext;
import com.ifielsc.ijob.plugin.datacheck.model.DataCheckResult;
import com.ifielsc.ijob.plugin.support.RecordingInstanceLogs;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

class DataCheckPipelineTest {
    @Test
    void contextVariableReadsShouldDelegateWithoutAStaleSnapshot() {
        TaskContext taskContext = taskContext(new ExecutionHandle(30L));
        taskContext.putVariable("state", "before");
        DataCheckContext context = checkContext(taskContext);

        taskContext.putVariable("state", "after");

        assertEquals("after", context.getVariable("state"));
    }

    @Test
    void warnFailureShouldBeReportedWithoutFailingTask() {
        RecordingSqlExecutor sql = new RecordingSqlExecutor();
        sql.values.put("warning", 3);
        TaskContext taskContext = taskContext(new ExecutionHandle(31L));
        DataCheckContext context = checkContext(taskContext);
        DataCheckConfig config = config(item("warning", CheckType.COUNT, CheckLevel.WARN, 5));
        config.setHandlers(List.of(handler("VARIABLE")));

        DataCheckResult result = pipeline(sql).execute(config, context);

        assertTrue(result.isSuccess());
        assertEquals(1, result.getFailedCount());
        assertEquals(Boolean.TRUE, taskContext.getVariable("data_check_success"));
        assertEquals(1, taskContext.getVariable("data_check_failed"));
    }

    @Test
    void errorFailureShouldFailTaskWhileAllChecksStillRun() {
        RecordingSqlExecutor sql = new RecordingSqlExecutor();
        sql.values.put("error", 0);
        sql.values.put("healthy", true);
        DataCheckConfig config =
                config(
                        item("error", CheckType.COUNT, CheckLevel.ERROR, 1),
                        item("healthy", CheckType.EXISTS, CheckLevel.ERROR, true));

        DataCheckResult result =
                pipeline(sql).execute(config, checkContext(taskContext(new ExecutionHandle(32L))));

        assertFalse(result.isSuccess());
        assertEquals(2, result.getTotal());
        assertEquals(2, sql.calls.get());
    }

    @Test
    void itemVariableShouldPublishActualValueAndRemoveNull() {
        RecordingSqlExecutor sql = new RecordingSqlExecutor();
        sql.values.put("present", "ready");
        sql.values.put("missing", null);
        TaskContext taskContext = taskContext(new ExecutionHandle(33L));
        taskContext.putVariable("missingValue", "stale");
        DataCheckItemConfig present = item("present", CheckType.NORMAL, CheckLevel.ERROR, "ready");
        present.setVariable("presentValue");
        DataCheckItemConfig missing = item("missing", CheckType.NORMAL, CheckLevel.ERROR, "ready");
        missing.setVariable("missingValue");

        pipeline(sql).execute(config(present, missing), checkContext(taskContext));

        assertEquals("ready", taskContext.getVariable("presentValue"));
        assertFalse(taskContext.containsVariable("missingValue"));
    }

    @Test
    void stopRequestedAfterFirstQueryShouldPreventFurtherChecks() {
        ExecutionHandle handle = new ExecutionHandle(34L);
        RecordingSqlExecutor sql = new RecordingSqlExecutor();
        sql.values.put("first", 1);
        sql.stopAfterFirst = handle;
        DataCheckConfig config =
                config(
                        item("first", CheckType.COUNT, CheckLevel.ERROR, 1),
                        item("second", CheckType.COUNT, CheckLevel.ERROR, 1));

        assertThrows(
                StopException.class,
                () -> pipeline(sql).execute(config, checkContext(taskContext(handle))));
        assertEquals(1, sql.calls.get());
    }

    @Test
    void completedChecksAndHandlersShouldReportMeasuredProgress() {
        RecordingSqlExecutor sql = new RecordingSqlExecutor();
        sql.values.put("first", 1);
        sql.values.put("second", 1);
        RecordingInstanceService instanceService = new RecordingInstanceService();
        ExecutionHandle handle = new ExecutionHandle(35L);
        TaskContext taskContext =
                new TaskContext(35L, handle, instanceService, new RecordingInstanceLogs(), null);
        DataCheckConfig config =
                config(
                        item("first", CheckType.COUNT, CheckLevel.ERROR, 1),
                        item("second", CheckType.COUNT, CheckLevel.ERROR, 1));
        config.setHandlers(List.of(handler("VARIABLE")));

        pipeline(sql).execute(config, checkContext(taskContext));

        assertEquals(List.of(45, 90, 95), instanceService.progressValues);
    }

    private DataCheckPipeline pipeline(RecordingSqlExecutor sql) {
        return new DataCheckPipeline(
                sql,
                new CompositeDataCheckAnalyzer(
                        List.of(
                                new NormalCheckAnalyzer(),
                                new CountCheckAnalyzer(),
                                new ExistsCheckAnalyzer(),
                                new EmptyCheckAnalyzer())),
                new CompositeDataCheckHandler(List.of(new VariableDataCheckHandler())));
    }

    private DataCheckConfig config(DataCheckItemConfig... items) {
        DataCheckConfig config = new DataCheckConfig();
        config.setResourceId(8L);
        config.setQueryTimeoutSeconds(17);
        config.setChecks(List.of(items));
        return config;
    }

    private DataCheckItemConfig item(
            String name, CheckType type, CheckLevel level, Object expected) {
        DataCheckItemConfig item = new DataCheckItemConfig();
        item.setName(name);
        item.setSql("select 1");
        item.setType(type);
        item.setLevel(level);
        item.setCompareType(CompareType.EQ);
        item.setExpected(expected);
        return item;
    }

    private HandlerConfig handler(String type) {
        HandlerConfig handler = new HandlerConfig();
        handler.setType(type);
        handler.setParams(Map.of());
        return handler;
    }

    private TaskContext taskContext(ExecutionHandle handle) {
        return new TaskContext(
                handle.getInstanceId(),
                handle,
                new RecordingInstanceService(),
                new RecordingInstanceLogs(),
                null);
    }

    private DataCheckContext checkContext(TaskContext taskContext) {
        return DataCheckContext.builder()
                .instanceId(taskContext.getInstanceId())
                .taskContext(taskContext)
                .build();
    }

    private static final class RecordingSqlExecutor extends DataCheckSqlExecutor {
        private final Map<String, Object> values = new HashMap<>();
        private final AtomicInteger calls = new AtomicInteger();
        private ExecutionHandle stopAfterFirst;

        private RecordingSqlExecutor() {
            super(null, null, null, null);
        }

        @Override
        public Object execute(
                Long resourceId,
                DataCheckItemConfig config,
                int timeoutSeconds,
                TaskContext context) {
            assertEquals(8L, resourceId);
            assertEquals(17, timeoutSeconds);
            int current = calls.incrementAndGet();
            if (current == 1 && stopAfterFirst != null) {
                stopAfterFirst.requestStop();
            }
            return values.get(config.getName());
        }
    }

    private static final class RecordingInstanceService extends InstanceService {
        private final List<Integer> progressValues = new java.util.ArrayList<>();

        private RecordingInstanceService() {
            super(null, null, null, null);
        }

        @Override
        public void updateProgress(Long instanceId, Integer progress) {
            progressValues.add(progress);
        }
    }
}
