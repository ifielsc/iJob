package com.ifielsc.ijob.monitor.agent.execution;

public class MonitorCollectionBusyException extends RuntimeException {

    public MonitorCollectionBusyException() {
        super("monitor collection is busy");
    }
}
