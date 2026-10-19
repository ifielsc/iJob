package com.ifielsc.ijob.core.resource;

import com.ifielsc.ijob.contract.enums.ResourceType;
import com.ifielsc.ijob.core.resource.factory.file.client.FileClient;
import com.ifielsc.ijob.core.resource.factory.monitor.client.MonitorClient;
import com.ifielsc.ijob.core.resource.factory.service.client.ServiceClient;
import com.ifielsc.ijob.core.resource.factory.ssh.client.SshClient;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

import javax.sql.DataSource;

@Component
@RequiredArgsConstructor
public class ResourceResolver {
    private final ResourceRuntimeService runtimeService;

    public ResourceClientLease<com.ifielsc.ijob.core.resource.mq.MessageSourceSession>
            leaseMessageSource(
                    Long id,
                    ResourceType type,
                    com.ifielsc.ijob.core.resource.mq.MessageSourceOptions options) {
        return runtimeService.leaseMessageSource(id, type, options);
    }

    public String endpointIdentity(Long resourceId) {
        return runtimeService.endpointIdentity(resourceId);
    }

    public DataSource getDataSource(Long resourceId) {
        return new LeasedDataSource(
                () ->
                        runtimeService.leaseCachedClient(
                                resourceId, ResourceType.DB, DataSource.class));
    }

    public ResourceClientLease<FileClient> leaseFileClient(Long resourceId) {
        return runtimeService.leaseClient(resourceId, ResourceType.FILE, FileClient.class);
    }

    public ResourceClientLease<ServiceClient> leaseServiceClient(Long resourceId) {
        return runtimeService.leaseCachedClient(
                resourceId, ResourceType.SERVICE, ServiceClient.class);
    }

    public ResourceClientLease<MonitorClient> leaseMonitorClient(Long resourceId) {
        return runtimeService.leaseClient(
                resourceId, ResourceType.MONITOR_SERVER, MonitorClient.class);
    }

    public ResourceClientLease<SshClient> leaseSshClient(Long resourceId) {
        return runtimeService.leaseClient(resourceId, ResourceType.SSH, SshClient.class);
    }
}
