package com.ifelio.ijob.monitor.agent.execution;

public class MonitorCollectionTimeoutException extends RuntimeException {

    public MonitorCollectionTimeoutException() {
        super("monitor collection timed out");
    }
}
