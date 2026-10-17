package com.ifelio.ijob.monitor.agent.collector.application;

import org.springframework.stereotype.Component;

import oshi.software.os.OSProcess;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ProcessSnapshotCache {

    /** key = pid */
    private final Map<Integer, OSProcess> processMap = new ConcurrentHashMap<>();

    public OSProcess get(Integer pid) {
        return processMap.get(pid);
    }

    public void put(OSProcess process) {
        processMap.put(process.getProcessID(), process);
    }

    public void retainOnly(Set<Integer> activePids) {
        processMap.keySet().removeIf(pid -> !activePids.contains(pid));
    }

    public void clear() {
        processMap.clear();
    }

    int size() {
        return processMap.size();
    }
}
