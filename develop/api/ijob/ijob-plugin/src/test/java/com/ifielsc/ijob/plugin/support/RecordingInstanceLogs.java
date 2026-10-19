package com.ifielsc.ijob.plugin.support;

import com.ifielsc.ijob.core.instance.InstanceLogService;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** An explicit in-memory log dependency for plugin tests without a database. */
public final class RecordingInstanceLogs extends InstanceLogService {
    private final List<String> messages = new CopyOnWriteArrayList<>();

    public RecordingInstanceLogs() {
        super(null);
    }

    @Override
    public void info(Long id, String message) {
        messages.add("INFO " + message);
    }

    @Override
    public void warn(Long id, String message) {
        messages.add("WARN " + message);
    }

    @Override
    public void error(Long id, String message) {
        messages.add("ERROR " + message);
    }

    public List<String> messages() {
        return List.copyOf(messages);
    }
}
