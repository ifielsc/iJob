package com.ifielsc.ijob.plugin.script;

import com.ifielsc.ijob.core.execute.task.TaskContext;

import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PushbackReader;
import java.nio.charset.Charset;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

@Component
public class ProcessStreamCollector {
    private static final int MAX_LINES = 1000;
    private static final String TRUNCATION_SUFFIX = "...[truncated]";

    public Future<String> collect(
            TaskContext context,
            InputStream inputStream,
            String streamName,
            Charset charset,
            ExecutorService pool) {
        return pool.submit(
                () -> {
                    OutputAccumulator output = new OutputAccumulator(context, streamName);
                    try (PushbackReader reader =
                            new PushbackReader(
                                    new BufferedReader(new InputStreamReader(inputStream, charset)),
                                    1)) {
                        BoundedLine line;
                        while ((line = readLine(reader)) != null) {
                            output.accept(line);
                        }
                    }
                    output.validate();
                    return output.content();
                });
    }

    private BoundedLine readLine(PushbackReader reader) throws IOException {
        StringBuilder line = new StringBuilder();
        boolean contentRead = false;
        boolean truncated = false;
        int current;
        while ((current = reader.read()) != -1) {
            contentRead = true;
            if (current == '\n') {
                break;
            }
            if (current == '\r') {
                int next = reader.read();
                if (next != '\n' && next != -1) {
                    reader.unread(next);
                }
                break;
            }
            if (line.length() < ScriptOutputLimits.MAX_LINE_BYTES) {
                line.append((char) current);
            } else {
                truncated = true;
            }
        }
        if (!contentRead && current == -1) {
            return null;
        }

        String value = line.toString();
        if (ScriptOutputLimits.utf8Length(value) > ScriptOutputLimits.MAX_LINE_BYTES) {
            truncated = true;
        }
        if (truncated) {
            int contentBytes =
                    ScriptOutputLimits.MAX_LINE_BYTES
                            - ScriptOutputLimits.utf8Length(TRUNCATION_SUFFIX);
            value = ScriptOutputLimits.truncateUtf8(value, contentBytes) + TRUNCATION_SUFFIX;
        }
        return new BoundedLine(value, truncated);
    }

    private String publishVariable(BoundedLine boundedLine, TaskContext context) {
        String line = boundedLine.value();
        if (!line.startsWith("__VAR__:")) {
            return line;
        }
        if (boundedLine.truncated()) {
            throw new IllegalArgumentException("Script output variable exceeds the line limit");
        }

        String content = line.substring(8);
        int index = content.indexOf('=');
        if (index < 0) {
            return "__VAR__:[ignored]";
        }

        String key = content.substring(0, index);
        String value = content.substring(index + 1);
        if (ScriptOutputLimits.utf8Length(value) > ScriptOutputLimits.MAX_VARIABLE_VALUE_BYTES) {
            throw new IllegalArgumentException("Script output variable exceeds the value limit");
        }
        synchronized (context) {
            var outputs = context.getOutputVariables();
            if (!outputs.containsKey(key) && outputs.size() >= ScriptOutputLimits.MAX_VARIABLES) {
                throw new IllegalArgumentException(
                        "Script output variable count exceeds the limit");
            }
            context.putVariable(key, value);
        }
        return "__VAR__:" + key + "=[redacted]";
    }

    private final class OutputAccumulator {
        private final TaskContext context;
        private final String streamName;
        private final Deque<String> lines = new ArrayDeque<>();
        private int capturedBytes;
        private int loggedBytes;
        private boolean loggingTruncated;
        private IllegalArgumentException variableFailure;

        private OutputAccumulator(TaskContext context, String streamName) {
            this.context = context;
            this.streamName = streamName;
        }

        private void accept(BoundedLine line) {
            String safeLine;
            try {
                safeLine = publishVariable(line, context);
            } catch (IllegalArgumentException exception) {
                if (variableFailure == null) {
                    variableFailure = exception;
                }
                safeLine = "__VAR__:[rejected]";
            }
            log(safeLine);
            capture(safeLine);
        }

        private void log(String line) {
            String message = "[" + streamName + "] " + line;
            int messageBytes = ScriptOutputLimits.utf8Length(message);
            if (loggedBytes + messageBytes <= ScriptOutputLimits.MAX_LOG_BYTES) {
                context.logInfo(message);
                loggedBytes += messageBytes;
            } else if (!loggingTruncated) {
                context.logWarn("[" + streamName + "] output logging truncated");
                loggingTruncated = true;
            }
        }

        private void capture(String line) {
            int lineBytes = ScriptOutputLimits.utf8Length(line);
            if (!lines.isEmpty()) {
                capturedBytes += separatorBytes();
            }
            lines.addLast(line);
            capturedBytes += lineBytes;
            while (lines.size() > MAX_LINES
                    || capturedBytes > ScriptOutputLimits.MAX_CAPTURE_BYTES) {
                capturedBytes -= ScriptOutputLimits.utf8Length(lines.removeFirst());
                if (!lines.isEmpty()) {
                    capturedBytes -= separatorBytes();
                }
            }
        }

        private String content() {
            return String.join(System.lineSeparator(), lines);
        }

        private void validate() {
            if (variableFailure != null) {
                throw variableFailure;
            }
        }

        private int separatorBytes() {
            return ScriptOutputLimits.utf8Length(System.lineSeparator());
        }
    }

    private record BoundedLine(String value, boolean truncated) {}
}
