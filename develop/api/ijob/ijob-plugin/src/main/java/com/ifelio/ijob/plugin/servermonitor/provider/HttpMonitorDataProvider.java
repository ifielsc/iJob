package com.ifelio.ijob.plugin.servermonitor.provider;

import com.ifelio.ijob.contract.monitor.MonitorCollectRequest;
import com.ifelio.ijob.contract.monitor.MonitorReport;
import com.ifelio.ijob.core.execute.task.TaskContext;
import com.ifelio.ijob.core.resource.ResourceClientLease;
import com.ifelio.ijob.core.resource.ResourceResolver;
import com.ifelio.ijob.core.resource.factory.monitor.client.MonitorClient;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class HttpMonitorDataProvider implements MonitorDataProvider {
    private final ResourceResolver resourceResolver;

    @Override
    public MonitorReport collect(
            Long resourceId, MonitorCollectRequest request, TaskContext context) {
        checkpoint(context);
        try (ResourceClientLease<MonitorClient> lease =
                resourceResolver.leaseMonitorClient(resourceId)) {
            context.registerResource(lease);
            checkpoint(context);
            return lease.getClient().collect(request);
        }
    }

    private void checkpoint(TaskContext context) {
        context.checkStop();
        context.checkInterrupted();
        context.checkPause();
    }
}
