package com.ifelio.ijob.core.resource.factory.monitor.client;

import com.ifelio.ijob.contract.monitor.AgentInfo;
import com.ifelio.ijob.contract.monitor.MonitorReport;

public interface MonitorClient extends AutoCloseable {
    boolean ping();

    AgentInfo getAgentInfo();

    MonitorReport collect(com.ifelio.ijob.contract.monitor.MonitorCollectRequest request);

    @Override
    default void close() {}
}
