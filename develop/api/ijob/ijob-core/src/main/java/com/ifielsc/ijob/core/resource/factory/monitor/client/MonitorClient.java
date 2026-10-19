package com.ifielsc.ijob.core.resource.factory.monitor.client;

import com.ifielsc.ijob.contract.monitor.AgentInfo;
import com.ifielsc.ijob.contract.monitor.MonitorReport;

public interface MonitorClient extends AutoCloseable {
    boolean ping();

    AgentInfo getAgentInfo();

    MonitorReport collect(com.ifielsc.ijob.contract.monitor.MonitorCollectRequest request);

    @Override
    default void close() {}
}
