package com.ifelio.ijob.plugin.script;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ifelio.ijob.core.execute.ExecutionHandle;
import com.ifelio.ijob.core.execute.task.TaskContext;
import com.ifelio.ijob.core.execute.variable.VariableResolver;
import com.ifelio.ijob.core.instance.InstanceLogService;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

class ProcessStreamCollectorTest {
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @AfterEach
    void shutdownExecutor() {
        executor.shutdownNow();
    }

    @Test
    void shouldKeepLastThousandLinesAndPublishVariables() throws Exception {
        StringBuilder content = new StringBuilder();
        for (int index = 0; index < 1002; index++) {
            content.append("line-").append(index).append('\n');
        }
        content.append("__VAR__:answer=42\n");
        RecordingLogService logs = new RecordingLogService();
        TaskContext context = context(logs);

        String output =
                new ProcessStreamCollector()
                        .collect(
                                context,
                                new ByteArrayInputStream(
                                        content.toString().getBytes(StandardCharsets.UTF_8)),
                                "STDOUT",
                                StandardCharsets.UTF_8,
                                executor)
                        .get(2, TimeUnit.SECONDS);

        String[] lines = output.split(System.lineSeparator());
        assertEquals(1000, lines.length);
        assertEquals("line-3", lines[0]);
        assertEquals("__VAR__:answer=[redacted]", lines[999]);
        assertEquals("42", context.getVariable("answer"));
        assertEquals(1003, logs.messages.size());
        assertFalse(logs.messages.stream().anyMatch(message -> message.contains("answer=42")));
        assertFalse(output.contains("line-2" + System.lineSeparator()));
    }

    @Test
    void shouldDecodeOutputWithConfiguredCharset() throws Exception {
        RecordingLogService logs = new RecordingLogService();

        String output =
                new ProcessStreamCollector()
                        .collect(
                                context(logs),
                                new ByteArrayInputStream(
                                        "验收通过\n".getBytes(StandardCharsets.UTF_16LE)),
                                "STDOUT",
                                StandardCharsets.UTF_16LE,
                                executor)
                        .get(2, TimeUnit.SECONDS);

        assertEquals("验收通过", output);
        assertTrue(logs.messages.getFirst().contains("验收通过"));
    }

    @Test
    void shouldBoundSingleLinesCapturedOutputAndLogs() throws Exception {
        String line = "x".repeat(20_000);
        String content = (line + "\n").repeat(30);
        RecordingLogService logs = new RecordingLogService();

        String output = collect(content, context(logs));

        assertTrue(ScriptOutputLimits.utf8Length(output) <= ScriptOutputLimits.MAX_CAPTURE_BYTES);
        assertTrue(
                output.lines()
                        .allMatch(
                                value ->
                                        ScriptOutputLimits.utf8Length(value)
                                                <= ScriptOutputLimits.MAX_LINE_BYTES));
        assertTrue(output.contains("...[truncated]"));
        assertEquals(1, logs.warnings.size());
        assertTrue(
                logs.messages.stream().mapToInt(ScriptOutputLimits::utf8Length).sum()
                        <= ScriptOutputLimits.MAX_LOG_BYTES);
    }

    @Test
    void oversizedVariableShouldFailWithoutPublishingPartialValue() {
        String content = "__VAR__:oversized=" + "值".repeat(10_000) + "\n";
        TaskContext context = context(new RecordingLogService());

        ExecutionException exception =
                assertThrows(ExecutionException.class, () -> collect(content, context));

        assertTrue(exception.getCause() instanceof IllegalArgumentException);
        assertFalse(context.containsVariable("oversized"));
    }

    @Test
    void variableCountShouldBeBounded() {
        StringBuilder content = new StringBuilder();
        for (int index = 0; index <= ScriptOutputLimits.MAX_VARIABLES; index++) {
            content.append("__VAR__:value").append(index).append("=accepted\n");
        }
        TaskContext context = context(new RecordingLogService());

        assertThrows(ExecutionException.class, () -> collect(content.toString(), context));
        assertEquals(ScriptOutputLimits.MAX_VARIABLES, context.getOutputVariables().size());
    }

    private String collect(String content, TaskContext context) throws Exception {
        return new ProcessStreamCollector()
                .collect(
                        context,
                        new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)),
                        "STDOUT",
                        StandardCharsets.UTF_8,
                        executor)
                .get(2, TimeUnit.SECONDS);
    }

    private TaskContext context(InstanceLogService logs) {
        return new TaskContext(1L, new ExecutionHandle(1L), null, logs, new VariableResolver());
    }

    private static final class RecordingLogService extends InstanceLogService {
        private final List<String> messages = new ArrayList<>();
        private final List<String> warnings = new ArrayList<>();

        private RecordingLogService() {
            super(null);
        }

        @Override
        public void info(Long instanceId, String content) {
            messages.add(content);
        }

        @Override
        public void warn(Long instanceId, String content) {
            warnings.add(content);
        }
    }
}
