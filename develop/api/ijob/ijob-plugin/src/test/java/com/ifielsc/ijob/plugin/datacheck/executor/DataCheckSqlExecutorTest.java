package com.ifielsc.ijob.plugin.datacheck.executor;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.ifielsc.ijob.core.execute.ExecutionHandle;
import com.ifielsc.ijob.core.execute.task.TaskContext;
import com.ifielsc.ijob.plugin.datacheck.config.CheckType;
import com.ifielsc.ijob.plugin.datacheck.config.DataCheckItemConfig;
import com.ifielsc.ijob.plugin.support.RecordingInstanceLogs;

import org.junit.jupiter.api.Test;

class DataCheckSqlExecutorTest {
    @Test
    void fourTypesShouldUseDefinedJdbcOperation() {
        RecordingJdbc jdbc = new RecordingJdbc();
        DataCheckSqlExecutor executor =
                new DataCheckSqlExecutor(
                        new NormalQueryExecutor(jdbc),
                        new CountQueryExecutor(jdbc),
                        new ExistsQueryExecutor(jdbc),
                        new EmptyQueryExecutor(jdbc));
        TaskContext context =
                new TaskContext(
                        1L, new ExecutionHandle(1L), null, new RecordingInstanceLogs(), null);

        assertEquals(
                "scalar",
                executor.execute(12L, item(CheckType.NORMAL, "select value"), 9, context));
        assertEquals(
                false, executor.execute(12L, item(CheckType.EMPTY, "select rows"), 9, context));
        assertEquals(4L, executor.execute(12L, item(CheckType.COUNT, "select rows;"), 9, context));
        assertEquals(
                true, executor.execute(12L, item(CheckType.EXISTS, "select rows"), 9, context));

        assertEquals(12L, jdbc.resourceId);
        assertEquals(9, jdbc.timeout);
        assertEquals("select count(*) from (\n" + "select rows\n) ifj_count_source", jdbc.countSql);
    }

    @Test
    void countWrapperShouldNotBeSwallowedByTrailingLineComment() {
        RecordingJdbc jdbc = new RecordingJdbc();
        CountQueryExecutor executor = new CountQueryExecutor(jdbc);

        executor.execute(
                12L,
                item(CheckType.COUNT, "select rows -- source query"),
                9,
                new TaskContext(
                        1L, new ExecutionHandle(1L), null, new RecordingInstanceLogs(), null));

        assertEquals(
                "select count(*) from (\n" + "select rows -- source query\n" + ") ifj_count_source",
                jdbc.countSql);
    }

    private DataCheckItemConfig item(CheckType type, String sql) {
        DataCheckItemConfig item = new DataCheckItemConfig();
        item.setType(type);
        item.setSql(sql);
        return item;
    }

    private static final class RecordingJdbc extends JdbcExecutor {
        private Long resourceId;
        private int timeout;
        private String countSql;

        private RecordingJdbc() {
            super(null);
        }

        @Override
        public Object queryForObject(Long id, String sql, int seconds, TaskContext context) {
            record(id, seconds);
            return "scalar";
        }

        @Override
        public long queryCount(Long id, String sql, int seconds, TaskContext context) {
            record(id, seconds);
            countSql = sql;
            return 4L;
        }

        @Override
        public boolean exists(Long id, String sql, int seconds, TaskContext context) {
            record(id, seconds);
            return true;
        }

        private void record(Long id, int seconds) {
            resourceId = id;
            timeout = seconds;
        }
    }
}
